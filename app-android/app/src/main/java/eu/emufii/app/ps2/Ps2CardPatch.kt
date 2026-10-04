package eu.emufii.app.ps2

import java.nio.ByteBuffer
import java.nio.ByteOrder

object Ps2CardPatch {

    class CardFormatException(message: String) : IllegalArgumentException(message)

    private const val PAGE = 528
    private const val PAGE_DATA = 512

    private const val FAT_TAIL = -1 // 0xFFFFFFFF, last cluster of a chain
    private const val IN_USE = Int.MIN_VALUE // 0x80000000

    private const val MODE_FILE = 0x8497
    private const val MODE_DIR = 0x8427
    private const val MODE_PROTECTED_DIR = 0x842F

    private val HEADER = "# <Sony Computer Entertainment Inc.>\n\n".toByteArray(Charsets.US_ASCII)

    fun inject(
        card: ByteArray,
        consoleId: ByteArray = Ps2NetcnfConfig.ARMSX2_CONSOLE_ID,
        epochSecond: Long = System.currentTimeMillis() / 1000,
        saveTitle: String = "Emufii",
    ): ByteArray {
        val work = if (looksFormatted(card)) card.copyOf() else format(card)
        return PatchedCard(work, epochSecond).apply {
            removeSave("BWNETCNF")
            writeSave("BWNETCNF", Ps2MemoryCard.saveFiles(saveTitle, consoleId), protectedDir = true)
            rewriteFat()
        }.image
    }

    fun addSave(
        card: ByteArray,
        directory: String,
        files: List<Pair<String, ByteArray>>,
        epochSecond: Long = System.currentTimeMillis() / 1000,
    ): ByteArray {
        if (!looksFormatted(card)) throw CardFormatException("card is not formatted")
        return PatchedCard(card, epochSecond).apply {
            removeSave(directory)
            writeSave(directory, files, protectedDir = false)
            rewriteFat()
        }.image
    }

    /** Recovers the i.Link ID BWNETCNF was encrypted for: the 38-byte YNCF header is known plaintext. */
    fun recoverConsoleId(card: ByteArray): ByteArray? {
        if (!looksFormatted(card)) return null
        val save = runCatching { PatchedCard(card, 0).readSaveFiles("BWNETCNF") }.getOrNull()
            ?.filterKeys { it.startsWith("ifc") || it.startsWith("dev") }
            ?.filterValues { it.size > HEADER.size + 10 }
            ?.values?.toList()
            ?: return null
        if (save.isEmpty()) return null

        val shifts = IntArray(24)
        val first = save.maxByOrNull { it.size }!!
        for (k in 0 until 19) {
            val p = le16(HEADER, 2 * k)
            val c = le16(first, 2 * k) xor 0xFFFF
            var found = -1
            for (s in 0 until 16) {
                val rotated = if (s == 0) p else ((p shl s) or (p ushr (16 - s))) and 0xFFFF
                if (rotated == c) {
                    found = s
                    break
                }
            }
            if (found < 0) return null // not this cipher, or not a YNCF file
            shifts[k] = found
        }
        // Words 0-18 give ID bytes 0-5 and the top 3 bits of byte 6; the rest is brute-forced.
        val id = ByteArray(8)
        for (byte in 0 until 6) {
            for (part in 0 until 3) {
                val s = shifts[3 * byte + part]
                if (s !in 1..8) return null // outside the keystream generator's range
            }
            id[byte] = (((shifts[3 * byte] - 1) shl 5) or ((shifts[3 * byte + 1] - 1) shl 2)
                or (shifts[3 * byte + 2] - 1)).toByte()
        }
        if (shifts[18] !in 1..8) return null
        val id6High = (shifts[18] - 1) shl 5

        var best: ByteArray? = null
        for (id6Low in 0 until 32) {
            id[6] = (id6High or id6Low).toByte()
            for (candidate in 0 until 256) {
                id[7] = candidate.toByte()
                var plausible = true
                for (blob in save) {
                    val decoded = Ps2NetcnfConfig.decode(blob, id)
                    for (index in HEADER.size until decoded.size) {
                        val c = decoded[index].toInt() and 0xFF
                        if (c != 0x0A && c != 0x0D && (c < 0x20 || c > 0x7E)) {
                            plausible = false
                            break
                        }
                    }
                    if (!plausible) break
                }
                if (plausible) {
                    if (best != null) return null // two identities read as text: refuse to guess
                    best = id.copyOf()
                }
            }
        }
        return best
    }

    private fun le16(b: ByteArray, at: Int) =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    /** Page 0 carries the PS2 superblock magic. PS1 cards simply lack it. */
    private fun looksFormatted(card: ByteArray): Boolean =
        card.size >= PAGE && card.copyOfRange(0, 28).contentEquals("Sony PS2 Memory Card Format ".toByteArray(Charsets.US_ASCII))

    private class Entry(val name: String, val bytes: ByteArray, val cluster: Int)

    private class PatchedCard(val image: ByteArray, epochSecond: Long) {

        val clusters: Int
        val allocOffset: Int
        val allocEnd: Int
        val fatClusters: List<Int>
        val fat: IntArray
        val rootChain: MutableList<Int>
        val created: ByteArray = Ps2MemoryCard.timestamp(epochSecond)

        init {
            val superblock = page(0)
            if (le16(superblock, 0x28) != PAGE_DATA) {
                throw CardFormatException("only 512-byte pages are supported")
            }
            clusters = u32(superblock, 0x30)
            allocOffset = u32(superblock, 0x34)
            allocEnd = u32(superblock, 0x38)
            val pagesTotal = image.size / PAGE
            if (image.size % PAGE != 0 || pagesTotal < 16 || pagesTotal % 2 != 0 ||
                clusters != pagesTotal / 2 || allocOffset <= 0 || allocEnd > clusters
            ) {
                throw CardFormatException("card geometry does not match its size")
            }
            val indirectFat = cluster(8)
            fatClusters = (0 until 32)
                .map { u32(indirectFat, it * 4) }
                .takeWhile { it in 8 until clusters }
            if (fatClusters.isEmpty()) throw CardFormatException("card has no FAT")
            fat = IntArray(clusters)
            for ((index, fatCluster) in fatClusters.withIndex()) {
                val data = cluster(fatCluster)
                for (e in 0 until 256) {
                    val rel = index * 256 + e
                    if (rel < clusters) fat[rel] = u32(data, e * 4)
                }
            }
            rootChain = chain(0)
            if (rootChain.isEmpty()) throw CardFormatException("card has no root directory")
        }

        fun readSaveFiles(directory: String): Map<String, ByteArray> {
            val entry = rootEntries().firstOrNull { it.name == directory } ?: return emptyMap()
            return readDirFiles(entry.cluster)
        }

        private fun readDirFiles(firstCluster: Int): Map<String, ByteArray> {
            val dir = chainAsBytes(firstCluster)
            val out = mutableMapOf<String, ByteArray>()
            for (slot in 0 until dir.size / PAGE_DATA) {
                val at = slot * PAGE_DATA
                val mode = u32(dir, at)
                if (mode == -1 || mode == 0) break
                val name = name(dir, at) ?: break
                if (name == "." || name == "..") continue
                if (mode and 0x0010 == 0) continue // not a file
                val first = u32(dir, at + 0x10)
                val content = if (first == -1) ByteArray(0) else chainAsBytes(first).copyOf(u32(dir, at + 4))
                out[name] = content
            }
            return out
        }

        fun rootEntries(): List<Entry> {
            val dir = chainAsBytes(0)
            val out = mutableListOf<Entry>()
            for (slot in 0 until dir.size / PAGE_DATA) {
                val at = slot * PAGE_DATA
                val mode = u32(dir, at)
                if (mode == -1 || mode == 0) break
                val name = name(dir, at) ?: break
                if (name == "." || name == "..") continue
                out.add(Entry(name, dir.copyOfRange(at, at + PAGE_DATA), u32(dir, at + 0x10)))
            }
            return out
        }

        fun removeSave(directory: String) {
            val entries = rootEntries()
            val victim = entries.firstOrNull { it.name == directory } ?: return
            // Read before freeing: the chains are walked through the FAT.
            for (fileCluster in readDirClusters(victim.cluster)) freeChain(fileCluster)
            freeChain(victim.cluster)
            rewriteRoot(entries.filter { it.name != directory })
        }

        private fun readDirClusters(firstCluster: Int): List<Int> {
            val dir = chainAsBytes(firstCluster)
            val out = mutableListOf<Int>()
            for (slot in 0 until dir.size / PAGE_DATA) {
                val at = slot * PAGE_DATA
                val mode = u32(dir, at)
                if (mode == -1 || mode == 0) break
                val name = name(dir, at) ?: break
                if (name == "." || name == "..") continue
                out.add(u32(dir, at + 0x10))
            }
            return out.filter { it != -1 }
        }

        /** A moved directory's `.` entry must name its new slot in the parent. */
        fun writeSave(directory: String, files: List<Pair<String, ByteArray>>, protectedDir: Boolean) {
            val saveEntryCount = 2 + files.size
            val saveDir = allocate((saveEntryCount + 2) / 2)
            val dirBytes = ByteBuffer.allocate(1024 * saveDir.size).order(ByteOrder.LITTLE_ENDIAN)
            // The '.' back-reference is filled once the root slot is known.
            dirent(dirBytes, MODE_DIR, 0, 0, ".", created, created, 0)
            dirent(dirBytes, MODE_DIR, 0, 0, "..", created, created, 0)
            for ((fileName, blob) in files) {
                val first = writeFile(blob)
                dirent(dirBytes, MODE_FILE, blob.size, first, fileName, created, created, 0)
            }
            for (i in saveEntryCount * PAGE_DATA until 1024 * saveDir.size) dirBytes.put(i, 0xFF.toByte())
            for ((i, rel) in saveDir.withIndex()) {
                writeCluster(allocOffset + rel, dirBytes.array().copyOfRange(i * 1024, (i + 1) * 1024))
            }

            val entries = rootEntries().filter { it.name != directory }.toMutableList()
            val rootSlot = 2 + entries.size
            val entry = ByteBuffer.allocate(PAGE_DATA).order(ByteOrder.LITTLE_ENDIAN)
            Ps2MemoryCard.dirent(
                entry,
                if (protectedDir) MODE_PROTECTED_DIR else MODE_DIR,
                saveEntryCount, saveDir.first(), directory, created, created, 0,
            )
            entries.add(Entry(directory, entry.array(), saveDir.first()))
            dirBytes.putInt(0x14, rootSlot)
            writeCluster(allocOffset + saveDir.first(), dirBytes.array().copyOfRange(0, 1024))

            rewriteRoot(entries)
        }

        private fun rewriteRoot(entries: List<Entry>) {
            val slotsNeeded = 2 + entries.size // '.', '..', then the saves
            while (rootChain.size * 2 < slotsNeeded + 1) { // plus a terminator
                val extra = allocate(1).first()
                fat[rootChain.last()] = IN_USE or extra
                rootChain.add(extra)
            }
            val rootBytes = ByteBuffer.allocate(1024 * rootChain.size).order(ByteOrder.LITTLE_ENDIAN)
            dirent(rootBytes, MODE_DIR, slotsNeeded, 0, ".", created, created, 0)
            dirent(rootBytes, 0xA426, 0, 0, "..", created, created, 0)
            for ((index, entry) in entries.withIndex()) {
                rootBytes.put(entry.bytes)
                fixBackReference(entry, 2 + index)
            }
            for (i in slotsNeeded * PAGE_DATA until 1024 * rootChain.size) rootBytes.put(i, 0xFF.toByte())
            for ((i, rel) in rootChain.withIndex()) {
                writeCluster(allocOffset + rel, rootBytes.array().copyOfRange(i * 1024, (i + 1) * 1024))
            }
            fat[rootChain.last()] = FAT_TAIL
        }

        private fun fixBackReference(entry: Entry, newSlot: Int) {
            val dir = chainAsBytes(entry.cluster)
            val current = u32(dir, 0x14)
            if (current == newSlot) return
            dir.putIntAt(0x14, newSlot)
            writeCluster(allocOffset + entry.cluster, dir.copyOfRange(0, 1024))
        }

        fun rewriteFat() {
            for ((index, fatCluster) in fatClusters.withIndex()) {
                val data = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
                for (e in 0 until 256) {
                    val rel = index * 256 + e
                    data.putInt(if (rel < clusters) fat[rel] else FAT_TAIL)
                }
                writeCluster(fatCluster, data.array())
            }
        }

        private fun page(p: Int): ByteArray = image.copyOfRange(p * PAGE, p * PAGE + PAGE_DATA)

        private fun cluster(c: Int): ByteArray {
            val out = ByteArray(1024)
            for (half in 0 until 2) {
                System.arraycopy(image, (2 * c + half) * PAGE, out, half * PAGE_DATA, PAGE_DATA)
            }
            return out
        }

        private fun chain(first: Int): MutableList<Int> {
            val out = mutableListOf<Int>()
            var rel = first
            val seen = mutableSetOf<Int>()
            while (rel in 0 until allocEnd) {
                if (!seen.add(rel)) throw CardFormatException("chain loops at cluster $rel")
                out.add(rel)
                val entry = fat[rel]
                if (entry == FAT_TAIL) break
                if (entry and IN_USE == 0) throw CardFormatException("chain leaves allocated space at cluster $rel")
                rel = entry and 0x7FFFFFFF
            }
            return out
        }

        private fun chainAsBytes(first: Int): ByteArray {
            val chain = chain(first)
            val out = ByteArray(chain.size * 1024)
            for ((i, rel) in chain.withIndex()) {
                System.arraycopy(cluster(allocOffset + rel), 0, out, i * 1024, 1024)
            }
            return out
        }

        private fun freeChain(first: Int) {
            var rel = first
            val seen = mutableSetOf<Int>()
            while (rel in 0 until allocEnd && seen.add(rel)) {
                val entry = fat[rel]
                fat[rel] = 0x7FFFFFFF
                if (entry == FAT_TAIL) break
                rel = entry and 0x7FFFFFFF
            }
        }

        private fun allocate(count: Int): List<Int> {
            val taken = mutableListOf<Int>()
            var rel = 0
            while (taken.size < count && rel < allocEnd) {
                if (fat[rel] and IN_USE == 0 && fat[rel] != FAT_TAIL) taken.add(rel)
                rel++
            }
            if (taken.size < count) throw CardFormatException("card is full")
            for (i in 0 until taken.size - 1) fat[taken[i]] = IN_USE or taken[i + 1]
            fat[taken.last()] = FAT_TAIL
            return taken
        }

        private fun writeFile(data: ByteArray): Int {
            val count = maxOf(1, (data.size + 1023) / 1024)
            val clustersTaken = allocate(count)
            for ((i, rel) in clustersTaken.withIndex()) {
                val chunk = ByteArray(1024) { 0xFF.toByte() }
                val from = i * 1024
                val length = minOf(1024, data.size - from).coerceAtLeast(0)
                if (length > 0) System.arraycopy(data, from, chunk, 0, length)
                writeCluster(allocOffset + rel, chunk)
            }
            return clustersTaken.first()
        }

        private fun writeCluster(clusterNumber: Int, data: ByteArray) {
            require(data.size == 1024)
            for (half in 0 until 2) {
                val page = 2 * clusterNumber + half
                val chunk = data.copyOfRange(half * PAGE_DATA, (half + 1) * PAGE_DATA)
                System.arraycopy(chunk, 0, image, page * PAGE, PAGE_DATA)
                System.arraycopy(Ps2MemoryCard.spare(chunk), 0, image, page * PAGE + PAGE_DATA, 16)
            }
        }

        private fun dirent(
            out: ByteBuffer,
            mode: Int,
            length: Int,
            cluster: Int,
            name: String,
            created: ByteArray,
            modified: ByteArray,
            dirEntry: Int,
        ) = Ps2MemoryCard.dirent(out, mode, length, cluster, name, created, modified, dirEntry)

        private fun name(entry: ByteArray, at: Int): String? {
            var end = at + 0x40
            while (end < at + 0x60 && entry[end].toInt() != 0) end++
            if (end == at + 0x60 && entry[at + 0x40].toInt() == 0) return null
            return String(entry.copyOfRange(at + 0x40, end), Charsets.US_ASCII)
        }

        private fun u32(b: ByteArray, at: Int): Int =
            (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or
                ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)

        private fun ByteArray.putIntAt(at: Int, value: Int) {
            this[at] = (value and 0xFF).toByte()
            this[at + 1] = ((value shr 8) and 0xFF).toByte()
            this[at + 2] = ((value shr 16) and 0xFF).toByte()
            this[at + 3] = ((value shr 24) and 0xFF).toByte()
        }
    }

    /** BIOS layout: indirect FAT cluster at 8, then ceil(clusters / 256) FAT clusters, then root. */
    private fun format(card: ByteArray): ByteArray {
        if (card.size % PAGE != 0 || card.size / PAGE < 32 || card.size / PAGE % 2 != 0) {
            throw CardFormatException("not a raw memory card image")
        }
        if (card.size == 0x20000) throw CardFormatException("PS1 memory cards are not supported")
        val clusters = card.size / PAGE / 2
        val fatClusterCount = (clusters + 255) / 256
        val allocOffset = 8 + 1 + fatClusterCount
        val allocEnd = clusters - 0x10 - allocOffset
        val blocks = clusters / 8
        val created = Ps2MemoryCard.timestamp(System.currentTimeMillis() / 1000)

        val image = card.copyOf()
        java.util.Arrays.fill(image, 0xFF.toByte())

        fun writePage(page: Int, data: ByteArray) {
            System.arraycopy(data, 0, image, page * PAGE, PAGE_DATA)
            System.arraycopy(Ps2MemoryCard.spare(data), 0, image, page * PAGE + PAGE_DATA, 16)
        }

        fun writeCluster(cluster: Int, data: ByteArray) {
            writePage(2 * cluster, data.copyOfRange(0, 512))
            writePage(2 * cluster + 1, data.copyOfRange(512, 1024))
        }

        val superblock = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
        superblock.put("Sony PS2 Memory Card Format ".toByteArray(Charsets.US_ASCII))
        superblock.put("1.2.0.0".toByteArray(Charsets.US_ASCII))
        superblock.position(0x28)
        superblock.putShort(512); superblock.putShort(2); superblock.putShort(16)
        superblock.putShort((-0x0100).toShort())
        superblock.putInt(clusters); superblock.putInt(allocOffset); superblock.putInt(allocEnd)
        superblock.putInt(0); superblock.putInt(blocks - 1); superblock.putInt(blocks - 2)
        superblock.position(0x50); superblock.putInt(8)
        superblock.position(0xD0); for (i in 0 until 32) superblock.putInt(-1)
        superblock.put(2); superblock.put(0x2B)
        superblock.position(0x154)
        for (word in intArrayOf(0x400, 0x100, 8, -1, 0, 0, 0, 0x1F41, 0, 0, -1, 0, -1, -1)) superblock.putInt(word)
        for (offset in 0x18C until 0x200 step 4) superblock.putInt(-1)
        writePage(0, superblock.array())
        for (page in 1 until 16) {
            System.arraycopy(Ps2MemoryCard.SPARE_ERASED, 0, image, page * PAGE + PAGE_DATA, 16)
        }

        val indirectFat = ByteBuffer.wrap(ByteArray(1024) { 0xFF.toByte() }).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until fatClusterCount) indirectFat.putInt(i * 4, 9 + i)
        writeCluster(8, indirectFat.array())

        val fat = IntArray(clusters) { if (it < allocEnd) 0x7FFFFFFF else FAT_TAIL }

        // A fresh format's root: two clusters, '.' claiming two entries.
        fat[0] = IN_USE or 1
        fat[1] = FAT_TAIL
        val root = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN)
        Ps2MemoryCard.dirent(root, MODE_DIR, 2, 0, ".", created, created, 0)
        Ps2MemoryCard.dirent(root, 0xA426, 0, 0, "..", created, created, 0)
        for (i in 2 * PAGE_DATA until 2048) root.put(i, 0xFF.toByte())
        writeCluster(allocOffset, root.array().copyOfRange(0, 1024))
        writeCluster(allocOffset + 1, root.array().copyOfRange(1024, 2048))

        for (i in 0 until fatClusterCount) {
            val data = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
            for (e in 0 until 256) {
                val rel = i * 256 + e
                data.putInt(if (rel < allocEnd) fat[rel] else FAT_TAIL)
            }
            writeCluster(9 + i, data.array())
        }
        return image
    }
}

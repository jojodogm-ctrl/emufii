package eu.emufii.app.ps2

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** 8 MB RAW card (16 384 pages of 528 bytes), layout copied byte for byte from a BIOS-formatted card. */
object Ps2MemoryCard {

    private const val PAGE = 528
    private const val PAGE_DATA = 512
    private const val PAGES_TOTAL = 16384
    private const val CLUSTERS_TOTAL = 8192
    private const val ALLOCATION_OFFSET = 41
    private const val ALLOCATION_END = 8135

    private const val FAT_FREE = 0x7FFFFFFF
    private const val FAT_TAIL = -1 // 0xFFFFFFFF
    private const val IN_USE = Int.MIN_VALUE // 0x80000000

    private const val MODE_DOT = 0x8427
    private const val MODE_ROOT_DOTDOT = 0xA426
    private const val MODE_PROTECTED_DIR = 0x842F
    private const val MODE_FILE = 0x8497

    internal val SPARE_ERASED =
        byteArrayOf(0x77, 0x7F, 0x7F, 0x77, 0x7F, 0x7F, 0x77, 0x7F, 0x7F, 0x77, 0x7F, 0x7F, 0, 0, 0, 0)

    internal fun saveFiles(
        saveTitle: String,
        consoleId: ByteArray = Ps2NetcnfConfig.ARMSX2_CONSOLE_ID,
    ): List<Pair<String, ByteArray>> = listOf(
        "BWNETCNF" to Ps2NetcnfConfig.INDEX,
        "icon.sys" to iconSys(sanitise(saveTitle)),
        "EMUFII.ICO" to icon(),
        "net000.cnf" to Ps2NetcnfConfig.NET_CNF,
        "ifc000.dat" to Ps2NetcnfConfig.ifcDat(consoleId),
        "dev000.dat" to Ps2NetcnfConfig.devDat(consoleId),
    )

    internal fun sanitise(saveTitle: String): String =
        saveTitle.filter { it in ' '..'~' }.take(64).ifEmpty { "Emufii" }

    fun generate(
        saveTitle: String,
        consoleId: ByteArray = Ps2NetcnfConfig.ARMSX2_CONSOLE_ID,
        epochSecond: Long = System.currentTimeMillis() / 1000,
    ): ByteArray {
        val title = sanitise(saveTitle)
        val created = timestamp(epochSecond)

        val image = ByteArray(PAGES_TOTAL * PAGE)
        java.util.Arrays.fill(image, 0xFF.toByte())
        val fat = IntArray(CLUSTERS_TOTAL) { if (it < ALLOCATION_END) FAT_FREE else FAT_TAIL }
        var nextFree = 0

        fun writePage(page: Int, data: ByteArray) {
            require(data.size == PAGE_DATA)
            val base = page * PAGE
            System.arraycopy(data, 0, image, base, PAGE_DATA)
            System.arraycopy(spare(data), 0, image, base + PAGE_DATA, 16)
        }

        fun writeCluster(cluster: Int, data: ByteArray) {
            require(data.size == 1024)
            writePage(2 * cluster, data.copyOfRange(0, 512))
            writePage(2 * cluster + 1, data.copyOfRange(512, 1024))
        }

        fun allocate(count: Int): List<Int> {
            val taken = mutableListOf<Int>()
            while (taken.size < count && nextFree < ALLOCATION_END) {
                if (fat[nextFree] == FAT_FREE) {
                    taken.lastOrNull()?.let { previous -> fat[previous] = IN_USE or nextFree }
                    taken.add(nextFree)
                }
                nextFree++
            }
            require(taken.size == count) { "card full" }
            fat[taken.last()] = FAT_TAIL
            return taken
        }

        fun writeFile(data: ByteArray): Pair<Int, Int> {
            val clusters = allocate(maxOf(1, (data.size + 1023) / 1024))
            for ((i, cluster) in clusters.withIndex()) {
                val chunk = ByteArray(1024) { 0xFF.toByte() }
                val from = i * 1024
                val length = minOf(1024, data.size - from).coerceAtLeast(0)
                if (length > 0) System.arraycopy(data, from, chunk, 0, length)
                writeCluster(ALLOCATION_OFFSET + cluster, chunk)
            }
            return clusters.first() to data.size
        }

        writePage(0, superblock())
        for (page in 1 until 16) {
            System.arraycopy(SPARE_ERASED, 0, image, page * PAGE + PAGE_DATA, 16)
        }
        val indirectFat = ByteBuffer.wrap(ByteArray(1024) { 0xFF.toByte() }).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 32) indirectFat.putInt(i * 4, 9 + i)
        writeCluster(8, indirectFat.array())

        val files = saveFiles(title, consoleId)
        val saveEntryCount = 2 + files.size
        val root = allocate(2)
        val saveDir = allocate((saveEntryCount + 2) / 2) // every entry, plus a terminator slot

        val rootBytes = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN) // zero-filled, as entries are
        dirent(rootBytes, MODE_DOT, 3, 0, ".", created, created, 0)
        dirent(rootBytes, MODE_ROOT_DOTDOT, 0, 0, "..", created, created, 0)
        dirent(rootBytes, MODE_PROTECTED_DIR, saveEntryCount, saveDir.first(), "BWNETCNF", created, created, 0)
        for (i in 3 * PAGE_DATA until 2048) rootBytes.put(i, 0xFF.toByte()) // the terminator slot
        for ((i, cluster) in root.withIndex()) {
            writeCluster(ALLOCATION_OFFSET + cluster, rootBytes.array().copyOfRange(i * 1024, (i + 1) * 1024))
        }

        val dirBytes = ByteBuffer.allocate(1024 * saveDir.size).order(ByteOrder.LITTLE_ENDIAN)
        dirent(dirBytes, MODE_DOT, 0, 0, ".", created, created, 2)
        dirent(dirBytes, MODE_DOT, 0, 0, "..", created, created, 0)
        for ((name, blob) in files) {
            val (first, length) = writeFile(blob)
            dirent(dirBytes, MODE_FILE, length, first, name, created, created, 0)
        }
        for (i in saveEntryCount * PAGE_DATA until 1024 * saveDir.size) dirBytes.put(i, 0xFF.toByte())
        for ((i, cluster) in saveDir.withIndex()) {
            writeCluster(ALLOCATION_OFFSET + cluster, dirBytes.array().copyOfRange(i * 1024, (i + 1) * 1024))
        }

        for (i in 0 until 32) {
            val clusterBytes = ByteBuffer.wrap(ByteArray(1024) { 0xFF.toByte() }).order(ByteOrder.LITTLE_ENDIAN)
            for (e in 0 until 256) {
                val index = i * 256 + e
                if (index < ALLOCATION_END) clusterBytes.putInt(e * 4, fat[index])
            }
            writeCluster(9 + i, clusterBytes.array())
        }

        return image
    }
    private fun superblock(): ByteArray {
        val sb = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
        sb.put("Sony PS2 Memory Card Format ".toByteArray(Charsets.US_ASCII))
        sb.put("1.2.0.0".toByteArray(Charsets.US_ASCII))
        sb.position(0x28)
        sb.putShort(512) // page size
        sb.putShort(2) // pages per cluster
        sb.putShort(16) // pages per block
        sb.putShort((-0x0100).toShort()) // unused, as the BIOS leaves it
        sb.putInt(CLUSTERS_TOTAL)
        sb.putInt(ALLOCATION_OFFSET)
        sb.putInt(ALLOCATION_END)
        sb.putInt(0) // root directory cluster, relative, always zero
        sb.putInt(1023) // backup block 1
        sb.putInt(1022) // backup block 2
        sb.position(0x50)
        sb.putInt(8) // the one indirect FAT cluster; the rest stay zero
        sb.position(0xD0)
        for (i in 0 until 32) sb.putInt(-1) // no bad blocks
        sb.put(2) // card type
        sb.put(0x2B) // card flags: ECC, bad-block table, as the BIOS sets them
        // Tail as measured on a BIOS-formatted card.
        sb.position(0x154)
        for (word in intArrayOf(0x400, 0x100, 8, -1, 0, 0, 0, 0x1F41, 0, 0, -1, 0, -1, -1)) {
            sb.putInt(word)
        }
        for (offset in 0x18C until 0x200 step 4) sb.putInt(-1)
        return sb.array()
    }

    /** A directory ends with one all-0xFF entry, not with zero padding. */
    internal fun dirent(
        out: ByteBuffer,
        mode: Int,
        length: Int,
        cluster: Int,
        name: String,
        created: ByteArray,
        modified: ByteArray,
        dirEntry: Int,
    ) {
        val start = out.position()
        out.putInt(mode)
        out.putInt(length)
        out.put(created)
        out.putInt(cluster)
        out.putInt(dirEntry)
        out.put(modified)
        out.putInt(0) // attributes
        out.position(start + 0x40)
        val bytes = name.toByteArray(Charsets.US_ASCII)
        require(bytes.size <= 31) { "name too long: $name" }
        out.put(bytes)
        out.position(start + PAGE_DATA)
    }

    /** Always Japan time, whatever the console setting. */
    internal fun timestamp(epochSecond: Long): ByteArray {
        val t: OffsetDateTime = OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneOffset.ofHours(9))
        return byteArrayOf(
            0,
            t.second.toByte(), t.minute.toByte(), t.hour.toByte(), t.dayOfMonth.toByte(), t.monthValue.toByte(),
            (t.year and 0xFF).toByte(), (t.year shr 8).toByte(),
        )
    }

    /** The emulator ignores these ECC bytes but the emulated console checks them. */
    internal fun spare(page: ByteArray): ByteArray {
        val out = ByteArray(16)
        for (chunk in 0 until 4) {
            var column = 0x77
            var line0 = 0x7F
            var line1 = 0x7F
            for (i in 0 until 128) {
                val b = page[chunk * 128 + i].toInt() and 0xFF
                column = column xor columnParity(b)
                if (Integer.bitCount(b) and 1 == 1) {
                    line0 = line0 xor (i.inv() and 0xFF)
                    line1 = line1 xor i
                }
            }
            out[chunk * 3] = (column and 0x77).toByte()
            out[chunk * 3 + 1] = (line0 and 0x7F).toByte()
            out[chunk * 3 + 2] = (line1 and 0x7F).toByte()
        }
        return out
    }

    private fun columnParity(b: Int): Int {
        var m = 0
        val masks = intArrayOf(0x55, 0x33, 0x0F, 0x00, 0xAA, 0xCC, 0xF0)
        for (bit in masks.indices) {
            if (Integer.bitCount(b and masks[bit]) and 1 == 1) m = m or (1 shl bit)
        }
        return m and 0x77
    }

    private fun iconSys(title: String): ByteArray {
        val e = ByteBuffer.allocate(964).order(ByteOrder.LITTLE_ENDIAN)
        e.put("PS2D".toByteArray(Charsets.US_ASCII))
        e.putShort(0) // reserved
        e.putShort(0) // title newline offset: one line
        e.putInt(0)
        e.putInt(0x50) // background transparency
        repeat(4) { e.putInt(0x14); e.putInt(0x14); e.putInt(0x3C); e.putInt(0) } // corners
        repeat(3) { e.putInt(0); e.putInt(0x3F); e.putInt(0x3F); e.putInt(0x3F) } // light directions
        e.position(128)
        e.putFloat(0.4f); e.putFloat(0.4f); e.putFloat(0.4f); e.putFloat(1.0f)
        e.putFloat(-1.0f); e.putFloat(-1.0f); e.putFloat(-1.0f); e.putFloat(0.0f)
        e.putFloat(0.0f); e.putFloat(0.0f); e.putFloat(0.0f); e.putFloat(0.0f)
        e.putFloat(0.5f); e.putFloat(0.5f); e.putFloat(0.5f); e.putFloat(1.0f)
        e.position(192)
        e.put(title.toByteArray(Charsets.US_ASCII))
        e.position(260)
        repeat(3) {
            e.put("EMUFII.ICO".toByteArray(Charsets.US_ASCII))
            e.position(e.position() + 54)
        }
        return e.array()
    }

    private fun icon(): ByteArray {
        val vertices = ByteBuffer.allocate(6 * 24).order(ByteOrder.LITTLE_ENDIAN)
        val quad = arrayOf(-2048 to -2048, 2048 to -2048, 2048 to 2048, -2048 to 2048)
        for (index in intArrayOf(0, 1, 2, 0, 2, 3)) {
            val (x, y) = quad[index]
            vertices.putShort(x.toShort()); vertices.putShort(y.toShort()); vertices.putShort(0); vertices.putShort(0)
            vertices.putShort(0); vertices.putShort(0); vertices.putShort(4096); vertices.putShort(0)
            vertices.putShort(if (x < 0) 0 else 4096); vertices.putShort(if (y < 0) 0 else 4096)
            vertices.put(0x80.toByte()); vertices.put(0x80.toByte())
            vertices.put(0x80.toByte()); vertices.put(0x80.toByte())
        }
        val animation = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN)
        animation.putInt(1); animation.putInt(1); animation.putFloat(0f); animation.putInt(0); animation.putInt(1)
        animation.putInt(0); animation.putInt(0); animation.putInt(0); animation.putInt(0)

        val texel = 0x7FFF // white
        val texture = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        texture.putInt(8) // bytes of RLE data follow
        texture.putShort(8192.toShort()); texture.putShort(texel.toShort())
        texture.putShort(8192.toShort()); texture.putShort(texel.toShort())

        val out = ByteBuffer.allocate(20 + 6 * 24 + 36 + 12).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x00010000) // magic, version 1.0
        out.putInt(1) // one animation shape
        out.putInt(0x0C) // texture present, RLE-compressed
        out.putInt(0x3F800000) // 1.0f, reserved
        out.putInt(6) // six vertices, two triangles
        out.put(vertices.array()); out.put(animation.array()); out.put(texture.array())
        return out.array()
    }
}

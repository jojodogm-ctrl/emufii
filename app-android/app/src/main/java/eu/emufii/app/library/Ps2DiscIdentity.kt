package eu.emufii.app.library

data class Ps2DiscIdentity(
    val serial: String,
    /** Eight uppercase hexadecimal digits; this is PCSX2's ELF XOR, not CRC32. */
    val elfCrc: String,
) {
    val settingsFilename: String get() = "${serial}_${elfCrc}.ini"
}

object Ps2DiscIdentityReader {

    fun interface Reader {
        fun read(offset: Long, into: ByteArray, count: Int): Int
    }

    internal data class BootFile(val serial: String, val offset: Long, val size: Int)

    fun read(reader: Reader): Ps2DiscIdentity? = runCatching {
        val boot = locateBoot(reader) ?: return null
        Ps2DiscIdentity(boot.serial, elfXor(reader, Record(boot.offset, boot.size, false)))
    }.getOrNull()

    internal fun locateBoot(reader: Reader): BootFile? = runCatching {
        val root = primaryVolumeRoot(reader) ?: return null
        val system = find(reader, root, listOf("SYSTEM.CNF")) ?: return null
        val cnfSize = system.size.coerceAtMost(MAX_CNF_BYTES)
        if (cnfSize <= 0) return null
        val cnfBytes = ByteArray(cnfSize)
        if (!readFully(reader, system.offset, cnfBytes, cnfSize)) return null
        val cnf = String(cnfBytes, Charsets.ISO_8859_1)
        val path = bootPath(cnf) ?: return null
        val serial = DiscImage.bootSerial(cnf) ?: return null
        val executable = find(reader, root, path) ?: return null
        if (executable.directory || executable.size < 4) return null
        BootFile(serial, executable.offset, executable.size)
    }.getOrNull()

    internal fun bootPath(cnf: String): List<String>? {
        val line = cnf.lineSequence().firstOrNull {
            it.trimStart().startsWith("BOOT2", ignoreCase = true)
        } ?: return null
        val raw = line.substringAfter('=', "").trim()
            .substringAfter(':', "")
            .trimStart('\\', '/')
        val parts = raw.split('\\', '/')
            .map { normalName(it) }
            .filter { it.isNotEmpty() }
        return parts.takeIf { it.isNotEmpty() }
    }

    /** PCSX2/ARMSX2's `ElfObject::GetCRC`: XOR complete little-endian words. */
    private fun elfXor(reader: Reader, file: Record): String {
        var crc = 0
        var offset = file.offset
        var wordsLeft = file.size / 4
        val buffer = ByteArray(64 * 1024)
        while (wordsLeft > 0) {
            val count = minOf(buffer.size / 4, wordsLeft) * 4
            if (!readFully(reader, offset, buffer, count)) error("truncated PS2 ELF")
            var at = 0
            while (at < count) {
                crc = crc xor leInt(buffer, at)
                at += 4
            }
            wordsLeft -= count / 4
            offset += count
        }
        return "%08X".format(crc.toLong() and 0xFFFF_FFFFL)
    }

    private data class Record(val offset: Long, val size: Int, val directory: Boolean)

    private fun primaryVolumeRoot(reader: Reader): Record? {
        val pvd = ByteArray(SECTOR_BYTES)
        if (!readFully(reader, PVD_OFFSET, pvd, pvd.size)) return null
        if (pvd[0].toInt() != 1 || String(pvd, 1, 5, Charsets.ISO_8859_1) != "CD001") {
            return null
        }
        return record(pvd, ROOT_RECORD_OFFSET)
    }

    private fun find(reader: Reader, root: Record, path: List<String>): Record? {
        var directory = root
        for ((index, component) in path.withIndex()) {
            if (!directory.directory || directory.size !in 1..MAX_DIRECTORY_BYTES) return null
            val bytes = ByteArray(directory.size)
            if (!readFully(reader, directory.offset, bytes, bytes.size)) return null
            val wanted = normalName(component)
            var found: Record? = null
            var at = 0
            while (at < bytes.size) {
                val length = bytes[at].toInt() and 0xFF
                if (length == 0) {
                    at = (at / SECTOR_BYTES + 1) * SECTOR_BYTES
                    continue
                }
                if (length < MIN_RECORD_BYTES || at + length > bytes.size) break
                val nameLength = bytes[at + 32].toInt() and 0xFF
                if (at + 33 + nameLength > bytes.size) break
                val name = String(bytes, at + 33, nameLength, Charsets.ISO_8859_1)
                if (normalName(name).equals(wanted, ignoreCase = true)) {
                    found = record(bytes, at)
                    break
                }
                at += length
            }
            directory = found ?: return null
            if (index < path.lastIndex && !directory.directory) return null
        }
        return directory
    }

    private fun record(bytes: ByteArray, at: Int): Record? {
        if (at < 0 || at + MIN_RECORD_BYTES > bytes.size) return null
        val length = bytes[at].toInt() and 0xFF
        if (length < MIN_RECORD_BYTES || at + length > bytes.size) return null
        val lba = leInt(bytes, at + 2).toLong() and 0xFFFF_FFFFL
        val size = leInt(bytes, at + 10).toLong() and 0xFFFF_FFFFL
        if (lba == 0L || size <= 0L || size > Int.MAX_VALUE) return null
        return Record(
            offset = lba * SECTOR_BYTES,
            size = size.toInt(),
            directory = bytes[at + 25].toInt() and 0x02 != 0,
        )
    }

    private fun readFully(reader: Reader, offset: Long, into: ByteArray, count: Int): Boolean {
        if (count < 0 || count > into.size) return false
        var done = 0
        while (done < count) {
            // Reader writes at zero, so use a temporary only after a partial read.
            val target = if (done == 0) into else ByteArray(count - done)
            val n = reader.read(offset + done, target, count - done)
            if (n <= 0 || n > count - done) return false
            if (done != 0) target.copyInto(into, done, 0, n)
            done += n
        }
        return true
    }

    private fun normalName(name: String): String = name
        .substringBefore(';')
        .trimEnd('.')
        .trim()

    private fun leInt(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or
            ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or
            ((bytes[at + 3].toInt() and 0xFF) shl 24)

    private const val SECTOR_BYTES = 2048
    private const val PVD_OFFSET = 16L * SECTOR_BYTES
    private const val ROOT_RECORD_OFFSET = 156
    private const val MIN_RECORD_BYTES = 34
    private const val MAX_CNF_BYTES = 4096
    private const val MAX_DIRECTORY_BYTES = 16 * 1024 * 1024
}

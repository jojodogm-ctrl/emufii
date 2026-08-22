package eu.emufii.app.library.psp

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Just enough of a UMD to find two files in it: its icon and its record.
 *
 * A PSP ISO is an ordinary ISO9660. Its table of contents is in the clear, with
 * neither encryption nor compression to get through, unlike the Switch, whose
 * icon required keys, and the two files we care about are always in the same
 * place: `PSP_GAME/ICON0.PNG` for the artwork, `PSP_GAME/PARAM.SFO` for the
 * title and the disc id.
 *
 * Everything here is free of Android, so the parsing can be tested on a byte
 * array. Every entry point returns null rather than throwing: a truncated dump, a
 * file that is not an ISO, a PS2 game filed by mistake in the PSP folder, "we
 * could not read it" is a normal answer, which the caller turns into a tile with
 * initials.
 */
object UmdIso {

    /** An ISO9660 is cut into 2048-byte sectors, with no exception here. */
    const val SECTOR = 2048

    /** The primary volume descriptor starts at the 17th sector. */
    private const val PVD_SECTOR = 16

    /** Where the root record sits inside that descriptor. */
    private const val ROOT_RECORD_AT = 156

    /** The largest file we agree to read; an icon is a few KB. */
    private const val MAX_FILE = 4 * 1024 * 1024

    /** A raw read: [length] bytes at [offset], or null past the end. */
    fun interface Source {
        fun read(offset: Long, length: Int): ByteArray?
    }

    /** A file found in the table of contents: where it starts, and its size. */
    data class Entry(val offset: Long, val size: Int)

    /**
     * The file at the given path, or null when it is not there.
     *
     * Names are compared ignoring case and the `;1` the standard sticks after
     * filenames; those two details are the classic cause of a "missing file" that
     * is in fact present.
     */
    fun find(source: Source, path: List<String>): Entry? {
        if (path.isEmpty()) return null
        val pvd = source.read(PVD_SECTOR.toLong() * SECTOR, SECTOR) ?: return null
        // "CD001" right after the descriptor type: without it this is not an
        // ISO9660, and everything that follows would confidently read noise.
        if (String(pvd, 1, 5, Charsets.US_ASCII) != "CD001") return null

        var dir = record(pvd, ROOT_RECORD_AT) ?: return null
        for ((depth, name) in path.withIndex()) {
            val last = depth == path.lastIndex
            val found = entriesOf(source, dir) { it.name.equals(name, ignoreCase = true) }
                ?: return null
            if (found.isDirectory == last) return null   // un dossier là où on attend un fichier, ou l'inverse
            dir = Entry(found.entry.offset, found.entry.size)
            if (last) return dir.takeIf { it.size in 1..MAX_FILE }
        }
        return null
    }

    private data class Found(val name: String, val entry: Entry, val isDirectory: Boolean)

    /** Walks a directory, sector by sector, until [match] is true. */
    private fun entriesOf(source: Source, dir: Entry, match: (Found) -> Boolean): Found? {
        if (dir.size <= 0 || dir.size > MAX_FILE) return null
        val data = source.read(dir.offset, dir.size) ?: return null
        var i = 0
        while (i < data.size) {
            val len = data[i].toInt() and 0xFF
            // A zero length: the rest of the sector is padding, and the next
            // entry starts at the following sector. That is the standard's rule,
            // and ignoring it misses any directory larger than 2048 bytes.
            if (len == 0) {
                val next = (i / SECTOR + 1) * SECTOR
                if (next <= i || next >= data.size) return null
                i = next
                continue
            }
            if (i + len > data.size) return null
            parse(data, i, len)?.let { if (match(it)) return it }
            i += len
        }
        return null
    }

    private fun parse(data: ByteArray, at: Int, len: Int): Found? {
        if (len < 34) return null
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val lba = buf.getInt(at + 2).toLong() and 0xFFFFFFFFL
        val size = buf.getInt(at + 10)
        val flags = data[at + 25].toInt()
        val nameLen = data[at + 32].toInt() and 0xFF
        if (nameLen <= 0 || at + 33 + nameLen > at + len) return null
        val raw = String(data, at + 33, nameLen, Charsets.US_ASCII)
        // A directory's first two entries are "." and "..", encoded as a single
        // byte 0x00 or 0x01: with no readable name, there is nothing to
        // compare.
        val name = raw.substringBefore(';')
        return Found(name, Entry(lba * SECTOR, size), (flags and 0x02) != 0)
    }

    private fun record(pvd: ByteArray, at: Int): Entry? {
        if (at + 34 > pvd.size) return null
        val buf = ByteBuffer.wrap(pvd).order(ByteOrder.LITTLE_ENDIAN)
        val lba = buf.getInt(at + 2).toLong() and 0xFFFFFFFFL
        val size = buf.getInt(at + 10)
        return if (size in 1..MAX_FILE) Entry(lba * SECTOR, size) else null
    }
}

/**
 * A PSP game's `PARAM.SFO`: a dictionary of keys to strings.
 *
 * Two keys interest us. `TITLE` is the name the console displays, far better
 * than the filename, which often carries the region and the revision in
 * brackets. `DISC_ID` (`ULES01267` and the like) is the disc id, stable from one
 * dump to the next, hence the right cache key for the icon.
 */
object ParamSfo {

    private const val MAGIC = 0x46535000   // "\0PSF" en petit-boutien

    fun read(bytes: ByteArray): Map<String, String> {
        if (bytes.size < 20) return emptyMap()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buf.getInt(0) != MAGIC) return emptyMap()
        val keyTable = buf.getInt(8)
        val dataTable = buf.getInt(12)
        val count = buf.getInt(16)
        if (keyTable !in 0..bytes.size || dataTable !in 0..bytes.size) return emptyMap()
        if (count !in 1..1024) return emptyMap()

        val out = LinkedHashMap<String, String>()
        for (i in 0 until count) {
            val at = 20 + i * 16
            if (at + 16 > bytes.size) break
            val keyAt = keyTable + (buf.getShort(at).toInt() and 0xFFFF)
            val format = buf.getShort(at + 2).toInt() and 0xFFFF
            val len = buf.getInt(at + 4)
            val dataAt = dataTable + buf.getInt(at + 12)
            if (keyAt >= bytes.size || dataAt < 0 || dataAt + len > bytes.size || len <= 0) continue
            val key = cString(bytes, keyAt)
            // 0x0204 = a zero-terminated string. The integers are of no use to
            // us, and reading them as text would give absurd tiles.
            if (format != 0x0204) continue
            out[key] = cString(bytes, dataAt, len)
        }
        return out
    }

    private fun cString(b: ByteArray, at: Int, max: Int = 256): String {
        var end = at
        val limit = minOf(b.size, at + max)
        while (end < limit && b[end].toInt() != 0) end++
        return String(b, at, end - at, Charsets.UTF_8)
    }
}

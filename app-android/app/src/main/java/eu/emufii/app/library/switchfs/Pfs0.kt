package eu.emufii.app.library.switchfs

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The plaintext table of contents at the head of every NSP.
 *
 * Worth saying plainly because it shapes everything else: this part needs no
 * keys. An NSP announces its own contents, names, offsets, sizes, and only
 * what those entries point at is encrypted. That is why a Switch dump can be
 * recognised, and its title id read off a `.tik` filename, on a device that has
 * never seen a console key.
 */
object Pfs0 {

    data class Entry(val name: String, val offset: Long, val size: Long)

    private const val MAGIC = "PFS0"

    /** Sanity ceilings: a real NSP has a handful of entries and short names. */
    private const val MAX_ENTRIES = 512
    private const val MAX_STRING_TABLE = 64 * 1024

    fun entries(source: SwitchControl.RandomAccess): List<Entry>? {
        if (source.size < 0x10) return null
        val head = ByteBuffer.wrap(source.read(0, 0x10)).order(ByteOrder.LITTLE_ENDIAN)
        if (String(head.array(), 0, 4, Charsets.US_ASCII) != MAGIC) return null

        val count = head.getInt(0x04)
        val stringTableSize = head.getInt(0x08)
        if (count !in 1..MAX_ENTRIES) return null
        if (stringTableSize !in 1..MAX_STRING_TABLE) return null

        val tableSize = count * 0x18
        val table = ByteBuffer.wrap(source.read(0x10, tableSize)).order(ByteOrder.LITTLE_ENDIAN)
        val names = source.read((0x10 + tableSize).toLong(), stringTableSize)
        val dataStart = 0x10L + tableSize + stringTableSize

        val out = ArrayList<Entry>(count)
        for (i in 0 until count) {
            val base = i * 0x18
            val offset = table.getLong(base)
            val size = table.getLong(base + 0x08)
            val nameOffset = table.getInt(base + 0x10)
            if (offset < 0 || size < 0 || nameOffset < 0 || nameOffset >= names.size) return null
            if (dataStart + offset + size > source.size) return null
            var end = nameOffset
            while (end < names.size && names[end].toInt() != 0) end++
            out += Entry(
                name = String(names, nameOffset, end - nameOffset, Charsets.UTF_8),
                offset = dataStart + offset,
                size = size
            )
        }
        return out
    }
}

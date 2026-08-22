package eu.emufii.app.library.switchfs

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The other Switch container, the one a cartridge dump uses.
 *
 * An XCI is a card image: a 0x200-byte card header, then a root HFS0 whose
 * entries are partitions, `update`, `logo`, `normal`, `secure`, and the
 * `secure` partition is itself an HFS0 holding the very same NCAs an NSP would
 * carry. So supporting cartridges costs one container reader and nothing else:
 * everything past the NCA is already written.
 *
 * HFS0 is PFS0 with bigger entries (0x40 instead of 0x18, the extra bytes being
 * a hash). Close enough to be confusing, different enough to break a parser that
 * assumes one when it has the other, hence two readers rather than a flag.
 */
object Hfs0 {

    private const val MAGIC = "HFS0"
    private const val ENTRY = 0x40
    private const val MAX_ENTRIES = 512
    private const val MAX_STRING_TABLE = 64 * 1024

    fun entries(source: SwitchControl.RandomAccess, base: Long): List<Pfs0.Entry>? {
        if (base < 0 || base + 0x10 > source.size) return null
        val head = ByteBuffer.wrap(source.read(base, 0x10)).order(ByteOrder.LITTLE_ENDIAN)
        if (String(head.array(), 0, 4, Charsets.US_ASCII) != MAGIC) return null

        val count = head.getInt(0x04)
        val stringTableSize = head.getInt(0x08)
        if (count !in 1..MAX_ENTRIES) return null
        if (stringTableSize !in 1..MAX_STRING_TABLE) return null

        val tableSize = count * ENTRY
        val table = source.read(base + 0x10, tableSize)
        val names = source.read(base + 0x10 + tableSize, stringTableSize)
        val dataStart = base + 0x10 + tableSize + stringTableSize

        val buffer = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN)
        val out = ArrayList<Pfs0.Entry>(count)
        for (i in 0 until count) {
            val at = i * ENTRY
            val offset = buffer.getLong(at)
            val size = buffer.getLong(at + 0x08)
            val nameOffset = buffer.getInt(at + 0x10)
            if (offset < 0 || size < 0 || nameOffset < 0 || nameOffset >= names.size) return null
            if (dataStart + offset + size > source.size) return null
            var end = nameOffset
            while (end < names.size && names[end].toInt() != 0) end++
            out += Pfs0.Entry(
                name = String(names, nameOffset, end - nameOffset, Charsets.UTF_8),
                offset = dataStart + offset,
                size = size
            )
        }
        return out
    }
}

/** A cartridge image: the card header, and the partition that holds the game. */
object Xci {

    /** `HEAD`, at 0x100, the card header's own magic. */
    private const val MAGIC_OFFSET = 0x100
    private const val ROOT_OFFSET = 0x130
    private const val SECURE = "secure"

    fun isXci(source: SwitchControl.RandomAccess): Boolean {
        if (source.size < 0x200) return false
        val head = source.read(0, 0x200)
        return String(head, MAGIC_OFFSET, 4, Charsets.US_ASCII) == "HEAD"
    }

    /**
     * The contents of the `secure` partition, where the NCAs live.
     *
     * The other partitions are of no use here: `update` is a firmware bundle,
     * `logo` a boot animation, `normal` a stub.
     */
    fun secureEntries(source: SwitchControl.RandomAccess): List<Pfs0.Entry>? {
        if (!isXci(source)) return null
        val head = ByteBuffer.wrap(source.read(0, 0x200)).order(ByteOrder.LITTLE_ENDIAN)
        val root = head.getLong(ROOT_OFFSET)
        val partitions = Hfs0.entries(source, root) ?: return null
        val secure = partitions.firstOrNull { it.name.equals(SECURE, ignoreCase = true) }
            ?: return null
        return Hfs0.entries(source, secure.offset)
    }
}

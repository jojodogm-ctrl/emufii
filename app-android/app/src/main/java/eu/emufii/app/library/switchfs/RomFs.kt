package eu.emufii.app.library.switchfs

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The tiny filesystem inside a control NCA. It holds exactly two kinds of thing:
 * `control.nacp`, which carries the title in sixteen languages, and one
 * `icon_<Language>.dat` per language the game was published in, each a plain
 * JPEG despite the extension.
 */
object RomFs {

    /** JPEG's start-of-image marker, used to refuse anything that isn't one. */
    private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

    private const val NACP = "control.nacp"
    private const val ICON_PREFIX = "icon_"

    /** A NACP title entry: 0x200 bytes of name then 0x100 of publisher. */
    private const val NACP_ENTRY = 0x300
    private const val NACP_NAME = 0x200
    private const val NACP_ENTRIES = 16

    fun control(romfs: ByteArray, preferredLanguages: List<String>): SwitchControl.Control? {
        val files = list(romfs) ?: return null

        val icon = pickIcon(files, preferredLanguages)?.let { entry ->
            romfs.copyOfRange(entry.dataOffset, entry.dataOffset + entry.size)
                .takeIf { it.size > JPEG.size && it.startsWith(JPEG) }
        }
        val title = files.firstOrNull { it.name.equals(NACP, ignoreCase = true) }
            ?.let { nacpTitle(romfs, it) }

        return if (icon == null && title == null) null else SwitchControl.Control(title, icon)
    }

    private data class File(val name: String, val dataOffset: Int, val size: Int)

    /**
     * Picks the icon to show: the player's own language first, then American
     * English, which is the one every dump seems to carry, then whatever came
     * first. A game published only in Japanese should still get a tile.
     */
    private fun pickIcon(files: List<File>, preferred: List<String>): File? {
        val icons = files.filter {
            it.name.startsWith(ICON_PREFIX, ignoreCase = true) && it.size > 0
        }
        if (icons.isEmpty()) return null
        for (language in preferred) {
            icons.firstOrNull { it.name.contains(language, ignoreCase = true) }?.let { return it }
        }
        return icons.firstOrNull { it.name.contains("AmericanEnglish", ignoreCase = true) }
            ?: icons.first()
    }

    /** The first non-blank of the sixteen per-language names. */
    private fun nacpTitle(romfs: ByteArray, nacp: File): String? {
        if (nacp.size < NACP_ENTRY) return null
        for (i in 0 until NACP_ENTRIES) {
            val start = nacp.dataOffset + i * NACP_ENTRY
            if (start + NACP_NAME > romfs.size) break
            var end = start
            while (end < start + NACP_NAME && romfs[end].toInt() != 0) end++
            val name = String(romfs, start, end - start, Charsets.UTF_8).trim()
            if (name.isNotBlank()) return name
        }
        return null
    }

    private fun list(romfs: ByteArray): List<File>? {
        if (romfs.size < 0x50) return null
        val h = ByteBuffer.wrap(romfs).order(ByteOrder.LITTLE_ENDIAN)
        // The header states its own size; anything else means we are not
        // looking at a RomFS, most likely a wrong key, which decrypts to
        // plausible-looking noise.
        if (h.getLong(0x00) != 0x50L) return null

        val tableOffset = h.getLong(0x38)
        val tableSize = h.getLong(0x40)
        val dataOffset = h.getLong(0x48)
        if (tableOffset < 0 || tableSize < 0 || tableOffset + tableSize > romfs.size) return null
        if (dataOffset < 0 || dataOffset > romfs.size) return null

        val out = ArrayList<File>()
        var pos = 0L
        while (pos + 0x20 <= tableSize) {
            val base = (tableOffset + pos).toInt()
            val fileDataOffset = h.getLong(base + 0x08)
            val fileSize = h.getLong(base + 0x10)
            val nameLength = h.getInt(base + 0x1C)
            if (nameLength < 0 || base + 0x20 + nameLength > romfs.size) return out
            val name = String(romfs, base + 0x20, nameLength, Charsets.UTF_8)
            val absolute = dataOffset + fileDataOffset
            if (absolute >= 0 && fileSize >= 0 && absolute + fileSize <= romfs.size) {
                out += File(name, absolute.toInt(), fileSize.toInt())
            }
            // Entries are padded to a four-byte boundary.
            pos += 0x20 + ((nameLength + 3) / 4) * 4
        }
        return out
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }
}

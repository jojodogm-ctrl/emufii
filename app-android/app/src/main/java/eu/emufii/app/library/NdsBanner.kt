package eu.emufii.app.library

/**
 * The icon and title a DS cartridge carries in its banner.
 *
 * A `.nds` header points at a banner block (offset `0x068`) holding a 32×32
 * icon, 4 bits per pixel, 8×8 tiles, one 16-colour palette, and the game's
 * title in up to eight languages. That is where "Pokémon Version Blanche 2"
 * lives, as opposed to the `white2.nds` a filename gives us.
 *
 * Decoding is pure and lives here so the tile and palette arithmetic can be
 * tested without a device; [NdsBannerReader] does the I/O and turns pixels into
 * a Bitmap. Same split as the 3DS side, for the same reason.
 */
object NdsBanner {

    const val HEADER_BANNER_OFFSET = 0x068
    const val HEADER_GAME_CODE_OFFSET = 0x00C
    const val HEADER_MAKER_CODE_OFFSET = 0x010
    const val HEADER_INTERNAL_TITLE_OFFSET = 0x000

    const val ICON_DIM = 32

    private const val BITMAP_OFFSET = 0x020
    private const val BITMAP_SIZE = 0x200
    private const val PALETTE_OFFSET = 0x220
    private const val TITLES_OFFSET = 0x240
    private const val TITLE_ENTRY_SIZE = 0x100

    /** Enough to cover the Japanese..Spanish titles every banner version has. */
    const val MIN_BANNER_SIZE = TITLES_OFFSET + 6 * TITLE_ENTRY_SIZE


    /**
     * The 32×32 icon as ARGB_8888 pixels, row-major, or null if [banner] is too
     * short to hold one.
     *
     * Palette index 0 is transparent by definition on this hardware, not merely
     * by convention, treating it as a colour is what turns these icons into
     * squares with a black background.
     */
    fun decodeIcon(banner: ByteArray): IntArray? {
        if (banner.size < PALETTE_OFFSET + 32) return null

        val palette = IntArray(16)
        for (i in 0 until 16) {
            val lo = banner[PALETTE_OFFSET + i * 2].toInt() and 0xFF
            val hi = banner[PALETTE_OFFSET + i * 2 + 1].toInt() and 0xFF
            palette[i] = if (i == 0) 0 else bgr555ToArgb8888((hi shl 8) or lo)
        }

        val pixels = IntArray(ICON_DIM * ICON_DIM)
        val tilesPerRow = ICON_DIM / 8
        for (tileY in 0 until tilesPerRow) {
            for (tileX in 0 until tilesPerRow) {
                val tileBase = BITMAP_OFFSET + (tileY * tilesPerRow + tileX) * 32
                for (row in 0 until 8) {
                    for (pair in 0 until 4) {
                        val byte = banner[tileBase + row * 4 + pair].toInt() and 0xFF
                        // Low nibble is the left pixel of the pair.
                        val left = byte and 0x0F
                        val right = (byte shr 4) and 0x0F
                        val x = tileX * 8 + pair * 2
                        val y = tileY * 8 + row
                        pixels[y * ICON_DIM + x] = palette[left]
                        pixels[y * ICON_DIM + x + 1] = palette[right]
                    }
                }
            }
        }
        return pixels
    }

    /**
     * The best available title, with the publisher line dropped.
     *
     * The last line is the publisher, and the ones before it are the title,
     * that much is convention, and the shape varies by cartridge. Checked
     * against three real dumps:
     *
     * - `Pokémon\nWhite Version 2\nNintendo`      → "Pokémon White Version 2"
     * - `Pokémon SoulSilver\nNintendo`            → "Pokémon SoulSilver"
     * - `Inazuma Eleven 2\nBlizzard\nNintendo`    → "Inazuma Eleven 2 Blizzard"
     *
     * Taking only the first line, which is what this did at first, turns
     * "Pokémon White Version 2" into a bare "Pokémon", and there is no telling
     * two Pokémon games apart in a grid after that.
     */
    fun pickTitle(banner: ByteArray): String? {
        for (language in TitleLanguage.ndsBanner) {
            val base = TITLES_OFFSET + language * TITLE_ENTRY_SIZE
            if (base + TITLE_ENTRY_SIZE > banner.size) continue

            val lines = readUtf16(banner, base, TITLE_ENTRY_SIZE)
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }

            val title = when (lines.size) {
                0 -> null
                1 -> lines[0]
                // Two lines is title then publisher; three is title, subtitle,
                // publisher. Either way the last line is not part of the name.
                else -> lines.dropLast(1).joinToString(" ")
            }
            if (!title.isNullOrBlank()) return title
        }
        return null
    }

    /**
     * A stable key for the icon cache: the 4-letter game code plus the 2-letter
     * maker code, e.g. `NDS-IRBO-01`. Distinct per game *and* region, which is
     * what matters, two regional releases have different icons.
     */
    fun cacheKey(header: ByteArray): String? {
        if (header.size < HEADER_MAKER_CODE_OFFSET + 2) return null
        val gameCode = readAscii(header, HEADER_GAME_CODE_OFFSET, 4)
        val makerCode = readAscii(header, HEADER_MAKER_CODE_OFFSET, 2)
        if (gameCode.isBlank()) return null
        return "NDS-$gameCode-$makerCode".filter { it.isLetterOrDigit() || it == '-' }
    }

    /** Where the banner sits, read from the header. Zero means "no banner". */
    fun bannerOffset(header: ByteArray): Long? {
        if (header.size < HEADER_BANNER_OFFSET + 4) return null
        var value = 0L
        for (i in 3 downTo 0) {
            value = (value shl 8) or (header[HEADER_BANNER_OFFSET + i].toLong() and 0xFF)
        }
        return value.takeIf { it > 0 }
    }

    /** The 12-byte internal title, a usable fallback when the banner is absent. */
    fun internalTitle(header: ByteArray): String? =
        readAscii(header, HEADER_INTERNAL_TITLE_OFFSET, 12).takeIf { it.isNotBlank() }

    private fun readAscii(data: ByteArray, offset: Int, length: Int): String {
        if (offset + length > data.size) return ""
        val sb = StringBuilder()
        for (i in offset until offset + length) {
            val c = data[i].toInt() and 0xFF
            if (c == 0) break
            if (c in 0x20..0x7E) sb.append(c.toChar())
        }
        return sb.toString().trim()
    }

    private fun readUtf16(data: ByteArray, offset: Int, maxBytes: Int): String {
        var end = offset
        val limit = minOf(offset + maxBytes, data.size)
        while (end + 1 < limit) {
            if (data[end] == 0.toByte() && data[end + 1] == 0.toByte()) break
            end += 2
        }
        return String(data, offset, end - offset, Charsets.UTF_16LE)
    }

    /** DS palettes are 15-bit with red in the low bits: `xBBBBBGGGGGRRRRR`. */
    private fun bgr555ToArgb8888(value: Int): Int {
        val r5 = value and 0x1F
        val g5 = (value shr 5) and 0x1F
        val b5 = (value shr 10) and 0x1F
        val r8 = (r5 shl 3) or (r5 shr 2)
        val g8 = (g5 shl 3) or (g5 shr 2)
        val b8 = (b5 shl 3) or (b5 shr 2)
        return (0xFF shl 24) or (r8 shl 16) or (g8 shl 8) or b8
    }
}

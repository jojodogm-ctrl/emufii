package eu.emufii.app.library

/** Banner block at header `0x068`: 32x32 4bpp tiled icon, 16-colour palette, titles in up to eight languages. */
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

    const val MIN_BANNER_SIZE = TITLES_OFFSET + 6 * TITLE_ENTRY_SIZE


    /** Palette index 0 is transparent on this hardware. */
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

    /** Last line is the publisher; earlier lines are the title. */
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
                else -> lines.dropLast(1).joinToString(" ")
            }
            if (!title.isNullOrBlank()) return title
        }
        return null
    }

    /** Game code plus maker code: regions have different icons. */
    fun cacheKey(header: ByteArray): String? {
        if (header.size < HEADER_MAKER_CODE_OFFSET + 2) return null
        val gameCode = readAscii(header, HEADER_GAME_CODE_OFFSET, 4)
        val makerCode = readAscii(header, HEADER_MAKER_CODE_OFFSET, 2)
        if (gameCode.isBlank()) return null
        return "NDS-$gameCode-$makerCode".filter { it.isLetterOrDigit() || it == '-' }
    }

    fun bannerOffset(header: ByteArray): Long? {
        if (header.size < HEADER_BANNER_OFFSET + 4) return null
        var value = 0L
        for (i in 3 downTo 0) {
            value = (value shl 8) or (header[HEADER_BANNER_OFFSET + i].toLong() and 0xFF)
        }
        return value.takeIf { it > 0 }
    }

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

    /** 15-bit, red in the low bits: `xBBBBBGGGGGRRRRR`. */
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

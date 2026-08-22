package eu.emufii.app.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

private const val EXEFS_HEADER_SIZE = 0x200
private const val SMDH_TITLES_OFFSET = 0x08
private const val SMDH_TITLE_ENTRY_SIZE = 0x200
private const val SMDH_SHORT_DESC_SIZE = 0x80
private const val SMDH_LONG_DESC_OFFSET = 0x80
private const val SMDH_LONG_DESC_SIZE = 0x100
private const val SMDH_LARGE_ICON_OFFSET = 0x24C0
private const val SMDH_LARGE_ICON_SIZE = 0x1200
private const val SMDH_TOTAL_SIZE = 0x36C0
private const val ICON_DIM = 48

data class SmdhData(val icon: Bitmap?, val title: String?)

/**
 * What can separate a title from its subtitle in a long description. The em dash
 * is the one used by translated Japanese cover art.
 */
private val SUBTITLE_SEPARATORS = charArrayOf(':', '-', '\u2013', '\u2014', '\n')

/** What gives away a tagline rather than a subtitle. */
private val SENTENCE_ENDINGS = charArrayOf('!', '.', '?')

/**
 * A game's full title, subtitle included.
 *
 * An SMDH entry carries a short description and a long one. The short one
 * truncates: A Link Between Worlds is called "The Legend of Zelda" there, and
 * nothing more, so two Zeldas in the library carried the same name, and an icon
 * search on that name could only bring back whichever Zelda came first.
 *
 * The long one cannot be taken as it stands either: it is sometimes cover-art
 * copy rather than a title. We only keep it when it extends the short one.
 *
 * The separator that matters most is the line break: Nintendo writes the series
 * on the first line and the subtitle on the second. That is the real shape of the
 * Zelda case, verified on the dump, and the first version of this function missed
 * it, because it normalised whitespace before looking for the separator, thereby
 * erasing the very sign it had to read.
 */
internal fun fullTitle(shortDesc: String, longDesc: String): String {
    val short = shortDesc.collapse()
    if (!longDesc.startsWith(shortDesc.trim(), ignoreCase = true)) return short

    val rest = longDesc.substring(shortDesc.trim().length)
    val separator = rest.firstOrNull { !it.isWhitespace() || it == '\n' } ?: return short
    if (separator !in SUBTITLE_SEPARATORS) return short

    val subtitle = rest.dropWhile { it.isWhitespace() || it in SUBTITLE_SEPARATORS }.collapse()
    if (subtitle.isEmpty()) return short

    // A second line is not always a subtitle: it is sometimes a tagline ("Race
    // your friends!"). The closing punctuation gives it away, and that is the
    // only reliable sign short of understanding the language.
    if (subtitle.last() in SENTENCE_ENDINGS) return short

    return "$short: $subtitle"
}

private fun String.collapse(): String =
    replace('\n', ' ').replace(Regex("\\s+"), " ").trim()

class SmdhReader(private val context: Context) {

    fun read(uri: Uri, header: RomHeader): SmdhData {
        if (!header.isDecrypted || header.exefsSize <= 0L) return SmdhData(null, null)

        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                    val exefsHeader = ByteBuffer.allocate(EXEFS_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                    if (readAt(ch, header.exefsOffset, exefsHeader) < EXEFS_HEADER_SIZE) return@use SmdhData(null, null)

                    val iconOffsetInData = findIconEntry(exefsHeader) ?: return@use SmdhData(null, null)
                    val smdhStart = header.exefsOffset + EXEFS_HEADER_SIZE + iconOffsetInData

                    val smdh = ByteBuffer.allocate(SMDH_TOTAL_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                    if (readAt(ch, smdhStart, smdh) < SMDH_TOTAL_SIZE) return@use SmdhData(null, null)
                    if (String(smdh.array(), 0, 4) != "SMDH") return@use SmdhData(null, null)

                    val title = pickTitle(smdh)
                    val bmp = decodeLargeIcon(smdh)
                    SmdhData(bmp, title)
                }
            } ?: SmdhData(null, null)
        }.getOrElse { SmdhData(null, null) }
    }

    private fun findIconEntry(exefsHeader: ByteBuffer): Long? {
        for (i in 0 until 10) {
            val base = i * 16
            val name = String(exefsHeader.array(), base, 8).trimEnd('\u0000', ' ')
            if (name == "icon") {
                return exefsHeader.getInt(base + 8).toLong() and 0xFFFFFFFFL
            }
        }
        return null
    }

    /**
     * The game's title, subtitle included when there is one.
     *
     * An SMDH entry carries three fields: short description, long description,
     * publisher. The short one is what the 3DS menu displays, and it truncates
     * subtitles: A Link Between Worlds is called "The Legend of Zelda" there, and
     * nothing more. Two Zeldas in the library therefore carried the same name,
     * and the icon search could find nothing better than whichever Zelda came
     * first.
     *
     * The long one is not a better source for all that: it is often marketing
     * copy ("Fight for the future!") rather than a title. So we only take it if
     * it extends the short one, same beginning, more of it. That is exactly the
     * "title: subtitle" shape, and nothing else satisfies the test.
     */
    private fun pickTitle(smdh: ByteBuffer): String? {
        val buf = smdh.array()
        for (lang in TitleLanguage.smdh) {
            val base = SMDH_TITLES_OFFSET + lang * SMDH_TITLE_ENTRY_SIZE
            val shortDesc = readUtf16(buf, base, SMDH_SHORT_DESC_SIZE)
            if (shortDesc.isBlank()) continue
            val longDesc = readUtf16(buf, base + SMDH_LONG_DESC_OFFSET, SMDH_LONG_DESC_SIZE)
            return fullTitle(shortDesc, longDesc)
        }
        return null
    }

    private fun readUtf16(buf: ByteArray, offset: Int, maxBytes: Int): String {
        var end = offset
        val limit = offset + maxBytes
        while (end + 1 < limit) {
            if (buf[end] == 0.toByte() && buf[end + 1] == 0.toByte()) break
            end += 2
        }
        return String(buf, offset, end - offset, Charsets.UTF_16LE)
    }

    private fun decodeLargeIcon(smdh: ByteBuffer): Bitmap {
        val pixels = IntArray(ICON_DIM * ICON_DIM)
        val tilesPerRow = ICON_DIM / 8
        val base = SMDH_LARGE_ICON_OFFSET
        for (tileY in 0 until tilesPerRow) {
            for (tileX in 0 until tilesPerRow) {
                for (py in 0 until 8) {
                    for (px in 0 until 8) {
                        val morton = mortonIndex(px, py)
                        val tileIdx = tileY * tilesPerRow + tileX
                        val byteOffset = base + (tileIdx * 64 + morton) * 2
                        val lo = smdh.get(byteOffset).toInt() and 0xFF
                        val hi = smdh.get(byteOffset + 1).toInt() and 0xFF
                        val rgb565 = (hi shl 8) or lo
                        val x = tileX * 8 + px
                        val y = tileY * 8 + py
                        pixels[y * ICON_DIM + x] = rgb565ToArgb8888(rgb565)
                    }
                }
            }
        }
        return Bitmap.createBitmap(pixels, ICON_DIM, ICON_DIM, Bitmap.Config.ARGB_8888)
    }

    private fun mortonIndex(x: Int, y: Int): Int =
        (x and 1) or
            ((y and 1) shl 1) or
            ((x and 2) shl 1) or
            ((y and 2) shl 2) or
            ((x and 4) shl 2) or
            ((y and 4) shl 3)

    private fun rgb565ToArgb8888(rgb565: Int): Int {
        val r5 = (rgb565 shr 11) and 0x1F
        val g6 = (rgb565 shr 5) and 0x3F
        val b5 = rgb565 and 0x1F
        val r8 = (r5 shl 3) or (r5 shr 2)
        val g8 = (g6 shl 2) or (g6 shr 4)
        val b8 = (b5 shl 3) or (b5 shr 2)
        return (0xFF shl 24) or (r8 shl 16) or (g8 shl 8) or b8
    }

    private fun readAt(ch: FileChannel, pos: Long, buf: ByteBuffer): Int {
        ch.position(pos)
        var total = 0
        while (buf.hasRemaining()) {
            val n = ch.read(buf)
            if (n < 0) break
            total += n
        }
        return total
    }
}

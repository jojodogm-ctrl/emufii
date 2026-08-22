package eu.emufii.app.library

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

/**
 * Version of the cached title *format*. See `titleFileFor`.
 * v3: the SMDH's line break separates a title from its subtitle.
 */
private const val TITLE_FORMAT = "v3"

class IconCache(context: Context) {
    private val dir: File = File(context.filesDir, "icons").apply { mkdirs() }

    fun fileFor(titleIdHex: String): File = File(dir, "$titleIdHex.png")
    /**
     * Titles are cached per language: the same cartridge has a different name in
     * each, so one file per game would have kept showing whichever language
     * happened to be current at the last scan.
     *
     * [TITLE_FORMAT] has to go up at every change in the way a title is read,
     * failing which libraries already scanned go on serving the old one and the
     * fix is only visible to a newcomer.
     *
     * v2 had already shipped with an incomplete rule: it did not recognise the
     * SMDH's line break as a subtitle separator. So it *wrote into the cache* the
     * truncated title "The Legend of Zelda", and the corrected version that
     * followed read it back without ever reopening the ROM. Hence v3, and the
     * lesson: raise the version at the same time as the rule, not before.
     */
    private fun titleFileFor(titleIdHex: String, lang: String): File =
        File(dir, "$titleIdHex.title.$TITLE_FORMAT.$lang")
    private fun accentFileFor(titleIdHex: String): File = File(dir, "$titleIdHex.accent")

    fun writeIcon(titleIdHex: String, bitmap: Bitmap) {
        FileOutputStream(fileFor(titleIdHex)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    fun writeTitle(titleIdHex: String, title: String, lang: String = TitleLanguage.tag) {
        titleFileFor(titleIdHex, lang).writeText(title, Charsets.UTF_8)
    }

    /** The tile glow, kept next to the icon so a rescan doesn't recompute it. */
    fun writeAccent(titleIdHex: String, argb: Int) {
        accentFileFor(titleIdHex).writeText(argb.toString(), Charsets.UTF_8)
    }

    fun readAccent(titleIdHex: String): Int? =
        accentFileFor(titleIdHex).takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()

    fun readTitle(titleIdHex: String, lang: String = TitleLanguage.tag): String? {
        val f = titleFileFor(titleIdHex, lang)
        return if (f.exists()) f.readText(Charsets.UTF_8).ifBlank { null } else null
    }
}

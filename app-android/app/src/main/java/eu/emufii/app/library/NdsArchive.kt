package eu.emufii.app.library

import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * A zipped DS cartridge, which melonDS extracts itself.
 * pourquoi : docs/decisions/scan-bibliotheque.md § A zipped cartridge stays zipped
 */
object NdsArchive {

    const val EXTENSION = "zip"

    private val ROM_EXTENSIONS = setOf("nds", "dsi", "ids")

    private const val MAX_ENTRIES = 64

    fun isArchive(filename: String): Boolean =
        filename.substringAfterLast('.', "").equals(EXTENSION, ignoreCase = true)

    /** The first cartridge in [file], or null if there is none. Closing [file] closes it. */
    fun openRom(file: InputStream): InputStream? {
        val zip = ZipInputStream(file)
        repeat(MAX_ENTRIES) {
            val entry = zip.nextEntry ?: return null
            val ext = entry.name.substringAfterLast('.', "").lowercase()
            if (!entry.isDirectory && ext in ROM_EXTENSIONS) return zip.buffered()
        }
        return null
    }
}

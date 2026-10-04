package eu.emufii.app.library

import java.io.InputStream
import java.util.zip.ZipInputStream

object NdsArchive {

    const val EXTENSION = "zip"

    private val ROM_EXTENSIONS = setOf("nds", "dsi", "ids")

    private const val MAX_ENTRIES = 64

    fun isArchive(filename: String): Boolean =
        filename.substringAfterLast('.', "").equals(EXTENSION, ignoreCase = true)

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

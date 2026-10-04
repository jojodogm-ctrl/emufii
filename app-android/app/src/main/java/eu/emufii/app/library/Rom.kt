package eu.emufii.app.library

import android.net.Uri
import java.io.File

data class Rom(
    val uri: Uri,
    val filename: String,
    val displayName: String,
    val console: Console,
    val titleIdHex: String? = null,
    val productCode: String? = null,
    val iconFile: File? = null,
    val accentArgb: Int? = null,
    val ps2ElfCrc: String? = null,
    val addedAt: Long = 0L
) {
    val sessionId: String? get() = titleIdHex ?: productCode
}

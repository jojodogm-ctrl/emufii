package eu.emufii.app.nemul

import java.util.Locale

/** N Emul's native host generates four-character rooms, separate from Emufii session codes. */
object NemulRoomCode {
    fun normalize(value: String): String = value.trim().uppercase(Locale.ROOT)

    fun isValid(value: String): Boolean = normalize(value).matches(Regex("[A-Z0-9]{4}"))

    fun joinUri(value: String, waitForHostPreparation: Boolean = false): String {
        require(isValid(value))
        val uri = "nemul://join/${normalize(value)}"
        return if (waitForHostPreparation) "$uri?liveboot=1" else uri
    }
}

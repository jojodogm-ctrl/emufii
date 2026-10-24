package eu.emufii.app.onetap

import java.net.URI
import java.util.Locale

/** Public One Tap room codes are distinct from Emufii session codes and capabilities. */
object OneTapRoomCode {
    private val code = Regex("[A-Z0-9]{4}")

    fun parse(value: String): String? {
        val raw = value.trim()
        if (raw.any { it.code > 127 }) return null
        val direct = raw.uppercase(Locale.ROOT)
        if (code.matches(direct)) return direct
        if (raw.length > 256) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (uri.rawUserInfo != null || uri.port != -1 || uri.rawQuery != null) return null
        val path = uri.rawPath.orEmpty()
        val candidate = when {
            uri.scheme == "https" && uri.host == OneTapEntry.HOST && uri.rawFragment == null &&
                path.startsWith("/join/") -> path.removePrefix("/join/")
            uri.scheme == "https" && uri.host == OneTapEntry.HOST && path == "/play/" &&
                uri.rawFragment?.startsWith("room/") == true -> uri.rawFragment.removePrefix("room/")
            uri.scheme in listOf("wantapparty", "wantap-nifi2") && uri.host == "join" &&
                uri.rawFragment == null && path.startsWith("/") -> path.removePrefix("/")
            uri.scheme == "wantapparty" && uri.rawFragment == null && path.isEmpty() -> uri.host
            else -> null
        }?.uppercase(Locale.ROOT) ?: return null
        return candidate.takeIf(code::matches)
    }

    fun require(value: String): String = requireNotNull(parse(value)) { "onetap-room-code" }
}

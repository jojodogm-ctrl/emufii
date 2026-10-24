package com.mpds.online

import java.net.URI
import java.util.Locale

/** A room code is not a credential. Only our exact public invitation origins
 * are accepted; never salvage a substring from a malformed or foreign URL. */
internal object RoomCode {
    private val code = Regex("[A-Z0-9]{4}")
    fun parse(text: String): String? {
        val raw = text.trim()
        val direct = raw.uppercase(Locale.ROOT)
        if (code.matches(direct)) return direct
        if (raw.length > 256) return null
        val uri = try { URI(raw) } catch (_: Exception) { return null }
        if (uri.rawUserInfo != null || uri.port != -1 || uri.rawQuery != null || uri.rawFragment != null) return null
        val path = uri.rawPath ?: ""
        val candidate = when {
            uri.scheme == "https" && uri.host == "party.lepast.fr" && path.startsWith("/join/") ->
                path.removePrefix("/join/")
            uri.scheme in listOf("wantapparty", "wantap-nifi2") && uri.host == "join" && path.startsWith("/") ->
                path.removePrefix("/")
            uri.scheme == "wantapparty" && path.isEmpty() -> uri.host
            else -> null
        }?.uppercase(Locale.ROOT) ?: return null
        return candidate.takeIf { code.matches(it) }
    }
}

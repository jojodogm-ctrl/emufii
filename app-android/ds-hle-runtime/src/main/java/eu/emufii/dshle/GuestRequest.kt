package eu.emufii.dshle

import java.util.Locale

/** Only public selection enters the runtime; routes and credentials never do. */
internal data class GuestRequest(val roomCode: String, val playerName: String) {
    companion object {
        private const val TRIM_WHITESPACE="\u0009\u000a\u000b\u000c\u000d\u0020\u00a0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200a\u2028\u2029\u202f\u205f\u3000\ufeff"
        fun parse(roomCode: String?, playerName: String?): GuestRequest? {
            if (roomCode == null || playerName == null || roomCode.any { it.code > 127 }) return null
            val code = roomCode.trim().uppercase(Locale.ROOT)
            if (!Regex("[A-Z0-9]{4}").matches(code)) return null
            val name = playerName.trim {it in TRIM_WHITESPACE}
            if (name.isEmpty() || name.length > 10 || name.any {
                it <= '\u001f' || it in '\u007f'..'\u009f' || it in '\ud800'..'\udfff' ||
                    it == '\u061c' || it == '\u200e' || it == '\u200f' ||
                    it in '\u2028'..'\u202e' || it in '\u2066'..'\u2069'
            }) return null
            return GuestRequest(code, name)
        }

        fun supports(abis: List<String>): Boolean = "arm64-v8a" in abis
    }
}

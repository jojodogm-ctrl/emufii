package eu.emufii.app.onetap

import eu.emufii.app.BuildConfig

/** Application-owned URLs. A room invite never selects executable code or a relay. */
object OneTapEntry {
    const val HOST = "party.lepast.fr"
    const val ORIGIN = "https://$HOST"
    const val BROWSER_HOME = "$ORIGIN/play/"
    // Public Android20260929-notices is the default; a private preview can select its diagnostic wrapper.
    val NATIVE_PACKAGE = BuildConfig.ONE_TAP_NATIVE_PACKAGE
    const val NATIVE_ACTIVITY = "com.mpds.online.MainActivity"

    fun browserRoom(code: String): String = "$BROWSER_HOME#room/${OneTapRoomCode.require(code)}"
    fun nativeRoom(code: String): String = "wantapparty://join/${OneTapRoomCode.require(code)}"
    fun publicInvite(code: String): String = "$ORIGIN/join/${OneTapRoomCode.require(code)}"
}

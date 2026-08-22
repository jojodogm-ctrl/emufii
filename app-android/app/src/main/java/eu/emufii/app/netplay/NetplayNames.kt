package eu.emufii.app.netplay

import eu.emufii.app.library.Backend

/**
 * Names the emulator's netplay form will actually accept.
 *
 * The room name, and on Eden only, the pseudo. Emufii writes nothing into
 * Azahar's: padding the profile name to Azahar's four-character minimum once
 * overwrote a valid pseudo the player had set in Azahar itself, which the form
 * then rejected with a message accusing the address.
 *
 * Eden is the opposite case, and the reason [username] exists: two players
 * carrying the same pseudo cannot share a room, and Eden ships everyone the same
 * default.
 *
 * The 3..20 bound is Azahar's own, verbatim from its resources (2126.0-rc5).
 */
object NetplayNames {

    const val MIN_ROOM_NAME = 3
    const val MAX_ROOM_NAME = 20

    /**
     * Minimum pseudo length, measured on Azahar (the validator lives in the DEX,
     * not in the resources) and reused for Eden, which descends from the same
     * code. Padded rather than refused: a profile named "Jo" is legitimate here.
     */
    const val MIN_USERNAME = 5

    /** Eden's own limit, the same as the room name's. */
    const val MAX_USERNAME = 20

    /**
     * The pseudo to write into Eden, taken from the Emufii profile. Null for a
     * nameless profile: an invented name would be worse than the emulator's,
     * which at least someone chose.
     */
    fun usernameFor(backend: Backend, profileName: String?): String? =
        // Eden and Dolphin both ship everyone the same default pseudo, so a room
        // where nobody can be told apart. Azahar keeps its own.
        if (backend == Backend.EDEN || backend == Backend.DOLPHIN) username(profileName)
        else null

    /**
     * The pseudo itself, without the emulator question. Public so it can be
     * tested on its own; callers go through [usernameFor].
     */
    fun username(profileName: String?): String? {
        val name = profileName?.trim().orEmpty()
        if (name.isEmpty()) return null
        // Padded with dots rather than letters: "Jo..." reads as a shortened
        // name, "Joxxx" reads as a different one.
        return name.take(MAX_USERNAME).padEnd(MIN_USERNAME, '.')
    }

    /**
     * A room name within Azahar's 3..20. The session code alone would do, but
     * prefixing it tells a player browsing a lobby list where the room came from.
     */
    fun roomName(sessionCode: String): String {
        val code = sessionCode.trim()
        val full = if (code.isEmpty()) "Emufii" else "Emufii $code"
        return full.take(MAX_ROOM_NAME).padEnd(MIN_ROOM_NAME, 'x')
    }
}

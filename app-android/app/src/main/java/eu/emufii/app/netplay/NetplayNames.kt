package eu.emufii.app.netplay

import eu.emufii.app.library.Backend

object NetplayNames {

    const val MIN_ROOM_NAME = 3
    const val MAX_ROOM_NAME = 20

    /** From Azahar's validator (in the dex, not resources); Eden shares the code. */
    const val MIN_USERNAME = 5

    const val MAX_USERNAME = 20

    fun usernameFor(backend: Backend, profileName: String?): String? =
        // Two players with the same default nickname can't share a room.
        if (backend == Backend.EDEN || backend == Backend.DOLPHIN || backend == Backend.AZAHAR) {
            username(profileName)
        } else null

    fun isDefaultUsername(current: String?): Boolean {
        val name = current?.trim().orEmpty()
        return name.length < MIN_USERNAME || name.lowercase() in DEFAULT_USERNAMES
    }

    private val DEFAULT_USERNAMES = setOf("azahar", "citra", "lime3ds")

    fun username(profileName: String?): String? {
        val name = profileName?.trim().orEmpty()
        if (name.isEmpty()) return null
        return name.take(MAX_USERNAME).padEnd(MIN_USERNAME, '.')
    }

    fun roomName(sessionCode: String): String {
        val code = sessionCode.trim()
        val full = if (code.isEmpty()) "Emufii" else "Emufii $code"
        return full.take(MAX_ROOM_NAME).padEnd(MIN_ROOM_NAME, 'x')
    }
}

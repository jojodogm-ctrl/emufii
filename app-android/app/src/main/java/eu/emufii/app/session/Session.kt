package eu.emufii.app.session

import android.net.Uri
import eu.emufii.app.library.Backend
import eu.emufii.app.library.Console
import kotlin.random.Random

data class RomRef(
    val uri: Uri,
    val displayName: String,
    val console: Console,
    /**
     * What the session is compared against when joining by a typed code. Null
     * for a dump the library could not identify, and then no comparison is
     * possible, see the join flow, which lets those through.
     */
    val titleIdHex: String? = null
)

data class Session(
    val code: String,
    val hostIp: String,
    val port: String,
    val role: Role,
    val rom: RomRef? = null,
    /**
     * The secret that proves the right to modify this session.
     *
     * The host's is returned at creation; a guest's is returned to them on
     * joining, and only authorises them to withdraw themselves. The code is
     * public: the finder publishes it, so it proves nothing.
     *
     * Null while the tunnel is not up, or against an older coordinator, in which
     * case the calls go out without the header, as before.
     */
    val token: String? = null,
    /**
     * The Eden room the VPS holds for this session, when it has one.
     *
     * Its presence changes who hosts: with a room, nobody hosts on their phone,
     * both players join it. Without one, the host carries the room as before. The
     * field is therefore not decorative, it decides the role Emufii plays in
     * Eden's form.
     */
    val room: eu.emufii.app.network.RoomRef? = null
) {
    enum class Role { HOST, GUEST }

    /** Null when joining a session for a game we don't own locally. */
    val console: Console? get() = rom?.console

    val backend: Backend get() = console?.backend ?: Backend.NONE

}

object SessionCodes {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"

    fun generate(): String {
        val letters = (1..3).map { ALPHABET.random(Random.Default) }.joinToString("")
        val digits = (1..3).map { DIGITS.random(Random.Default) }.joinToString("")
        return "$letters-$digits"
    }

    /**
     * What the player typed, turned into the code the coordinator stores.
     *
     * The hyphen is a reading aid, nobody says it out loud, and someone who
     * types "HMM295" means the session called "HMM-295". Without this, they got
     * "session introuvable" and went looking for a typo that wasn't there.
     * Spaces, lowercase and a hyphen in the wrong place are all forgiven the
     * same way; anything else is left alone, so a genuinely wrong code still
     * fails as a wrong code.
     */
    fun normalize(typed: String): String {
        val body = typed.uppercase().filter { it.isLetterOrDigit() }
        if (body.length != 6) return typed.uppercase().trim()
        return "${body.take(3)}-${body.drop(3)}"
    }
}

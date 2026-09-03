package eu.emufii.app.session

import android.net.Uri
import eu.emufii.app.library.Backend
import eu.emufii.app.library.Console
import java.security.SecureRandom

data class RomRef(
    val uri: Uri,
    val displayName: String,
    val console: Console,
    /**
     * What the session is compared against when joining by a typed code. Null
     * for a dump the library could not identify, and then no comparison is
     * possible, see the join flow, which lets those through.
     */
    val titleIdHex: String? = null,
    /** Compressed PSP dumps often carry DISC_ID only here. */
    val filename: String? = null,
    /** PSP DISC_ID is stored as `PSP-ULUS10277`; other consoles keep their own product code. */
    val productCode: String? = null,
    /** ARMSX2's per-game settings suffix, so launch performs no disc scan. */
    val ps2ElfCrc: String? = null,
)

data class Session(
    val code: String,
    val hostIp: String,
    val port: String,
    val role: Role,
    val rom: RomRef? = null,
    /**
     * The secret that proves the right to modify this session. The host's comes
     * back at creation, a guest's on joining, and a guest's only authorises
     * withdrawing themselves. The code is public and proves nothing. Null while
     * the tunnel is down or against an older coordinator, and the calls then go
     * out without the header.
     */
    val token: String? = null,
    /**
     * The Eden room the VPS holds, when it has one. Its presence decides who
     * hosts: with a room both players join it, without one the host carries it.
     */
    val room: eu.emufii.app.network.RoomRef? = null
) {
    enum class Role { HOST, GUEST }

    val console: Console? get() = rom?.console

    val backend: Backend get() = console?.backend ?: Backend.NONE

    /**
     * The one address the player sees. A single definition because there were
     * two: the session screen computed `room?.host ?: hostIp` while the panel
     * got raw `hostIp`, so an Eden session with a room showed an address the
     * emulator does not expect once the front screen stopped repeating it.
     * pourquoi : docs/decisions/session.md § What the rear panel carries, the front screen does not repeat
     */
    val shownAddress: String get() = when {
        room != null -> room.host
        // The PSP is the only case that is not an IP: its ad hoc server has a
        // fixed name, which PPSSPP resolves itself.
        backend == Backend.PPSSPP -> eu.emufii.app.psp.HOST_SENTINEL
        else -> hostIp
    }

    /** Null when the console does not ask: the PSP's ad hoc port is fixed. */
    val shownPort: String? get() = when {
        room != null -> room.port.toString()
        backend == Backend.PPSSPP -> null
        else -> port
    }
}

object SessionCodes {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"

    /**
     * Three and three, and the join screen is why: its keypad draws that many boxes and
     * stops taking keys at the sixth, so a longer code cannot be typed at all. Widening
     * the code means widening that screen first.
     * pourquoi : docs/decisions/coquille-ecrans.md § Six slots rather than a field
     */
    private const val LETTERS = 3
    private const val NUMBERS = 3

    /**
     * `SecureRandom`, like the friend code beside it: `Random.Default` is a XorWow seeded
     * per process, and its next draw is not built to be hard to derive from its last.
     */
    private val secureRandom by lazy { SecureRandom() }

    private fun pick(from: String, n: Int) =
        buildString { repeat(n) { append(from[secureRandom.nextInt(from.length)]) } }

    fun generate(): String = "${pick(ALPHABET, LETTERS)}-${pick(DIGITS, NUMBERS)}"

    /**
     * What the player typed, turned into the code the coordinator stores. The
     * hyphen is a reading aid nobody says out loud, so "HMM295" means "HMM-295".
     * Spaces, lowercase and a misplaced hyphen are forgiven the same way;
     * anything else is left alone, so a wrong code still fails as one.
     */
    fun normalize(typed: String): String {
        val body = typed.uppercase().filter { it.isLetterOrDigit() }
        // Four and four is recognised too: one build generated it, and such a session
        // stays joinable from the finder, where nothing has to be typed.
        val letters = body.takeWhile { it.isLetter() }.length
        val digits = body.length - letters
        val known = (letters == LETTERS && digits == NUMBERS) || (letters == 4 && digits == 4)
        if (!known) return typed.uppercase().trim()
        return "${body.take(letters)}-${body.drop(letters)}"
    }
}

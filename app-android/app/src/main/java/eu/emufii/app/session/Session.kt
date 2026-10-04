package eu.emufii.app.session

import android.net.Uri
import eu.emufii.app.library.Backend
import eu.emufii.app.library.Console
import java.security.SecureRandom

data class RomRef(
    val uri: Uri,
    val displayName: String,
    val console: Console,
    val titleIdHex: String? = null,
    /** Compressed PSP dumps often carry DISC_ID only here. */
    val filename: String? = null,
    val productCode: String? = null,
    val ps2ElfCrc: String? = null,
)

data class Session(
    val code: String,
    val hostIp: String,
    val port: String,
    val role: Role,
    val rom: RomRef? = null,
    /** Proves the right to modify the session; a guest's only allows leaving. The code is public. */
    val token: String? = null,
    val room: eu.emufii.app.network.RoomRef? = null
) {
    enum class Role { HOST, GUEST }

    val console: Console? get() = rom?.console

    val backend: Backend get() = console?.backend ?: Backend.NONE

    val shownAddress: String get() = when {
        room != null -> room.host
        // PPSSPP's ad hoc server is a fixed sentinel address.
        backend == Backend.PPSSPP -> eu.emufii.app.psp.HOST_SENTINEL
        else -> hostIp
    }

    val shownPort: String? get() = when {
        room != null -> room.port.toString()
        backend == Backend.PPSSPP -> null
        else -> port
    }
}

object SessionCodes {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"

    // The join keypad has six boxes; widen it before widening the code.
    private const val LETTERS = 3
    private const val NUMBERS = 3

    // `Random.Default` is predictable from previous draws.
    private val secureRandom by lazy { SecureRandom() }

    private fun pick(from: String, n: Int) =
        buildString { repeat(n) { append(from[secureRandom.nextInt(from.length)]) } }

    fun generate(): String = "${pick(ALPHABET, LETTERS)}-${pick(DIGITS, NUMBERS)}"

    fun normalize(typed: String): String {
        val body = typed.uppercase().filter { it.isLetterOrDigit() }
        // 4+4 codes from an old build are still accepted.
        val letters = body.takeWhile { it.isLetter() }.length
        val digits = body.length - letters
        val known = (letters == LETTERS && digits == NUMBERS) || (letters == 4 && digits == 4)
        if (!known) return typed.uppercase().trim()
        return "${body.take(letters)}-${body.drop(letters)}"
    }
}

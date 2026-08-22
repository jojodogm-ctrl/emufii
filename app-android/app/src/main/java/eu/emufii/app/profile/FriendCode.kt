package eu.emufii.app.profile

import java.security.SecureRandom

/**
 * The string you give someone so they can add you.
 *
 * It is not a key into a directory: there is no directory. The code *is* the
 * identity, the same value the coordinator counts presence by, so adding a
 * friend needs no server round-trip and works while they are offline. That is
 * what keeps this feature free to run: no account, no database, nothing to
 * host beyond the coordinator that already exists.
 *
 * Alphabet is Crockford's base32, which drops I, L, O and U. The first three
 * because they are read back as 1 and 0 over a voice call or off a photo of a
 * screen; U because excluding it keeps accidental words out of generated codes.
 *
 * Eleven random symbols is 2^55, far past any risk of two players colliding or
 * of someone finding a live code by guessing, each attempt costs a request to
 * a rate-limited endpoint.
 *
 * The twelfth symbol is a checksum. Without it a typo is indistinguishable
 * from a friend who simply has not opened the app: with no directory to ask,
 * the app cannot tell "no such code" from "offline". The checksum lets it
 * reject a mistyped code on the spot rather than storing a friend who will
 * never come online.
 */
object FriendCode {

    const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /** Random symbols, before the trailing checksum. */
    const val RANDOM_SYMBOLS = 11

    /** Total length of a canonical (undashed) code. */
    const val LENGTH = RANDOM_SYMBOLS + 1

    private const val GROUP = 4

    private val secureRandom by lazy { SecureRandom() }

    fun generate(): String {
        val body = buildString {
            repeat(RANDOM_SYMBOLS) { append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]) }
        }
        return body + checksum(body)
    }

    /** `E7K29QM4XR8T` → `E7K2-9QM4-XR8T`, for display only. */
    fun format(code: String): String =
        code.chunked(GROUP).joinToString("-")

    /**
     * Canonical form of whatever the user typed, or null if it cannot be one of
     * our codes.
     *
     * Accepts lower case, stray dashes and spaces, and the four characters
     * Crockford maps back onto digits, someone reading a code off a screen
     * types the letter O for zero often enough that refusing it would be our
     * bug, not theirs.
     */
    fun normalize(input: String): String? {
        val cleaned = buildString {
            for (raw in input) {
                when (raw) {
                    '-', ' ', '\t', '\n' -> continue
                    else -> append(
                        when (raw.uppercaseChar()) {
                            'O' -> '0'
                            'I', 'L' -> '1'
                            else -> raw.uppercaseChar()
                        }
                    )
                }
            }
        }
        if (cleaned.length != LENGTH) return null
        if (cleaned.any { it !in ALPHABET }) return null
        if (cleaned.last() != checksum(cleaned.take(RANDOM_SYMBOLS))) return null
        return cleaned
    }

    fun isValid(input: String): Boolean = normalize(input) != null

    /**
     * Positional weights rather than a plain sum, so that swapping two adjacent
     * symbols, the other half of how people mistype a code, changes the
     * result. A plain sum would not notice.
     */
    private fun checksum(body: String): Char {
        var acc = 0
        for ((index, symbol) in body.withIndex()) {
            acc += ALPHABET.indexOf(symbol) * (index + 1)
        }
        return ALPHABET[acc % ALPHABET.length]
    }
}

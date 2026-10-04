package eu.emufii.app.profile

import java.security.SecureRandom

/** Eleven Crockford base32 symbols plus a check symbol. */
object FriendCode {

    const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    const val RANDOM_SYMBOLS = 11

    const val LENGTH = RANDOM_SYMBOLS + 1

    private const val GROUP = 4

    private val secureRandom by lazy { SecureRandom() }

    fun generate(): String {
        val body = buildString {
            repeat(RANDOM_SYMBOLS) { append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]) }
        }
        return body + checksum(body)
    }

    fun format(code: String): String =
        code.chunked(GROUP).joinToString("-")

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

    /** Positional weights so adjacent swaps are caught. */
    private fun checksum(body: String): Char {
        var acc = 0
        for ((index, symbol) in body.withIndex()) {
            acc += ALPHABET.indexOf(symbol) * (index + 1)
        }
        return ALPHABET[acc % ALPHABET.length]
    }
}

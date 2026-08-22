package eu.emufii.app.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendCodeTest {

    @Test
    fun `generated codes are valid and the right shape`() {
        repeat(200) {
            val code = FriendCode.generate()
            assertEquals(FriendCode.LENGTH, code.length)
            assertTrue(code, code.all { it in FriendCode.ALPHABET })
            assertTrue(code, FriendCode.isValid(code))
        }
    }

    @Test
    fun `generated codes differ`() {
        val codes = (1..500).map { FriendCode.generate() }.toSet()
        assertEquals(500, codes.size)
    }

    @Test
    fun `formatting groups by four and normalizing undoes it`() {
        val code = FriendCode.generate()
        val shown = FriendCode.format(code)
        assertEquals("${code.take(4)}-${code.drop(4).take(4)}-${code.takeLast(4)}", shown)
        assertEquals(code, FriendCode.normalize(shown))
    }

    @Test
    fun `lower case, spaces and stray dashes are accepted`() {
        val code = FriendCode.generate()
        val messy = " ${code.take(3).lowercase()}-${code.drop(3).lowercase()} "
        assertEquals(code, FriendCode.normalize(messy))
    }

    /**
     * The reason the alphabet excludes these: someone reading a code off a
     * screen types the letter for the digit, and being strict about it would be
     * our bug rather than theirs.
     */
    @Test
    fun `letters that look like digits are read as digits`() {
        // Build a code whose body contains 0 and 1, then type it the wrong way.
        val body = "0123456789A"
        val code = FriendCode.normalize(body + checksumOf(body))
            ?: error("fixture should be a valid code")
        val mistyped = code.replace('0', 'O').replace('1', 'l')
        assertEquals(code, FriendCode.normalize(mistyped))
    }

    @Test
    fun `a single wrong character is rejected`() {
        val code = FriendCode.generate()
        // Change the first symbol to a different one; the checksum must notice.
        val wrong = FriendCode.ALPHABET.first { it != code[0] }
        assertNull(FriendCode.normalize(wrong + code.drop(1)))
    }

    @Test
    fun `swapping two adjacent characters is rejected`() {
        // A plain sum would miss this, which is why the checksum is weighted.
        var caught = 0
        var tried = 0
        repeat(200) {
            val code = FriendCode.generate()
            val i = (0 until FriendCode.RANDOM_SYMBOLS - 1).first { code[it] != code[it + 1] }
            val swapped = StringBuilder(code).apply {
                val a = get(i)
                setCharAt(i, get(i + 1))
                setCharAt(i + 1, a)
            }.toString()
            tried++
            if (!FriendCode.isValid(swapped)) caught++
        }
        assertEquals(tried, caught)
    }

    @Test
    fun `wrong length is rejected`() {
        val code = FriendCode.generate()
        assertFalse(FriendCode.isValid(code.drop(1)))
        assertFalse(FriendCode.isValid(code + "Z"))
        assertFalse(FriendCode.isValid(""))
    }

    @Test
    fun `characters outside the alphabet are rejected`() {
        val code = FriendCode.generate()
        // U is the one excluded letter that isn't remapped to a digit.
        assertNull(FriendCode.normalize("U" + code.drop(1)))
        assertNull(FriendCode.normalize("*" + code.drop(1)))
    }

    /** Mirrors the private checksum, so the fixture above is a real code. */
    private fun checksumOf(body: String): Char {
        var acc = 0
        for ((index, symbol) in body.withIndex()) {
            acc += FriendCode.ALPHABET.indexOf(symbol) * (index + 1)
        }
        return FriendCode.ALPHABET[acc % FriendCode.ALPHABET.length]
    }
}

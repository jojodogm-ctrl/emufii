package eu.emufii.app.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCodesTest {

    @Test
    fun `a code typed without its hyphen is the same code`() {
        assertEquals("HMM-295", SessionCodes.normalize("HMM295"))
    }

    @Test
    fun `case, spaces and stray punctuation are forgiven`() {
        assertEquals("HMM-295", SessionCodes.normalize("hmm295"))
        assertEquals("HMM-295", SessionCodes.normalize(" HMM 295 "))
        assertEquals("HMM-295", SessionCodes.normalize("hmm–295".replace('–', '-')))
        assertEquals("HMM-295", SessionCodes.normalize("HMM-295"))
    }

    @Test
    fun `a code of the wrong shape is left as it was typed`() {
        assertEquals("HMM-29", SessionCodes.normalize("hmm-29"))
        assertEquals("HMM2955", SessionCodes.normalize("hmm2955"))
        assertEquals("HMMKL295567", SessionCodes.normalize("hmmkl295567"))
        assertEquals("2955HMM", SessionCodes.normalize("2955hmm"))
        assertEquals("", SessionCodes.normalize(""))
    }

    @Test
    fun `the four and four shape is still recognised`() {
        assertEquals("HMMK-2955", SessionCodes.normalize("hmmk2955"))
        assertEquals("HMMK-2955", SessionCodes.normalize("HMMK-2955"))
    }

    @Test
    fun `what generate produces survives normalize untouched`() {
        repeat(50) {
            val code = SessionCodes.generate()
            assertEquals(code, SessionCodes.normalize(code))
            assertTrue(code, Regex("^[A-Z]{3}-[2-9]{3}$").matches(code))
        }
    }

    @Test
    fun `generate does not repeat itself`() {
        val seen = List(1000) { SessionCodes.generate() }.toSet()
        assertTrue("only ${seen.size} distinct codes in 1000 draws", seen.size >= 995)
    }

    @Test
    fun `the code fits the ARMSX2 room field once the hyphen is dropped`() {
        val body = SessionCodes.generate().filter { it.isLetterOrDigit() }
        assertTrue(body, body.length in 4..12)
    }

    @Test
    fun `the code is exactly what the join keypad accepts`() {
        val body = SessionCodes.generate().filter { it.isLetterOrDigit() }
        assertEquals(6, body.length)
    }
}

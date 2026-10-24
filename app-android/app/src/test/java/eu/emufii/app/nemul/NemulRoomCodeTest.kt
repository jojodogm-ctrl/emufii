package eu.emufii.app.nemul

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NemulRoomCodeTest {
    @Test
    fun `native room code accepts lowercase and surrounding spaces`() {
        assertEquals("AB2D", NemulRoomCode.normalize(" ab2d "))
        assertTrue(NemulRoomCode.isValid(" ab2d "))
    }

    @Test
    fun `Emufii session codes and malformed invitations cannot be sent as native rooms`() {
        for (code in listOf("ABC-234", "ABC234", "ABC", "ABCDE", "AB/D", "AB D", "ÉBCD", "")) {
            assertFalse(code, NemulRoomCode.isValid(code))
        }
    }

    @Test
    fun `beta invitation keeps normal boot by default and requests liveboot only explicitly`() {
        assertEquals("nemul://join/VMB7", NemulRoomCode.joinUri(" vmb7 "))
        assertEquals("nemul://join/VMB7", NemulRoomCode.joinUri("VMB7", false))
        assertEquals("nemul://join/VMB7?liveboot=1", NemulRoomCode.joinUri("VMB7", true))
    }
}

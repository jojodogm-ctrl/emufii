package eu.emufii.app.netplay

import eu.emufii.app.profile.Profile
import org.junit.Assert.assertTrue
import eu.emufii.app.library.Backend
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

/** A name the emulator rejects shows up as a connection that silently never happens. */
class NetplayNamesTest {

    @Test
    fun `a room name stays within Azahar's 3 to 20`() {
        // "Room name must be between 3 and 20 characters", verbatim from Azahar 2126.0-rc5.
        for (code in listOf("", "A", "AB7X", "VERYLONGSESSIONCODE1234567890")) {
            val room = NetplayNames.roomName(code)
            assertTrue(
                "room name for code '$code' was '$room' (${room.length} chars)",
                room.length in NetplayNames.MIN_ROOM_NAME..NetplayNames.MAX_ROOM_NAME
            )
        }
    }

    @Test
    fun `the default profile name already clears the pseudo floor`() {
        // The onboarding pre-fills with it: tapping straight through must not leave a
        // name the emulator bounces.
        assertTrue(Profile.DEFAULT_NAME.length >= Profile.MIN_NAME_LENGTH)
    }

    @Test
    fun `Azahar never has its nickname rewritten`() {
        // Emufii had replaced a valid Azahar nickname with the profile name, and the
        // form refused the whole dialog while blaming the address.
        assertNull(NetplayNames.usernameFor(Backend.AZAHAR, "Clossv"))
        assertNull(NetplayNames.usernameFor(Backend.PPSSPP, "Clossv"))
        assertNull(NetplayNames.usernameFor(Backend.MELONDS, "Clossv"))
    }

    @Test
    fun `Eden gets the profile nickname`() {
        // Eden does not state it: two players with the same nickname cannot share a
        // room, and its default nickname is the same for everybody.
        assertEquals("Clossv", NetplayNames.usernameFor(Backend.EDEN, "Clossv"))
    }

    @Test
    fun `a nickname that is too short is padded, not refused`() {
        val filled = NetplayNames.usernameFor(Backend.EDEN, "Jo")

        assertEquals(NetplayNames.MIN_USERNAME, filled?.length)
        assertTrue("must stay recognisable: $filled", filled!!.startsWith("Jo"))
    }

    @Test
    fun `a nickname that is too long is cut`() {
        val long = NetplayNames.usernameFor(Backend.EDEN, "a".repeat(40))

        assertEquals(NetplayNames.MAX_USERNAME, long?.length)
    }

    @Test
    fun `with no named profile, nothing is touched`() {
        assertNull(NetplayNames.usernameFor(Backend.EDEN, null))
        assertNull(NetplayNames.usernameFor(Backend.EDEN, "   "))
    }
}

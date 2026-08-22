package eu.emufii.app.netplay

import eu.emufii.app.profile.Profile
import org.junit.Assert.assertTrue
import eu.emufii.app.library.Backend
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The names Emufii types into the emulator's form have to be ones it accepts,
 * a rejected name shows up as a connection that silently never happens.
 */
class NetplayNamesTest {

    @Test
    fun `a room name stays within Azahar's 3 to 20`() {
        // "Room name must be between 3 and 20 characters", verbatim from the
        // 2126.0-rc5 resources.
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
        // The onboarding pre-fills with it, so a user who taps straight through
        // must not end up with a name the emulator bounces.
        assertTrue(Profile.DEFAULT_NAME.length >= Profile.MIN_NAME_LENGTH)
    }

    @Test
    fun `Azahar n'a jamais son pseudo réécrit`() {
        // The regression this file holds: Emufii had replaced a valid Azahar
        // nickname with the profile name, and the form refused the whole dialog
        // while blaming the address.
        assertNull(NetplayNames.usernameFor(Backend.AZAHAR, "Clossv"))
        assertNull(NetplayNames.usernameFor(Backend.PPSSPP, "Clossv"))
        assertNull(NetplayNames.usernameFor(Backend.MELONDS_WFC, "Clossv"))
    }

    @Test
    fun `Eden reçoit le pseudo du profil`() {
        // The opposite, and for a reason Eden does not state: two players with
        // the same nickname cannot share a room, and its default nickname is the
        // same for everybody.
        assertEquals("Clossv", NetplayNames.usernameFor(Backend.EDEN, "Clossv"))
    }

    @Test
    fun `un pseudo trop court est complété, pas refusé`() {
        // A "Jo" profile is legitimate inside Emufii; it is the emulator that
        // requires five characters, so it is for us to comply.
        val filled = NetplayNames.usernameFor(Backend.EDEN, "Jo")

        assertEquals(NetplayNames.MIN_USERNAME, filled?.length)
        assertTrue("doit rester reconnaissable : $filled", filled!!.startsWith("Jo"))
    }

    @Test
    fun `un pseudo trop long est coupé`() {
        val long = NetplayNames.usernameFor(Backend.EDEN, "a".repeat(40))

        assertEquals(NetplayNames.MAX_USERNAME, long?.length)
    }

    @Test
    fun `sans profil nommé, on ne touche à rien`() {
        // Writing a fabricated name would be worse than leaving the emulator's,
        // which at least was chosen by somebody.
        assertNull(NetplayNames.usernameFor(Backend.EDEN, null))
        assertNull(NetplayNames.usernameFor(Backend.EDEN, "   "))
    }
}

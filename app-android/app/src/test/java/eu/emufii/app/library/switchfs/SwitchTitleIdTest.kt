package eu.emufii.app.library.switchfs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwitchTitleIdTest {

    // The Thor's Let's Go Pikachu NSP holds four NCAs and no ticket: the filename is all there is.
    @Test
    fun ticketlessNspFallsBackToTheFilename() {
        assertEquals("010003F003A34000", SwitchReader.titleIdFromName("Pokémon Let's Go, Pikachu! [010003F003A34000][v0].nsp"))
    }

    @Test
    fun updateAndDlcReduceToTheGame() {
        assertEquals("010003F003A34000", SwitchReader.baseTitleId("010003F003A34800"))
        assertEquals("010003F003A34000", SwitchReader.baseTitleId("010003F003A35001"))
        assertEquals("010003F003A34000", SwitchReader.titleIdFromName("Game [010003f003a34800][v65536].nsp"))
    }

    @Test
    fun noTagNoId() {
        assertNull(SwitchReader.titleIdFromName("Pokemon Sword.nsp"))
        assertNull(SwitchReader.titleIdFromName("hash 6158a98544f6ebb462b0dcb3d44cbe73.nca"))
    }
}

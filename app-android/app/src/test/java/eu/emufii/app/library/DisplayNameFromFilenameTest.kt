package eu.emufii.app.library

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayNameFromFilenameTest {

    @Test fun `scene tags and release numbers go`() {
        assertEquals("Metroid Dread", displayNameFromFilename("Metroid Dread [NSP] 0919838.nsp"))
        assertEquals("The Legend of Zelda", displayNameFromFilename("The Legend of Zelda [Decrypted].3ds"))
        assertEquals("Mario Kart 7", displayNameFromFilename("Mario Kart 7 (Europe) [Decrypted].cia"))
    }

    @Test fun `a region in parentheses goes, as before`() {
        assertEquals("Game", displayNameFromFilename("Game (USA) (Rev 1).nsp"))
    }

    @Test fun `a number that is part of the title stays`() {
        assertEquals("Portal 2", displayNameFromFilename("Portal 2.nsp"))
        assertEquals("Final Fantasy VII", displayNameFromFilename("Final Fantasy VII.nsp"))
        assertEquals("Gran Turismo 4", displayNameFromFilename("Gran Turismo 4 (USA).iso"))
    }

    @Test fun `a filename reduced to tags falls back on itself`() {
        assertEquals("[NSP]", displayNameFromFilename("[NSP].nsp"))
    }
}

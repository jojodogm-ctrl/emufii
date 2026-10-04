package eu.emufii.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomTagsTest {

    @Test fun `a Sony serial names its own region`() {
        assertEquals(
            "Europe",
            RomTagReader.read(Console.PS2, "SLES-53717", null, "Whatever.iso").region
        )
        assertEquals(
            "USA",
            RomTagReader.read(Console.PSP, "ULUS-10041", null, "Whatever.iso").region
        )
    }

    @Test fun `a Nintendo game code names its region by position`() {
        assertEquals(
            "USA",
            RomTagReader.read(Console.DS, "NDS-ADAE-01", null, "x.nds").region
        )
        assertEquals(
            "Europe",
            RomTagReader.read(Console.GAMECUBE, "GALP01", null, "x.iso").region
        )
        // `A` is the 3DS's region-free letter, and it is a fact, not a gap.
        assertEquals(
            "World",
            RomTagReader.read(Console.THREE_DS, "CTR-P-ARRA", null, "x.3ds").region
        )
    }

    @Test fun `the filename answers when the serial cannot`() {
        val tags = RomTagReader.read(Console.SWITCH, null, "0100152000022000", "Game (Europe) (Rev 1).nsp")
        assertEquals("Europe", tags.region)
        assertEquals("Europe", tags.line())
    }

    @Test fun `silence stays silent`() {
        val tags = RomTagReader.read(Console.SWITCH, null, null, "Game.nsp")
        assertNull(tags.region)
        assertNull(tags.line())
    }

    @Test fun `a title in brackets is not a region`() {
        assertNull(RomTagReader.read(Console.SWITCH, null, null, "Disney's Aladdin.nsp").region)
    }
}

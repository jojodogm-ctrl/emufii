package eu.emufii.app.ps2

import eu.emufii.app.ps2.Ps2Armsx2Folder.BiosPick
import org.junit.Assert.assertEquals
import org.junit.Test

class Ps2BiosPickTest {

    private val dumps = listOf("SCPH-39001.bin", "scph-70004.BIN", "SCPH-90006.bin")

    // A player with three dumps: ARMSX2 boots the one its settings name, so the card follows it.
    @Test
    fun severalDumpsFollowArmsx2Choice() {
        assertEquals(BiosPick.Chosen(1), Ps2Armsx2Folder.pickBios(dumps, "SCPH-70004.bin"))
    }

    @Test
    fun choiceMissingFromTheFolderIsNotGuessed() {
        assertEquals(BiosPick.Missing, Ps2Armsx2Folder.pickBios(dumps, "SCPH-50004.bin"))
        assertEquals(BiosPick.Missing, Ps2Armsx2Folder.pickBios(listOf("SCPH-39001.bin"), "SCPH-50004.bin"))
    }

    @Test
    fun withoutAChoiceOnlyASingleDumpIsTaken() {
        assertEquals(BiosPick.Ambiguous, Ps2Armsx2Folder.pickBios(dumps, null))
        assertEquals(BiosPick.Chosen(0), Ps2Armsx2Folder.pickBios(listOf("SCPH-39001.bin"), null))
        assertEquals(BiosPick.Missing, Ps2Armsx2Folder.pickBios(emptyList(), null))
    }
}

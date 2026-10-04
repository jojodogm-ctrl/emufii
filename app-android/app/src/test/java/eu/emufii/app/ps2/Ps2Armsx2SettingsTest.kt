package eu.emufii.app.ps2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ps2Armsx2SettingsTest {

    @Test
    fun `a real mirror yields both slots bios and relevant game overrides`() {
        val mirror = """
            {"global":{"memoryCardSlot1Enabled":true,"memoryCardSlot1Filename":"mcd001.ps2",
            "biosFilename":"scph-39001.bin","memoryCardSlot2Enabled":false,
            "memoryCardSlot2Filename":"archive.ps2"},"games":{
            "SLES-52942":{"memoryCardSlot1Filename":"midnight.ps2"},
            "SLUS-20911":{"biosFilename":"scph-70012.bin"},
            "SLUS-00001":{"memoryCardSlot1Enabled":false},
            "SLUS-00000":{"renderer":13}}}
        """.trimIndent()
        val parsed = Ps2Armsx2Settings.parse(mirror)
        assertEquals(true, parsed.slot1Enabled)
        assertEquals("mcd001.ps2", parsed.slot1Filename)
        assertEquals("scph-39001.bin", parsed.biosFilename)
        assertFalse(parsed.slot2Enabled)
        assertEquals("archive.ps2", parsed.slot2Filename)
        assertTrue(parsed.hasRelevantGameOverrides)
        assertEquals(
            setOf(
                Ps2Armsx2Settings.GameOverride("SLES-52942", slot1Filename = "midnight.ps2"),
                Ps2Armsx2Settings.GameOverride("SLUS-20911", biosFilename = "scph-70012.bin"),
                Ps2Armsx2Settings.GameOverride("SLUS-00001", slot1Enabled = false),
            ),
            parsed.gameOverrides.toSet(),
        )
    }

    @Test
    fun `absent keys fall back to armsx2 defaults`() {
        val parsed = Ps2Armsx2Settings.parse("""{"global":{}}""")
        assertEquals(true, parsed.slot1Enabled)
        assertEquals("mcd001.ps2", parsed.slot1Filename)
        assertNull(parsed.biosFilename)
        assertEquals(true, parsed.slot2Enabled)
        assertEquals("mcd002.ps2", parsed.slot2Filename)
        assertFalse(parsed.hasRelevantGameOverrides)
    }

    @Test
    fun `a slot turned off says so`() {
        val parsed = Ps2Armsx2Settings.parse(
            """{"global":{"memoryCardSlot1Enabled":false,"memoryCardSlot1Filename":"other.ps2"}}""",
        )
        assertFalse(parsed.slot1Enabled)
        assertEquals("other.ps2", parsed.slot1Filename)
    }

    @Test
    fun `a broken mirror parses as defaults rather than throwing`() {
        val parsed = Ps2Armsx2Settings.parse("not json at all")
        assertEquals(Ps2Armsx2Settings.Parsed(), parsed)
    }

    @Test
    fun `the native ini parses too, measured off a live install`() {
        // Verbatim shape of PCSX2-Android.ini on a real data folder.
        val ini = """
            [Filenames]
            BIOS = SCPH-77004_BIOS_V15_EUR_220.ROM0

            [MemoryCards]
            Slot1_Enable = true
            Slot1_Filename = emufii-ps2-net.ps2
            Slot2_Enable = true
            Slot2_Filename = mcd001.ps2
        """.trimIndent()
        val parsed = Ps2Armsx2Settings.parseIni(ini)
        assertEquals(true, parsed.slot1Enabled)
        assertEquals("emufii-ps2-net.ps2", parsed.slot1Filename)
        assertEquals("SCPH-77004_BIOS_V15_EUR_220.ROM0", parsed.biosFilename)
        assertEquals(true, parsed.slot2Enabled)
        assertEquals("mcd001.ps2", parsed.slot2Filename)
    }

    @Test
    fun `the native ini honours a disabled slot and missing keys`() {
        assertEquals(
            false,
            Ps2Armsx2Settings.parseIni("[MemoryCards]\nSlot1_Enable = false").slot1Enabled,
        )
        assertEquals(
            false,
            Ps2Armsx2Settings.parseIni("[MemoryCards]\nSlot2_Enable = false").slot2Enabled,
        )
        assertEquals(Ps2Armsx2Settings.Parsed(), Ps2Armsx2Settings.parseIni("[GameList]\nSomething = else"))
    }

    @Test
    fun `games-only mirror keeps global defaults and preserves explicit empty overrides`() {
        val parsed = Ps2Armsx2Settings.parse(
            """{"games":{"SCUS-00001":{"biosFilename":""},"SCUS-00002":{"memoryCardSlot1Filename":""}}}""",
        )
        assertEquals(true, parsed.slot1Enabled)
        assertEquals("mcd001.ps2", parsed.slot1Filename)
        assertEquals(
            listOf(
                Ps2Armsx2Settings.GameOverride("SCUS-00001", biosFilename = ""),
                Ps2Armsx2Settings.GameOverride("SCUS-00002", slot1Filename = ""),
            ),
            parsed.gameOverrides,
        )
    }
}

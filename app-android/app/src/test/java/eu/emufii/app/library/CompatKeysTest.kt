package eu.emufii.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one thing this feature lives or dies on: a rating given once has to reach
 * the same game in every language. Each test here is a pair of regional dumps
 * that must land on one key — or a pair that must never be confused.
 */
class CompatKeysTest {

    @Test
    fun `two 3DS regions share a family key`() {
        val jp = compatKeys(Console.THREE_DS, "CTR-P-ARRJ", null)
        val eu = compatKeys(Console.THREE_DS, "CTR-P-ARRP", null)
        assertTrue("3ds:ARR" in jp)
        assertTrue("3ds:ARR" in eu)
        // And they stay distinguishable, for a rating that really is region-only.
        assertTrue("3ds:ARRJ" in jp)
        assertTrue("3ds:ARRP" in eu)
    }

    @Test
    fun `a digital 3DS product code is read from its end, not its prefix`() {
        // CTR-N- for a download, KTR-P- for a New 3DS title: matching on the
        // prefix would need a list of prefixes, and the list would be short one.
        assertTrue("3ds:ARR" in compatKeys(Console.THREE_DS, "CTR-N-ARRJ", null))
        assertTrue("3ds:ARR" in compatKeys(Console.THREE_DS, "KTR-P-ARRE", null))
    }

    @Test
    fun `two DS regions share a family key`() {
        val us = compatKeys(Console.DS, "NDS-ADAE-01", null)
        val eu = compatKeys(Console.DS, "NDS-ADAP-01", null)
        assertTrue("ds:ADA" in us)
        assertTrue("ds:ADA" in eu)
    }

    @Test
    fun `the DS key ignores the maker code, because no index publishes one`() {
        // GameTDB keys DS titles on the four-character game code alone. Keeping
        // the maker code here would have meant the tool could never write a key
        // the app would match, so a DS badge would simply never appear.
        val a = compatKeys(Console.DS, "NDS-ADAE-01", null)
        val b = compatKeys(Console.DS, "NDS-ADAE-52", null)
        assertEquals(a, b)
    }

    @Test
    fun `two Wii regions share a family key`() {
        val us = compatKeys(Console.WII, "RMCE01", null)
        val eu = compatKeys(Console.WII, "RMCP01", null)
        assertTrue("wii:RMC01" in us)
        assertTrue("wii:RMC01" in eu)
    }

    @Test
    fun `the GameCube and the Wii never share a key`() {
        val gc = compatKeys(Console.GAMECUBE, "GALE01", null)
        val wii = compatKeys(Console.WII, "GALE01", null)
        assertEquals(emptySet<String>(), gc.toSet() intersect wii.toSet())
    }

    @Test
    fun `the PSP and the PS2 are matched on the exact serial only`() {
        // No family key: nothing links UCUS-98653 to UCES-00842 but a table, and
        // inventing a rule here would rate a game from its neighbour.
        assertEquals(listOf("psp:UCUS98653"), compatKeys(Console.PSP, "PSP-UCUS98653", null))
        assertEquals(listOf("ps2:SLES-50001"), compatKeys(Console.PS2, "SLES-50001", null))
    }

    @Test
    fun `the Switch is matched on its worldwide title id`() {
        assertEquals(
            listOf("switch:01007EF00011E000"),
            compatKeys(Console.SWITCH, null, "01007ef00011e000")
        )
    }

    @Test
    fun `a ROM with no identifier yields no keys rather than a wrong one`() {
        // A homebrew, or a dump too damaged to read: it gets no badge, which is
        // the honest answer. A fallback key would file every unreadable game
        // under one verdict.
        assertTrue(compatKeys(Console.THREE_DS, null, null).isEmpty())
        assertTrue(compatKeys(Console.PS2, null, null).isEmpty())
    }
}

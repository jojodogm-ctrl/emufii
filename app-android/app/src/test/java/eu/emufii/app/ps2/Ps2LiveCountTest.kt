package eu.emufii.app.ps2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Ps2LiveCountTest {

    // Shape of ps2online.com's table as served on 2026-10-07.
    private val page = """
        <b>Number of entries to each game</font><br><font size=5>(Approximate Values)</font></b>
        <table border=1>
        <tr><td><h1>Game</h1></td><td><h2>Online</h2></td><td><h2>Rooms</h2></td></tr>
        <tr><td><font size=5>Area 51</font></td><td><font size=5>0</font></td><td><font size=5>0</font></td></tr>
        <tr><td><font size=5>Battlefield 2: Modern Combat</font></td><td><font size=5><a style=text-decoration:none; href=https://bf2mc.com/#servers>1</a></font></td><td>6</td></tr>
        <tr><td><font size=5>Blitz: The League</font></td><td><font size=5>?</font></td><td>?</td></tr>
        <tr><td><font size=5>Crash &#039;N&#039; Burn</font></td><td><font size=5>3</font></td><td>0</td></tr>
        <tr><td><font size=5>Pro Evolution Soccer 6 (PES.es)</font></td><td><font size=5>178</font></td><td>0</td></tr>
        </table>
    """.trimIndent()

    @Test
    fun readsTheOnlineColumnThroughLinksAndEntities() {
        val table = Ps2LiveCount.parseActivity(page)
        assertEquals(0, table["Area 51"])
        assertEquals(1, table["Battlefield 2: Modern Combat"])
        assertEquals(3, table["Crash 'N' Burn"])
    }

    @Test
    fun theRenamedParentheticalIsDropped() {
        assertEquals(178, Ps2LiveCount.parseActivity(page)["Pro Evolution Soccer 6"])
    }

    @Test
    fun skipsHeaderAndUnknownCounts() {
        val table = Ps2LiveCount.parseActivity(page)
        assertTrue("Game" !in table)
        assertTrue("Blitz: The League" !in table)
    }

    @Test
    fun aPageWithoutTheTableYieldsNothing() {
        assertTrue(Ps2LiveCount.parseActivity("<html>maintenance</html>").isEmpty())
    }
}

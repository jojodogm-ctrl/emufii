package eu.emufii.app.psp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PspServersTest {

    /** Shape of adhoc.eahub.eu/data.json, 2026-09-28. */
    private val json = """
        {"games":[
          {"name":"Metal Gear Solid: Peace Walker","usercount":"2","groups":[],"game_ids":["ULUS10509","ULES01372"]},
          {"name":"UFC Undisputed 2010","usercount":"5","groups":[],"game_ids":["ULUS10508"]}
        ]}
    """.trimIndent()

    /** Shape of socom.cc/status.xml, 2026-09-28: names only. */
    private val xml = """
        <prometheus usercount="201">
            <game name="PES 2013: Pro Evolution Soccer" usercount="3"><group name="LOBBY" usercount="3" /></game>
            <game name="Metal Slug XX" usercount="1" />
        </prometheus>
    """.trimIndent()

    @Test
    fun `data json counts by disc id, whatever the region`() {
        assertEquals(2, PspServers.countJson(json, "ULES01372", "Peace Walker EU"))
        assertEquals(0, PspServers.countJson(json, "ULUS10999", "Something else"))
    }

    @Test
    fun `status xml counts by title`() {
        assertEquals(1, PspServers.countXml(xml, "Metal Slug XX"))
        assertEquals(0, PspServers.countXml(xml, "Tekken 6"))
    }

    @Test
    fun `titles match loosely but not on nothing`() {
        assertTrue(PspServers.sameTitle("Monster Hunter Freedom Unite", "MONSTER HUNTER FREEDOM UNITE"))
        assertFalse(PspServers.sameTitle("", "Tekken 6"))
    }
}

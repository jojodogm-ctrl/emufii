package eu.emufii.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayRegionsTest {

    @Test
    fun `reads the mean from Android's ping summary`() {
        val out = """
            3 packets transmitted, 3 received, 0% packet loss, time 402ms
            rtt min/avg/max/mdev = 88.411/91.203/95.870/3.012 ms
        """.trimIndent()
        assertEquals(91.203, RelayRegions.parseAvg(out)!!, 0.001)
    }

    @Test
    fun `an unreachable host has no mean`() {
        assertNull(RelayRegions.parseAvg("3 packets transmitted, 0 received, 100% packet loss"))
    }

    @Test
    fun `a clearly nearer relay wins`() {
        assertEquals("na", RelayRegions.autoPick(listOf("eu" to 95.0, "na" to 20.0)))
    }

    @Test
    fun `within the margin the default keeps the session`() {
        assertEquals("eu", RelayRegions.autoPick(listOf("eu" to 30.0, "na" to 22.0)))
    }

    @Test
    fun `nothing measured sends no region`() {
        assertNull(RelayRegions.autoPick(listOf("eu" to null, "na" to null)))
    }

    @Test
    fun `the default unmeasured leaves the one that answered`() {
        assertEquals("na", RelayRegions.autoPick(listOf("eu" to null, "na" to 40.0)))
    }

    private fun opt(id: String) = RelayRegions.Option(RelayRegion(id, id, "$id.test"), null)

    @Test
    fun `a manual pick wins over automatic`() {
        assertEquals("na", RelayRegions.resolved(listOf(opt("eu"), opt("na")), "na", "eu"))
    }

    @Test
    fun `a manual pick the coordinator no longer offers falls back to automatic`() {
        assertEquals("eu", RelayRegions.resolved(listOf(opt("eu")), "na", "eu"))
    }
}

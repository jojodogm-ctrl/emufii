package eu.emufii.app.ps2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ps2RevivalTest {

    @Test
    fun everyListedResolverIsAnIpv4Address() {
        Ps2Revival.servers.values.forEach { assertTrue(it.host, Ps2Revival.isUsable(it.host)) }
    }

    @Test
    fun malformedResolversAreRefused() {
        listOf("", "1.2.3", "1.2.3.256", "psrewired.com", "1.2.3.4.5").forEach {
            assertFalse(it, Ps2Revival.isUsable(it))
        }
    }

    @Test
    fun defaultIsTheFirstServer() {
        assertEquals(Ps2Revival.servers.values.first().host, Ps2Revival.DEFAULT_DNS)
    }

    @Test
    fun picksKeepTheBaseOrderAndDropUnknownIds() {
        val picks = Ps2Revival.picks(listOf("ps2online", "nope", "psrewired"))
        assertEquals(listOf("104.237.9.163", "67.222.156.250"), picks.map { it.server.host })
        assertTrue(Ps2Revival.picks(emptyList()).isEmpty())
    }

    @Test
    fun onlineMergeReplacesLocalLinkModeAndKeepsTheRest() {
        val original = "[DEV9/Eth]\nEthEnable = true\nEthApi = Local Link\nLocalLinkPort = 19072\n\n[EmuCore]\nFoo = 1\n"
        val merged = Ps2GameSettings.merge(
            original,
            linkedMapOf("DEV9/Eth" to linkedMapOf<String, String?>("EthApi" to "Sockets", "ModeDNS1" to "Manual", "DNS1" to "67.222.156.250")),
        )
        assertTrue(merged.contains("EthApi = Sockets"))
        assertFalse(merged.contains("EthApi = Local Link"))
        assertTrue(merged.contains("DNS1 = 67.222.156.250"))
        assertTrue(merged.contains("[EmuCore]\nFoo = 1"))
    }
}

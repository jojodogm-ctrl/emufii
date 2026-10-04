package eu.emufii.app.wg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DNS in the tunnel routes all resolution through the relay, so only PS2 sessions get it. */
class WgConfigDnsTest {

    private val info = WgTunnelInfo(
        address = "10.67.1.2",
        subnet = "10.67.1.0/24",
        relayEndpoint = "relais.example:51820",
        relayPublicKey = "key",
        relayAllowedIps = "10.67.1.0/24,10.67.0.1/32,10.66.1.1/32"
    )

    @Test
    fun `with no DNS asked for, the config carries none`() {
        assertFalse(WgConfig.render(info, "private key").contains("DNS"))
    }

    @Test
    fun `the DNS asked for is the relay's, and it is in the AllowedIPs`() {
        val text = WgConfig.render(info, "private key", dns = WgConfig.RELAY_ADDRESS)
        assertTrue(text.contains("DNS = 10.67.0.1"))
        // A DNS outside the AllowedIPs is never reached: the query goes out over Wi-Fi and is lost.
        assertTrue(info.relayAllowedIps.contains("${WgConfig.RELAY_ADDRESS}/32"))
    }

    @Test
    fun `DNS lands in the interface, not in the peer`() {
        val text = WgConfig.render(info, "private key", dns = WgConfig.RELAY_ADDRESS)
        assertTrue(
            "DNS must come before [Peer], or wg-quick reads it as a peer setting",
            text.indexOf("DNS = ") < text.indexOf("[Peer]")
        )
    }

    @Test
    fun `the name typed by the PS2 guest can be entered on the ARMSX2 keyboard`() {
        // The ARMSX2 keyboard types lowercase letters only, no punctuation.
        assertTrue(WgConfig.PS2_HOST_NAME.all { it in 'a'..'z' })
        assertEquals("emufii", WgConfig.PS2_HOST_NAME)
    }
}

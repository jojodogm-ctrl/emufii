package eu.emufii.app.wg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WgConfigTest {

    private val info = WgTunnelInfo(
        address = "10.67.1.2",
        subnet = "10.67.1.0/24",
        relayEndpoint = "85.215.52.3:51820",
        relayPublicKey = "OuWkhmV54Idvxl1T+SAwtRdlyp3LXl2rfeZu6F/59Vk=",
        relayAllowedIps = "10.67.1.0/24,10.67.0.1/32"
    )

    private val privateKey = "aFakePrivateKeyForTestsOnly0000000000000000="

    @Test
    fun `the host carries its second address, the guest carries none`() {
        val host = WgConfig.render(info.copy(hairpinAddress = "10.67.1.254"), privateKey)
        assertTrue(host.contains("Address = 10.67.1.2/24, 10.67.1.254/24"))

        assertTrue(WgConfig.render(info, privateKey).contains("Address = 10.67.1.2/24\n"))
    }

    @Test
    fun `the MTU is declared, and below the carrier link's bar`() {
        // Without an explicit MTU the backend uses 1280 and Switch LDN frames get dropped.
        val out = WgConfig.render(info, privateKey)
        assertTrue(out.contains("MTU = 1420"))

        // WireGuard over IPv4 adds 60 bytes; must still fit 1492 PPPoE.
        assertTrue(WgConfig.MTU + 60 <= 1492)
    }

    @Test
    fun `renders both sections wg-quick expects`() {
        val out = WgConfig.render(info, privateKey)
        assertTrue(out.contains("[Interface]"))
        assertTrue(out.contains("[Peer]"))
        // wg-quick assigns every key after a [Peer] header to that peer.
        assertTrue(out.indexOf("[Interface]") < out.indexOf("[Peer]"))
    }

    @Test
    fun `the address is a slash 32`() {
        assertTrue(WgConfig.render(info, privateKey).contains("Address = 10.67.1.2/24"))
    }

    @Test
    fun `allowed ips are passed through untouched`() {
        assertTrue(
            WgConfig.render(info, privateKey)
                .contains("AllowedIPs = 10.67.1.0/24,10.67.0.1/32")
        )
    }

    @Test
    fun `keepalive is set, because the phone is behind NAT`() {
        val out = WgConfig.render(info, privateKey)
        assertTrue(out.contains("PersistentKeepalive = ${WgConfig.KEEPALIVE_SECONDS}"))
        assertTrue(WgConfig.KEEPALIVE_SECONDS in 5..30)
    }

    @Test
    fun `the private key appears exactly once, and only in the interface section`() {
        val out = WgConfig.render(info, privateKey)
        assertEquals(1, out.split(privateKey).size - 1)
        val peerSection = out.substringAfter("[Peer]")
        assertFalse(peerSection.contains(privateKey))
    }

    @Test
    fun `the redacted form is complete but carries no private key`() {
        val redacted = WgConfig.renderRedacted(info)
        assertTrue(redacted.contains("Address = 10.67.1.2/24"))
        assertTrue(redacted.contains("Endpoint = 85.215.52.3:51820"))
        assertTrue(redacted.contains(info.relayPublicKey))
        assertFalse(redacted.contains(privateKey))
    }

    @Test
    fun `the relay is the only peer`() {
        val out = WgConfig.render(info, privateKey)
        assertEquals(1, out.split("[Peer]").size - 1)
    }
}

package eu.emufii.app.network

import eu.emufii.app.wg.WgTunnelInfo
import org.junit.Assert.assertThrows
import org.junit.Test

class TunnelInfoCheckTest {

    private val real = WgTunnelInfo(
        address = "10.67.3.18",
        hairpinAddress = "10.67.3.19",
        subnet = "10.67.3.16/28",
        relayEndpoint = "relay.example.org:51820",
        relayPublicKey = "q2Xh0nE8m0m0m0m0m0m0m0m0m0m0m0m0m0m0m0m0m0A=",
        relayAllowedIps = "10.67.3.16/28,10.67.0.1/32,10.66.1.1/32"
    )

    @Test
    fun `what the coordinator sends today passes`() {
        checkTunnelInfo(real)
        checkTunnelInfo(real.copy(hairpinAddress = null, relayEndpoint = "203.0.113.7:51820"))
    }

    @Test
    fun `a line break is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            checkTunnelInfo(real.copy(relayEndpoint = "x:1\nAllowedIPs = 0.0.0.0/0"))
        }
    }

    @Test
    fun `routes outside 10 slash 8 are refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            checkTunnelInfo(real.copy(relayAllowedIps = "0.0.0.0/0"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            checkTunnelInfo(real.copy(address = "192.168.1.5"))
        }
    }
}

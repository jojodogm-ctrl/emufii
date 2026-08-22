package eu.emufii.app.wg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rendered tunnel configuration.
 *
 * Worth testing on its own because every mistake here fails the same unhelpful
 * way, a tunnel that comes up and carries nothing, and because the two
 * load-bearing choices (the subnet prefix on the address, which Switch LDN reads
 * as its mask, see docs/M19_SWITCH_LDN.md, and the relay's /32 in AllowedIPs)
 * both look like details until traffic goes missing.
 */
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
        // This is the address the host's ad hoc server records and then hands out
        // to the other players: unless it is carried here, their packets arrive
        // through the tunnel and are dropped for want of a recipient.
        val host = WgConfig.render(info.copy(hairpinAddress = "10.67.1.254"), privateKey)
        assertTrue(host.contains("Address = 10.67.1.2/24, 10.67.1.254/24"))

        // And the guest keeps exactly the old configuration: one address.
        assertTrue(WgConfig.render(info, privateKey).contains("Address = 10.67.1.2/24\n"))
    }

    @Test
    fun `le MTU est declare, et sous la barre du lien porteur`() {
        // With no explicit line the backend falls back to 1280, and Switch LDN
        // breaks on it: discovery and connection get through, then the game frames
        // exceed it and are dropped without fragmentation. Measured on the Thor,
        // 1252 bytes of payload got through, 1300 did not.
        val out = WgConfig.render(info, privateKey)
        assertTrue(out.contains("MTU = 1420"))

        // The WireGuard header costs 60 bytes over IPv4: the carrying packet has
        // to fit inside a 1492 PPPoE, otherwise one silent loss is swapped for
        // another.
        assertTrue(WgConfig.MTU + 60 <= 1492)
    }

    @Test
    fun `renders both sections wg-quick expects`() {
        val out = WgConfig.render(info, privateKey)
        assertTrue(out.contains("[Interface]"))
        assertTrue(out.contains("[Peer]"))
        // The interface must come first: wg-quick assigns every key after a [Peer]
        // header to that peer.
        assertTrue(out.indexOf("[Interface]") < out.indexOf("[Peer]"))
    }

    @Test
    fun `the address is a slash 32`() {
        // On Android the tunnel's routes come from the peer's AllowedIPs, not from
        // the interface address, so this only has to name the device. A wider
        // prefix here would claim to own addresses belonging to other players.
        assertTrue(WgConfig.render(info, privateKey).contains("Address = 10.67.1.2/24"))
    }

    @Test
    fun `allowed ips are passed through untouched`() {
        // This single line is what makes both the other players and the relay
        // reachable, it is where Android gets the tunnel's routes from.
        assertTrue(
            WgConfig.render(info, privateKey)
                .contains("AllowedIPs = 10.67.1.0/24,10.67.0.1/32")
        )
    }

    @Test
    fun `keepalive is set, because the phone is behind NAT`() {
        // Without it the relay loses its mapping to the phone after a minute or so
        // and the player silently stops being reachable.
        val out = WgConfig.render(info, privateKey)
        assertTrue(out.contains("PersistentKeepalive = ${WgConfig.KEEPALIVE_SECONDS}"))
        // The upper bound is held by NAT traversal (a minute is enough to lose
        // the mapping), the lower bound by common sense: a beat tighter than 5 s
        // would wake the radio for nothing. The move from 25 to 10 s comes from a
        // measurement: a link left idle charges up to 369 ms on the first packet,
        // against 46 ms kept warm.
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
        // Useful in a bug report only if it still shows the parts that go wrong.
        assertTrue(redacted.contains("Address = 10.67.1.2/24"))
        assertTrue(redacted.contains("Endpoint = 85.215.52.3:51820"))
        assertTrue(redacted.contains(info.relayPublicKey))
        assertFalse(redacted.contains(privateKey))
    }

    @Test
    fun `the relay is the only peer`() {
        // Hub-and-spoke: a client that held the other players' keys would be
        // carrying something it has no use for.
        val out = WgConfig.render(info, privateKey)
        assertEquals(1, out.split("[Peer]").size - 1)
    }
}

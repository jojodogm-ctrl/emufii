package eu.emufii.app.wg

/**
 * What the coordinator hands back when you claim an address on a session.
 *
 * Mirrors `POST /sessions/:code/peers`. No other player's key: the topology is
 * hub-and-spoke, so a client's only peer is the relay.
 */
data class WgTunnelInfo(
    /** This device's address on the session subnet, e.g. `10.67.1.2`. */
    val address: String,
    /**
     * The host's second address, `10.67.<n>.254`, null on a guest.
     *
     * The relay rewrites the host's self-connection to this address, and it is
     * the one the ad hoc server hands out. Without it here, packets sent to the
     * host arrive through the tunnel and get dropped. See `relay/firewall.js`.
     */
    val hairpinAddress: String? = null,
    /** The session subnet, e.g. `10.67.1.0/24`. */
    val subnet: String,
    val relayEndpoint: String,
    val relayPublicKey: String,
    /** The relay's `AllowedIPs`: the session subnet plus the relay's own /32. */
    val relayAllowedIps: String
)

/**
 * Renders a wg-quick configuration, which is what the library parses.
 *
 * Text rather than `Interface.Builder`/`Peer.Builder`: one shape to get right,
 * loggable when a tunnel refuses to come up, and the format the WireGuard
 * documentation uses.
 */
object WgConfig {

    /**
     * Carrier NAT mappings expire well under a minute, and the relay can only
     * reach a peer it has a mapping for.
     *
     * Lowered from 25 s on 2026-08-02: between bursts the Wi-Fi radio sleeps and
     * the waking packet paid up to 369 ms, against 46 ms on a link kept awake.
     */
    const val KEEPALIVE_SECONDS = 10

    /**
     * Without this the backend defaults to 1280, the IPv6 floor.
     *
     * 1420 is the wg-quick default and is safe here: the WireGuard header costs
     * 60 bytes over IPv4, so the carrier packet is 1480 and crosses a 1500 link
     * as well as a 1492 PPPoE. Measured on the Thor, 2026-08-04: 1252 bytes get
     * through, 1300 is lost, nothing fragments. That silent drop was the LDN
     * failure mode, see `docs/M19_SWITCH_LDN.md`.
     */
    const val MTU = 1420

    /**
     * The relay's address inside the tunnel, which also answers DNS.
     *
     * Already present in `AllowedIPs`; spelled out here because the `DNS` line
     * names it separately.
     */
    const val RELAY_ADDRESS = "10.67.0.1"

    /**
     * The name a PS2 guest types instead of an address.
     *
     * ARMSX2's own keyboard has no dot key, so no IPv4 address can be entered.
     * Local Link resolves names, and one label is enough. The relay answers this
     * name with the `10.66.1.1` sentinel. See `relay/dns.js`.
     */
    const val PS2_HOST_NAME = "emufii"

    fun render(
        info: WgTunnelInfo,
        privateKeyBase64: String,
        /**
         * The DNS to advertise, or null for none.
         *
         * Null everywhere but PS2, deliberately: a VPN advertising a DNS takes
         * over the whole device's resolution. The other consoles dial addresses,
         * not names.
         */
        dns: String? = null
    ): String = buildString {
        appendLine("[Interface]")
        appendLine("PrivateKey = $privateKeyBase64")
        val addresses = listOfNotNull(info.address, info.hairpinAddress)
        // The interface address must carry the session prefix and not a /32:
        // Eden reads the mask to hand it to the game. See docs/NOTES_TUNNEL.md.
        val prefix = info.subnet.substringAfter('/', "24")
        appendLine("Address = ${addresses.joinToString(", ") { "$it/$prefix" }}")
        appendLine("MTU = $MTU")
        dns?.let { appendLine("DNS = $it") }
        appendLine()
        appendLine("[Peer]")
        appendLine("PublicKey = ${info.relayPublicKey}")
        appendLine("Endpoint = ${info.relayEndpoint}")
        appendLine("AllowedIPs = ${info.relayAllowedIps}")
        appendLine("PersistentKeepalive = $KEEPALIVE_SECONDS")
    }

    /** The same thing with the private key replaced, for logs and bug reports. */
    fun renderRedacted(info: WgTunnelInfo, dns: String? = null): String =
        render(info, "«clé privée retirée»", dns)
}

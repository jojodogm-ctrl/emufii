package eu.emufii.app.wg

data class WgTunnelInfo(
    val address: String,
    /** Host only; without it packets to the host loop back through the tunnel and drop. */
    val hairpinAddress: String? = null,
    val subnet: String,
    val relayEndpoint: String,
    val relayPublicKey: String,
    val relayAllowedIps: String
)

object WgConfig {

    /** Carrier NAT mappings expire well under a minute. */
    const val KEEPALIVE_SECONDS = 10

    /** Backend default is 1280; measured path passes 1252, loses 1300. */
    const val MTU = 1420

    const val RELAY_ADDRESS = "10.67.0.1"

    /** ARMSX2's keyboard has no dot key, so guests type a name, not an IP. */
    const val PS2_HOST_NAME = "emufii"

    fun render(
        info: WgTunnelInfo,
        privateKeyBase64: String,
        /** PS2 only: a VPN DNS takes over all device resolution. */
        dns: String? = null
    ): String = buildString {
        appendLine("[Interface]")
        appendLine("PrivateKey = $privateKeyBase64")
        val addresses = listOfNotNull(info.address, info.hairpinAddress)
        // The session prefix, not a /32: Eden reads the mask to hand to the game.
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

    fun renderRedacted(info: WgTunnelInfo, dns: String? = null): String =
        render(info, "<private key redacted>", dns)
}

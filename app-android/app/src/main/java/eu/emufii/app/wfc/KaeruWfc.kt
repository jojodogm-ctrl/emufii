package eu.emufii.app.wfc

/**
 * Where the DS's online play goes now that Nintendo's WFC is gone.
 *
 * Kaeru WFC is a third-party reimplementation reached by pointing the console's
 * DNS at it; it answers for the `nintendowifi.net` names the games ask for and
 * hands the Wii side over to Wiimmfi. Emufii never routes to Nintendo, see the
 * project invariants.
 *
 * Verified on 2026-07-26 by querying it directly:
 * - `nas.nintendowifi.net`      → 178.62.43.212 (itself)
 * - `conntest.nintendowifi.net` → 198.62.122.140
 * - `naswii.nintendowifi.net`   → 95.217.77.151 (Wiimmfi)
 *
 * It also answers for unrelated names (`example.com`, `github.com`), i.e. it is
 * a full recursive resolver. That is why the relay can forward *everything* to
 * it instead of splitting WFC names from the rest: melonDS's other lookups
 * (update checks, RetroAchievements) keep working.
 */
object KaeruWfc {

    /** Kaeru's resolver, as documented by the project and confirmed live. */
    const val DNS_SERVER = "178.62.43.212"

    /**
     * The address we advertise to Android as the DNS server, and the only route
     * we put in the tunnel.
     *
     * It is deliberately *not* Kaeru's real address: the tunnel answers these
     * queries itself and forwards them over a protected socket, so nothing
     * depends on Kaeru's address being routable through a tun. It also leaves
     * room to answer selectively later without re-plumbing anything.
     *
     * Picked inside 10.0.0.0/8 but away from the ranges the emulated networks
     * use (a session address is 10.67.x, melonDS's own slirp uses 10.0.2.x).
     */
    const val SENTINEL_DNS = "10.66.53.53"

    /** The tunnel's own address. A /32, we route one host, not a subnet. */
    const val TUN_ADDRESS = "10.66.53.2"

    const val DNS_PORT = 53

    /** Plenty for EDNS0; anything larger is not a DNS query we should relay. */
    const val MAX_DNS_MESSAGE = 4096
}

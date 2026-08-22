package eu.emufii.app.wfc

/**
 * Answers the DNS queries Android sends into the tunnel, by asking Kaeru.
 *
 * Why this exists at all: the DS's WFC settings live in melonDS's private
 * storage and its config screen is inside the emulated console, so Emufii cannot
 * set the console's DNS, not by writing a file, not by driving the UI. What it
 * *can* do is answer from the network side. melonDS ships with the console in
 * "auto-obtain DNS" mode, and in that mode resolution goes through Android's
 * resolver (measured: with auto DNS the console reached Nintendo's dead ELB, and
 * only a DNS pointed at Kaeru reached Kaeru). So a tunnel that advertises a DNS
 * server puts Emufii in the one place that decides where the console ends up, with
 * nothing for the player to configure.
 *
 * The logic is deliberately dumb, parse, forward, wrap the answer, and lives
 * apart from the service so it can be tested without an emulator.
 */
class DnsRelay(
    private val sentinelAddress: ByteArray,
    private val upstream: Upstream
) {

    /** Whatever actually talks to Kaeru. A socket in production, a fake in tests. */
    fun interface Upstream {
        /**
         * Sends a DNS message and returns the answer, or null if it timed out or
         * failed. Implementations must not throw.
         */
        fun exchange(query: ByteArray): ByteArray?
    }

    /** Counters, so the UI can show that something is actually happening. */
    @Volatile var queriesRelayed: Long = 0
        private set

    @Volatile var queriesDropped: Long = 0
        private set

    @Volatile var upstreamFailures: Long = 0
        private set

    /**
     * How many lookups in a row Kaeru has failed to answer.
     *
     * The total above cannot tell "one timeout on a busy evening" from "the
     * server is gone", and only the second is worth putting in front of a
     * player. A run is the honest signal: it resets on the first answer that
     * comes back, and packets that were never ours to answer do not touch it.
     */
    @Volatile var consecutiveUpstreamFailures: Int = 0
        private set

    /**
     * Takes one raw IP packet read from the tunnel and returns the raw packet to
     * write back, or null when there is nothing to answer.
     *
     * Null covers three different situations on purpose, not ours, malformed,
     * or upstream said nothing, because the caller does the same thing in all
     * three: nothing. Dropping is the right default for a tunnel that routes a
     * single host for a single purpose.
     */
    fun handle(packet: ByteArray, length: Int = packet.size): ByteArray? {
        val datagram = Ipv4Udp.parse(packet, length)
        if (datagram == null) {
            queriesDropped++
            return null
        }

        val addressedToUs = datagram.destination.contentEquals(sentinelAddress) &&
            datagram.destinationPort == KaeruWfc.DNS_PORT
        if (!addressedToUs) {
            queriesDropped++
            return null
        }

        // A DNS message has a 12-byte header; anything shorter cannot be a query,
        // and anything past the EDNS ceiling is not one we should be relaying.
        if (datagram.payload.size < 12 || datagram.payload.size > KaeruWfc.MAX_DNS_MESSAGE) {
            queriesDropped++
            return null
        }

        val answer = upstream.exchange(datagram.payload)
        if (answer == null || answer.size < 12) {
            upstreamFailures++
            consecutiveUpstreamFailures++
            return null
        }

        queriesRelayed++
        consecutiveUpstreamFailures = 0

        // Straight back where it came from, with the endpoints swapped.
        return Ipv4Udp.build(
            source = datagram.destination,
            destination = datagram.source,
            sourcePort = KaeruWfc.DNS_PORT,
            destinationPort = datagram.sourcePort,
            payload = answer,
            // The reply's transaction id is inside the DNS payload; the IP id is
            // free to reuse the query's low bits and does not need to be unique
            // for a packet that is never fragmented.
            identification = Ipv4Udp.readShort(answer, 0)
        )
    }
}

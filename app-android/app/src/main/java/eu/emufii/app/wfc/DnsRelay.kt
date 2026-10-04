package eu.emufii.app.wfc

/** melonDS uses Android's resolver in auto-DNS mode, so the tunnel's DNS server decides where the DS lands. */
class DnsRelay(
    private val sentinelAddress: ByteArray,
    private val upstream: Upstream
) {

    fun interface Upstream {
        fun exchange(query: ByteArray): ByteArray?
    }

    @Volatile var queriesRelayed: Long = 0
        private set

    @Volatile var queriesDropped: Long = 0
        private set

    @Volatile var upstreamFailures: Long = 0
        private set

    @Volatile var consecutiveUpstreamFailures: Int = 0
        private set

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

        return Ipv4Udp.build(
            source = datagram.destination,
            destination = datagram.source,
            sourcePort = KaeruWfc.DNS_PORT,
            destinationPort = datagram.sourcePort,
            payload = answer,
            identification = Ipv4Udp.readShort(answer, 0)
        )
    }
}

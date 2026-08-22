package eu.emufii.app.wfc

/**
 * Just enough IPv4/UDP to read a datagram off a tun device and write one back.
 *
 * A `VpnService` tunnel hands over raw IP packets, so the DNS relay has to do
 * its own parsing and its own checksums. Kept free of Android types on purpose:
 * every rule here is covered by JVM tests, the same way `TapserverHub`'s framing
 * is.
 *
 * Only what the relay needs is implemented, no fragmentation, no IPv6. A
 * fragmented DNS query over a tunnel we control does not happen, and a packet
 * we do not understand is dropped rather than guessed at.
 */
object Ipv4Udp {

    const val PROTO_UDP = 17
    private const val MIN_IP_HEADER = 20
    private const val UDP_HEADER = 8

    /** A parsed UDP datagram, addresses kept as raw 4-byte big-endian. */
    data class Datagram(
        val source: ByteArray,
        val destination: ByteArray,
        val sourcePort: Int,
        val destinationPort: Int,
        val payload: ByteArray
    ) {
        // Data class equality on ByteArray compares references, which makes for
        // confusing tests. Compare contents.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Datagram) return false
            return source.contentEquals(other.source) &&
                destination.contentEquals(other.destination) &&
                sourcePort == other.sourcePort &&
                destinationPort == other.destinationPort &&
                payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = source.contentHashCode()
            result = 31 * result + destination.contentHashCode()
            result = 31 * result + sourcePort
            result = 31 * result + destinationPort
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    /**
     * Reads the first [length] bytes of [packet] as an IPv4/UDP datagram, or
     * returns null if it is anything else, wrong version, not UDP, truncated,
     * fragmented, or claiming a length the buffer does not back up.
     */
    fun parse(packet: ByteArray, length: Int = packet.size): Datagram? {
        if (length < MIN_IP_HEADER) return null

        val versionAndIhl = packet[0].toInt() and 0xFF
        if ((versionAndIhl shr 4) != 4) return null

        val headerLength = (versionAndIhl and 0x0F) * 4
        if (headerLength < MIN_IP_HEADER || length < headerLength + UDP_HEADER) return null

        if ((packet[9].toInt() and 0xFF) != PROTO_UDP) return null

        // Fragmented? Bail: reassembly is not our job and a partial datagram
        // would parse as a plausible one.
        val fragmentField = readShort(packet, 6)
        if ((fragmentField and 0x1FFF) != 0 || (fragmentField and 0x2000) != 0) return null

        val totalLength = readShort(packet, 2)
        if (totalLength < headerLength + UDP_HEADER || totalLength > length) return null

        val udpLength = readShort(packet, headerLength + 4)
        if (udpLength < UDP_HEADER || headerLength + udpLength > totalLength) return null

        val payloadStart = headerLength + UDP_HEADER
        val payloadLength = udpLength - UDP_HEADER

        return Datagram(
            source = packet.copyOfRange(12, 16),
            destination = packet.copyOfRange(16, 20),
            sourcePort = readShort(packet, headerLength),
            destinationPort = readShort(packet, headerLength + 2),
            payload = packet.copyOfRange(payloadStart, payloadStart + payloadLength)
        )
    }

    /**
     * Builds a complete IPv4/UDP packet, with both checksums filled in.
     *
     * The UDP checksum is optional in IPv4, but computing it costs nothing here
     * and a wrong-but-present checksum is the kind of bug that shows up as
     * "DNS silently doesn't work", so it is computed and tested.
     */
    fun build(
        source: ByteArray,
        destination: ByteArray,
        sourcePort: Int,
        destinationPort: Int,
        payload: ByteArray,
        identification: Int = 0
    ): ByteArray {
        require(source.size == 4 && destination.size == 4) { "IPv4 addresses are 4 bytes" }

        val udpLength = UDP_HEADER + payload.size
        val totalLength = MIN_IP_HEADER + udpLength
        val packet = ByteArray(totalLength)

        packet[0] = 0x45                       // IPv4, 20-byte header
        packet[1] = 0                          // no DSCP/ECN
        writeShort(packet, 2, totalLength)
        writeShort(packet, 4, identification)
        writeShort(packet, 6, 0x4000)          // don't fragment
        packet[8] = 64                         // TTL
        packet[9] = PROTO_UDP.toByte()
        // 10..11 header checksum, filled below
        source.copyInto(packet, 12)
        destination.copyInto(packet, 16)
        writeShort(packet, 10, checksum(packet, 0, MIN_IP_HEADER))

        writeShort(packet, 20, sourcePort)
        writeShort(packet, 22, destinationPort)
        writeShort(packet, 24, udpLength)
        // 26..27 UDP checksum, filled below
        payload.copyInto(packet, 28)
        writeShort(packet, 26, udpChecksum(packet))

        return packet
    }

    /**
     * Standard 16-bit one's complement sum, returned already complemented.
     * A run over a header that already carries its checksum yields 0.
     */
    fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += readShort(data, i)
            i += 2
        }
        if (i < end) sum += (data[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    /**
     * UDP checksum over the pseudo-header plus the datagram, for a packet whose
     * checksum field is currently zero.
     */
    private fun udpChecksum(packet: ByteArray): Int {
        val udpLength = readShort(packet, 24)
        var sum = 0
        // Pseudo-header: source and destination addresses, zero, protocol, length.
        for (i in 12 until 20 step 2) sum += readShort(packet, i)
        sum += PROTO_UDP
        sum += udpLength
        // The datagram itself, checksum field included as the zero it currently is.
        var i = MIN_IP_HEADER
        val end = MIN_IP_HEADER + udpLength
        while (i + 1 < end) {
            sum += readShort(packet, i)
            i += 2
        }
        if (i < end) sum += (packet[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val result = sum.inv() and 0xFFFF
        // Zero means "no checksum" on the wire, so it is transmitted as all-ones.
        return if (result == 0) 0xFFFF else result
    }

    fun readShort(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun writeShort(data: ByteArray, offset: Int, value: Int) {
        data[offset] = ((value shr 8) and 0xFF).toByte()
        data[offset + 1] = (value and 0xFF).toByte()
    }
}

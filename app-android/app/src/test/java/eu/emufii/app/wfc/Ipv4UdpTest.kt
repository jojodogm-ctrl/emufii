package eu.emufii.app.wfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tunnel hands us raw packets and trusts whatever we write back, so these
 * tests are the only thing standing between a checksum slip and "DNS silently
 * doesn't work on device".
 */
class Ipv4UdpTest {

    private fun ip(a: Int, b: Int, c: Int, d: Int) =
        byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte())

    private val client = ip(10, 66, 53, 2)
    private val sentinel = ip(10, 66, 53, 53)

    @Test
    fun `builds a packet that parses back to what went in`() {
        val payload = byteArrayOf(0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0, 7)

        val packet = Ipv4Udp.build(client, sentinel, 41234, 53, payload)
        val parsed = Ipv4Udp.parse(packet)!!

        assertArrayEquals(client, parsed.source)
        assertArrayEquals(sentinel, parsed.destination)
        assertEquals(41234, parsed.sourcePort)
        assertEquals(53, parsed.destinationPort)
        assertArrayEquals(payload, parsed.payload)
    }

    @Test
    fun `header checksum verifies in place`() {
        val packet = Ipv4Udp.build(client, sentinel, 5353, 53, ByteArray(20) { it.toByte() })

        // Summing a header that already carries a correct checksum gives zero.
        assertEquals(0, Ipv4Udp.checksum(packet, 0, 20))
    }

    /**
     * Verifies a UDP checksum the way a receiver does: pseudo-header plus the
     * datagram with its checksum left in place, which sums to all-ones.
     */
    private fun udpChecksumVerifies(packet: ByteArray): Boolean {
        var sum = 0
        for (i in 12 until 20 step 2) sum += Ipv4Udp.readShort(packet, i)
        sum += Ipv4Udp.PROTO_UDP
        val udpLength = Ipv4Udp.readShort(packet, 24)
        sum += udpLength
        var i = 20
        while (i + 1 < 20 + udpLength) {
            sum += Ipv4Udp.readShort(packet, i)
            i += 2
        }
        if (i < 20 + udpLength) sum += (packet[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum and 0xFFFF) == 0xFFFF
    }

    // The three expected values below come from a separate implementation of the
    // one's-complement sum written in Python against the same inputs, not from
    // this code, a checksum that agrees with itself proves nothing. The first
    // value I worked out by hand was wrong, which is the point of doing this.
    @Test
    fun `header checksum matches an independently computed value`() {
        // 45 00 00 21 00 00 40 00 40 11 .. .. 0a 42 35 02 0a 42 35 35
        val packet = Ipv4Udp.build(client, sentinel, 0x1F90, 53, ByteArray(5))

        assertEquals(0xBC11, Ipv4Udp.readShort(packet, 10))
    }

    @Test
    fun `udp checksum verifies over the pseudo-header`() {
        val packet = Ipv4Udp.build(client, sentinel, 40000, 53, "kaeru".toByteArray())

        assertTrue(udpChecksumVerifies(packet))
    }

    @Test
    fun `udp checksum handles an odd-length payload`() {
        // The odd trailing byte is padded for the sum but must not change the
        // declared length, and the length is itself part of the sum, so the two
        // packets must NOT come out with the same checksum.
        val odd = Ipv4Udp.build(client, sentinel, 1234, 53, byteArrayOf(1, 2, 3))
        val even = Ipv4Udp.build(client, sentinel, 1234, 53, byteArrayOf(1, 2, 3, 0))

        assertEquals(0x7814, Ipv4Udp.readShort(odd, 26))
        assertEquals(0x7812, Ipv4Udp.readShort(even, 26))
        assertEquals(3 + 8, Ipv4Udp.readShort(odd, 24))
        assertTrue(udpChecksumVerifies(odd))
        assertTrue(udpChecksumVerifies(even))
    }

    @Test
    fun `parses a header carrying IP options`() {
        // IHL 6 -> 24-byte header. Build one by hand: the encoder only emits 20.
        val payload = byteArrayOf(9, 9, 9)
        val packet = ByteArray(24 + 8 + payload.size)
        packet[0] = 0x46
        packet[2] = 0
        packet[3] = packet.size.toByte()
        packet[6] = 0x40
        packet[9] = 17
        client.copyInto(packet, 12)
        sentinel.copyInto(packet, 16)
        // 20..23 are the options; left as zeroes on purpose.
        packet[24] = 0x30; packet[25] = 0x39  // source port 12345
        packet[26] = 0x00; packet[27] = 0x35  // destination port 53
        packet[28] = 0x00; packet[29] = (8 + payload.size).toByte()
        payload.copyInto(packet, 32)

        val parsed = Ipv4Udp.parse(packet)!!

        assertEquals(12345, parsed.sourcePort)
        assertEquals(53, parsed.destinationPort)
        assertArrayEquals(payload, parsed.payload)
    }

    @Test
    fun `rejects what it should not try to answer`() {
        val good = Ipv4Udp.build(client, sentinel, 40000, 53, ByteArray(16))

        // Truncated below an IP header.
        assertNull(Ipv4Udp.parse(good, 12))

        // Not IPv4.
        val v6 = good.copyOf().also { it[0] = 0x60 }
        assertNull(Ipv4Udp.parse(v6))

        // TCP, not UDP.
        val tcp = good.copyOf().also { it[9] = 6 }
        assertNull(Ipv4Udp.parse(tcp))

        // Claims more length than the buffer holds.
        assertNull(Ipv4Udp.parse(good, good.size - 4))

        // A fragment: reassembly is not our job.
        val fragment = good.copyOf().also { it[6] = 0x20 }  // more-fragments set
        assertNull(Ipv4Udp.parse(fragment))

        // A UDP length that does not fit inside the IP total length.
        val lying = good.copyOf().also { it[25] = (it[25] + 40).toByte() }
        assertNull(Ipv4Udp.parse(lying))

        // Sanity: the untouched packet is still accepted.
        assertTrue(Ipv4Udp.parse(good) != null)
    }

    @Test
    fun `rejects an impossible header length`() {
        val packet = Ipv4Udp.build(client, sentinel, 40000, 53, ByteArray(16))
        val ihlTooSmall = packet.copyOf().also { it[0] = 0x44 }  // IHL 4 -> 16 bytes

        assertNull(Ipv4Udp.parse(ihlTooSmall))
    }
}

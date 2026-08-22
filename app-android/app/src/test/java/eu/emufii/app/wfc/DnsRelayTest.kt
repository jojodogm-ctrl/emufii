package eu.emufii.app.wfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsRelayTest {

    private fun ip(a: Int, b: Int, c: Int, d: Int) =
        byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte())

    private val sentinel = ip(10, 66, 53, 53)
    private val client = ip(10, 66, 53, 2)
    private val elsewhere = ip(8, 8, 8, 8)

    /** A plausible query for nas.nintendowifi.net, header plus a question. */
    private val query = byteArrayOf(
        0x4D, 0x2A,             // transaction id
        0x01, 0x00,             // standard query, recursion desired
        0x00, 0x01,             // one question
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        3, 'n'.code.toByte(), 'a'.code.toByte(), 's'.code.toByte(),
        13, 'n'.code.toByte(), 'i'.code.toByte(), 'n'.code.toByte(), 't'.code.toByte(),
        'e'.code.toByte(), 'n'.code.toByte(), 'd'.code.toByte(), 'o'.code.toByte(),
        'w'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 'i'.code.toByte(),
        3, 'n'.code.toByte(), 'e'.code.toByte(), 't'.code.toByte(),
        0,
        0x00, 0x01, 0x00, 0x01  // A, IN
    )

    private val answer = query.copyOf() + byteArrayOf(178.toByte(), 62, 43, 212.toByte())

    private class RecordingUpstream(private val reply: ByteArray?) : DnsRelay.Upstream {
        val seen = mutableListOf<ByteArray>()
        override fun exchange(query: ByteArray): ByteArray? {
            seen += query
            return reply
        }
    }

    @Test
    fun `forwards the DNS payload untouched and wraps the answer back`() {
        val upstream = RecordingUpstream(answer)
        val relay = DnsRelay(sentinel, upstream)

        val request = Ipv4Udp.build(client, sentinel, 45678, 53, query)
        val reply = relay.handle(request)!!

        // Upstream saw exactly the DNS message, with no IP/UDP framing left on it.
        assertEquals(1, upstream.seen.size)
        assertArrayEquals(query, upstream.seen[0])

        // The reply comes back from the resolver we advertised, to the port that asked.
        val parsed = Ipv4Udp.parse(reply)!!
        assertArrayEquals(sentinel, parsed.source)
        assertArrayEquals(client, parsed.destination)
        assertEquals(53, parsed.sourcePort)
        assertEquals(45678, parsed.destinationPort)
        assertArrayEquals(answer, parsed.payload)
        assertEquals(1, relay.queriesRelayed)
    }

    @Test
    fun `ignores traffic not addressed to the resolver we advertised`() {
        val upstream = RecordingUpstream(answer)
        val relay = DnsRelay(sentinel, upstream)

        // Right port, wrong host: someone else's DNS server.
        assertNull(relay.handle(Ipv4Udp.build(client, elsewhere, 45678, 53, query)))
        // Right host, wrong port.
        assertNull(relay.handle(Ipv4Udp.build(client, sentinel, 45678, 5353, query)))

        assertTrue(upstream.seen.isEmpty())
        assertEquals(2, relay.queriesDropped)
        assertEquals(0, relay.queriesRelayed)
    }

    @Test
    fun `drops packets that are not parseable UDP`() {
        val relay = DnsRelay(sentinel, RecordingUpstream(answer))

        assertNull(relay.handle(ByteArray(8)))
        assertNull(relay.handle(Ipv4Udp.build(client, sentinel, 1, 53, query).also { it[9] = 6 }))

        assertEquals(2, relay.queriesDropped)
    }

    @Test
    fun `drops payloads that cannot be DNS`() {
        val upstream = RecordingUpstream(answer)
        val relay = DnsRelay(sentinel, upstream)

        // Shorter than a DNS header.
        assertNull(relay.handle(Ipv4Udp.build(client, sentinel, 45678, 53, ByteArray(11))))
        // Past the EDNS ceiling.
        val oversized = ByteArray(KaeruWfc.MAX_DNS_MESSAGE + 1)
        assertNull(relay.handle(Ipv4Udp.build(client, sentinel, 45678, 53, oversized)))

        assertTrue(upstream.seen.isEmpty())
        assertEquals(2, relay.queriesDropped)
    }

    @Test
    fun `stays quiet when upstream does not answer`() {
        val relay = DnsRelay(sentinel, RecordingUpstream(null))

        assertNull(relay.handle(Ipv4Udp.build(client, sentinel, 45678, 53, query)))

        assertEquals(1, relay.upstreamFailures)
        assertEquals(0, relay.queriesRelayed)
    }

    @Test
    fun `rejects a truncated answer rather than forwarding a stub`() {
        val relay = DnsRelay(sentinel, RecordingUpstream(ByteArray(4)))

        assertNull(relay.handle(Ipv4Udp.build(client, sentinel, 45678, 53, query)))

        assertEquals(1, relay.upstreamFailures)
    }

    @Test
    fun `only reads the bytes the tunnel actually delivered`() {
        val upstream = RecordingUpstream(answer)
        val relay = DnsRelay(sentinel, upstream)

        // A reused read buffer keeps stale bytes past the packet; if the relay
        // trusted the array length it would forward garbage.
        val request = Ipv4Udp.build(client, sentinel, 45678, 53, query)
        val buffer = request.copyOf(request.size + 64).also { buf ->
            for (i in request.size until buf.size) buf[i] = 0x7F
        }

        val reply = relay.handle(buffer, request.size)!!

        assertArrayEquals(query, upstream.seen[0])
        assertArrayEquals(answer, Ipv4Udp.parse(reply)!!.payload)
    }

    /** An upstream whose answers can be switched off and back on mid-test. */
    private class FlakyUpstream(var reply: ByteArray?) : DnsRelay.Upstream {
        override fun exchange(query: ByteArray): ByteArray? = reply
    }

    @Test
    fun `counts unanswered lookups as a run, and forgets it on the first answer`() {
        val upstream = FlakyUpstream(null)
        val relay = DnsRelay(sentinel, upstream)
        val request = Ipv4Udp.build(client, sentinel, 45678, 53, query)

        repeat(3) { relay.handle(request) }
        assertEquals(3, relay.consecutiveUpstreamFailures)

        upstream.reply = answer
        relay.handle(request)

        // The run is what says "Kaeru is gone"; one answer back means it isn't,
        // even though the lifetime total still remembers the outage.
        assertEquals(0, relay.consecutiveUpstreamFailures)
        assertEquals(3, relay.upstreamFailures)
    }

    @Test
    fun `packets that were never ours do not break the run`() {
        val relay = DnsRelay(sentinel, FlakyUpstream(null))
        val request = Ipv4Udp.build(client, sentinel, 45678, 53, query)

        relay.handle(request)
        // Addressed elsewhere, so it is dropped before upstream is ever asked.
        // If a drop reset the run, background chatter would mask a dead server.
        relay.handle(Ipv4Udp.build(client, elsewhere, 45678, 53, query))
        relay.handle(request)

        assertEquals(2, relay.consecutiveUpstreamFailures)
        assertEquals(1, relay.queriesDropped)
    }
}

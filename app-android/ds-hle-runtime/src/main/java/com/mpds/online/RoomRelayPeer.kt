package com.mpds.online

import java.security.SecureRandom

/** Both admitted roles of datagram-peer.mjs. Call only from one bounded carrier executor.
 * Callbacks must be nonblocking: onData commits bytes to an owned local queue,
 * never directly into a game-frame call that could wait for this same carrier.
 * Socket, gateway ticket and round cancellation remain the outer owner's duties.
 */
internal class RoomRelayPeer(
    private val config: RoomInvitation.Config,
    capability: ByteArray,
    private val stream: Boolean,
    private val sendPacket: (ByteArray) -> Boolean,
    private val onData: (ByteArray) -> Boolean,
    private val onStatus: (String) -> Unit,
    private val now: () -> Double = { System.nanoTime() / 1000000.0 },
    private val wall: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom()
) : AutoCloseable {
    private val context = RoomRelayCodec.context(config, !stream)
    private val cap = capability.copyOf()
    private val handshake = RoomRelayCodec.Handshake(cap, context)
    private var codec: RoomRelayCodec.Session? = null
    private val identity = ByteArray(32).also(random::nextBytes)
    private var peerId: ByteArray? = null
    private var handshakeChallenge = ByteArray(16)
    private data class Candidate(val challenge:ByteArray,val issuedAt:Double)
    private val candidates=linkedMapOf<String,Candidate>()
    private val journal = if (stream) RoomStreamJournal() else null
    private val gaps = RoomStreamJournal.GapRecovery()
    private val startedAt = now()
    private val deadline: Double
    private var started = false
    var closed = false; private set
    var authenticated = false; private set
    var ready = false; private set
    var reason: String? = null; private set
    val outgoingRecords: Int get() = journal?.outgoingCount ?: 0
    private var handshakeAt = 0.0
    private var proofAt: Double? = null
    private var registrationAt = Double.NEGATIVE_INFINITY
    private var helloAt = Double.NEGATIVE_INFINITY
    private var replyAt = Double.NEGATIVE_INFINITY
    private var statusAt = Double.NEGATIVE_INFINITY
    private var budgetAt = startedAt
    private var budget = 32
    private var tokensAt = startedAt
    private var tokens = 256.0
    private var challenge = ByteArray(8)
    private var echo = ByteArray(8)
    private var echoCounter = 0uL
    private val challenges = linkedMapOf<String, Double>()
    private var nextChallenge = 0.0
    private var sequence = 0uL
    private var ackDirty = false
    private var flushing = false
    private var receiving = false
    private var deliveryBlockedAt: Double? = null
    init {
        val remaining = config.expiresAtUnixMs - wall()
        require(remaining > 0 && remaining <= config.ttlSeconds * 1000L + 120000)
        deadline = startedAt + minOf(config.ttlSeconds * 1000L, remaining)
    }
    private fun event(value: String) {
        try { onStatus(value) } catch (_: Exception) { if (!closed) close("status-consumer-failed") }
    }
    private fun alive(): Boolean {
        if (closed) return false
        val t = now()
        if (t >= deadline || wall() >= config.expiresAtUnixMs) close("ttl-expired")
        else if (proofAt?.let { t - it >= 30000 } == true) close("outage-lease-expired")
        else if (started && config.role=="client" && !authenticated && t - handshakeAt >= 30000) close("handshake-timeout")
        else if (deliveryBlockedAt?.let { t - it >= 30000 } == true) close("local-write-timeout")
        return !closed
    }
    private fun packet(bytes: ByteArray): Boolean {
        if (!alive()) return false
        return try { sendPacket(bytes) } catch (_: Exception) { close("carrier-failed"); false }
    }
    fun start() {
        check(!started && !closed); started = true; handshakeAt = now(); tick()
    }
    private fun record(payload: ByteArray? = null, seq: ULong = 0u): Boolean {
        if (!alive() || codec == null) return false
        val t = now()
        if (t - budgetAt >= 20) { budgetAt = t; budget = 32 }
        if (budget <= 0) return false
        budget--
        return try {
            val sent = packet(codec!!.encode(RoomRelayCodec.Record(if (payload == null) 0 else 1,
                true, seq, journal?.received ?: 0u, challenge, echo, payload ?: byteArrayOf())))
            if (sent) { ackDirty = false; if (payload == null) statusAt = now() }
            sent
        } catch (_: Exception) { close("record-encode-failed"); false }
    }
    fun send(bytes: ByteArray): Boolean {
        require(bytes.size in 1..if (stream) 65536 else 1024)
        if (!started || !alive() || !authenticated || !ready) return false
        if (stream) {
            if (!journal!!.appendStream(bytes)) return false
            flush(); return true
        }
        if (sequence == ULong.MAX_VALUE) { close("sequence-exhausted"); return false }
        return record(bytes, ++sequence)
    }
    private fun flush() {
        if (flushing || !authenticated || !alive() || journal == null) return
        flushing = true
        try {
            for (pending in journal.pending()) {
                val fast = !pending.fastRetried && pending.fastRetryAt?.let { now() >= it } == true
                if (!fast && now() - pending.lastSent < 250) continue
                if (!record(pending.payload, pending.sequence)) return
                if (fast) pending.fastRetried = true
                pending.lastSent = now()
            }
            // Cumulative receipts: one STATUS per tick, not per received 1 KiB
            // fragment. A fast asset burst otherwise produces dozens of tiny
            // WebSocket messages which can arrive in one TCP read and exhaust
            // the gateway's bounded UDP message queue. DATA still piggybacks
            // the latest committed ACK immediately; tick flushes the final ACK.
            if ((ackDirty && now() - statusAt >= 20) || now() - statusAt >= 250) record()
        } finally { flushing = false }
    }
    private fun rotate() {
        challenge = ByteArray(8).also(random::nextBytes)
        val t = now(); challenges[RoomInvitation.hex(challenge)] = t
        val it = challenges.iterator()
        while (it.hasNext()) if (t - it.next().value >= 30000) it.remove()
        nextChallenge = t + 1000
    }
    private fun pump() {
        val j = journal ?: return
        while (alive()) {
            val delivery = j.nextDelivery() ?: break
            val accepted = onData(delivery.payload)
            if (!alive()) return
            // The consumer's bounded ring may be full. Keep this record pending,
            // do not acknowledge it, and retry on a later tick without blocking.
            if (!accepted) {
                if (deliveryBlockedAt == null) deliveryBlockedAt = now()
                return
            }
            deliveryBlockedAt = null
            j.commit(delivery.sequence); ackDirty = true
        }
    }
    fun tick() {
        if (!started || !alive()) return
        try {
            val t = now()
            val expired=candidates.iterator()
            while(expired.hasNext())if(t-expired.next().value.issuedAt>=2000)expired.remove()
            if (t - registrationAt >= 1000 && packet(RoomRelayCodec.registration(config))) registrationAt = t
            if (config.role=="client" && !authenticated && t - helloAt >= 250) {
                if (packet(handshake.encode(if (peerId == null) 1 else 3, identity,
                    peerId ?: ByteArray(32), handshakeChallenge))) helloAt = t
            }
            if (!authenticated || !alive()) return
            if (t >= nextChallenge) rotate()
            if (stream) { pump(); flush() } else if (t - statusAt >= 250) record()
        } catch (_: Exception) { close("tick-failed") }
    }
    fun receive(bytes: ByteArray) {
        if (!started || receiving || !alive()) return
        val t = now()
        tokens = minOf(256.0, tokens + maxOf(0.0, t - tokensAt) * 4.096); tokensAt = t
        if (bytes.size > RoomRelayCodec.MAX_PACKET || tokens < 1) return
        tokens--; receiving = true
        try {
            if (bytes.size == 120 && bytes.take(4).toByteArray().contentEquals("N2H1".toByteArray())) {
                val h = handshake.decode(bytes) ?: return
                if(config.role=="server") {serverHandshake(h,t);return}
                if (h.type != 2 || !h.clientId.contentEquals(identity)) return
                if (peerId != null && !h.serverId.contentEquals(peerId)) return
                if (peerId == null) {
                    peerId = h.serverId
                    codec = RoomRelayCodec.Session(cap, context, identity, h.serverId, "client")
                    cap.fill(0)
                }
                if (!authenticated) handshakeChallenge = h.challenge
                if (t - replyAt >= 100) {
                    replyAt = t; packet(handshake.encode(3, identity, peerId!!, handshakeChallenge))
                }
                return
            }
            val r = codec?.decode(bytes) ?: return
            if (!stream) require(r.ack == 0uL)
            if (journal != null) {
                journal.acknowledge(r.ack)
                if (r.type == 0) gaps.observe(r.counter, r.ack, journal)
                if (r.type == 1) {
                    require(r.ready || ready)
                    journal.stage(r.sequence, r.payload)
                    if (r.sequence <= journal.received || journal.nextDelivery() == null) ackDirty = true
                }
            }
            if (!authenticated) {
                if(config.role!="client")return
                authenticate()
            }
            if (!alive()) return
            challenges[RoomInvitation.hex(r.echo)]?.let { proofAt = maxOf(proofAt ?: it, it) }
            if (r.counter > echoCounter) { echoCounter = r.counter; echo = r.challenge }
            if (r.ready && !ready) { ready = true; event(if (stream) "stream-ready" else "datagram-ready") }
            if (!alive()) return
            if (journal != null) {
                pump()
                flush()
            } else {
                if (r.type == 1) { check(ready); onData(r.payload) }
                if (now() - statusAt >= 250) record()
            }
        } catch (_: Exception) { close("receive-failed") }
        finally { receiving = false }
    }
    private fun authenticate(){authenticated=true;proofAt=now();rotate();event("authenticated")}
    private fun challengePeer(client:ByteArray,t:Double) {
        if(t-replyAt<100)return
        val key=RoomInvitation.hex(client)
        var candidate=candidates[key]
        if(candidate==null||t-candidate.issuedAt>=2000) {
            candidate=Candidate(ByteArray(16).also(random::nextBytes),t)
            candidates[key]=candidate
            if(candidates.size>8)candidates.remove(candidates.keys.first())
        }
        replyAt=t;packet(handshake.encode(2,client,identity,candidate.challenge))
    }
    private fun serverHandshake(h:RoomRelayCodec.Hello,t:Double) {
        if(h.type==2)return
        if(peerId!=null&&!h.clientId.contentEquals(peerId))return
        if(h.type==1){challengePeer(h.clientId,t);return}
        if(h.type!=3||!h.serverId.contentEquals(identity))return
        if(peerId==null) {
            val candidate=candidates[RoomInvitation.hex(h.clientId)]
            if(candidate==null||t-candidate.issuedAt>=2000||!candidate.challenge.contentEquals(h.challenge)) {
                challengePeer(h.clientId,t);return
            }
            peerId=h.clientId
            codec=RoomRelayCodec.Session(cap,context,h.clientId,identity,"server");cap.fill(0)
            candidates.clear();authenticate()
        }
        if(t-replyAt>=100){replyAt=t;record()}
    }
    fun close(why: String) {
        if (closed) return
        closed = true; reason = why; ready = false
        handshake.close(); codec?.close(); cap.fill(0); journal?.clear(); challenges.clear();candidates.clear()
        event("closed")
    }
    override fun close() = close("stopped")
}

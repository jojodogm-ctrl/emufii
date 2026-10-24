package com.mpds.online

import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Byte-compatible with tools/nifi2-web/protocol.mjs. No sockets or game policy.
 * The carrier still owns expiry, gateway admission, handshake and delivery state.
 * Synchronized methods reserve each GCM nonce exactly once and retire atomically.
 */
internal object RoomRelayCodec {
    const val MAX_PACKET = 1106
    private val maxCounter = 0xffffffffuL
    private val replayMask = BigInteger.ONE.shiftLeft(4096).subtract(BigInteger.ONE)

    private fun routing(c: RoomInvitation.Config) {
        require(Regex("[A-Za-z0-9_-]{1,15}").matches(c.room) && c.seat in 0..7)
    }
    fun context(c: RoomInvitation.Config, datagrams: Boolean = false): ByteArray {
        routing(c)
        require(Regex("[0-9a-f]{32}").matches(c.invitationId) && c.ttlSeconds in 1..28800 &&
            c.expiresAtUnixMs in 1..9007199254740991L)
        return RoomInvitation.json(listOf("nifi2-relay-v1", c.room, c.seat, c.invitationId,
            c.ttlSeconds, c.expiresAtUnixMs)).toByteArray(Charsets.UTF_8) +
            (if (datagrams) "\u0000nifi2-datagrams/v2".toByteArray(Charsets.UTF_8) else byteArrayOf())
    }
    fun registration(c: RoomInvitation.Config): ByteArray {
        routing(c)
        return ByteArray(22).apply {
            "MRJN".toByteArray(Charsets.US_ASCII).copyInto(this)
            require(c.role in listOf("client","server"))
            this[5] = c.seat.toByte()
            c.room.toByteArray(Charsets.US_ASCII).copyInto(this, 6)
        }
    }
    private fun digest(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)
    private fun hmac(key: ByteArray, b: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256")); doFinal(b)
    }
    private fun derive(key: ByteArray, salt: ByteArray, label: String): ByteArray {
        val prk = hmac(salt, key)
        return try { hmac(prk, label.toByteArray(Charsets.UTF_8) + byteArrayOf(1)) }
        finally { prk.fill(0) }
    }
    private fun material(cap: ByteArray, ctx: ByteArray) {
        require(cap.size == 32 && cap.any { it != 0.toByte() } && ctx.size in 1..4096)
    }
    private fun magic(b: ByteArray, s: String) = b.size >= s.length &&
        s.indices.all { b[it] == s[it].code.toByte() }

    data class Hello(val type: Int, val clientId: ByteArray, val serverId: ByteArray, val challenge: ByteArray)
    class Handshake(capability: ByteArray, context: ByteArray) : AutoCloseable {
        private val client: ByteArray
        private val server: ByteArray
        private var closed = false
        init {
            material(capability, context)
            val cap = capability.copyOf(); val salt = digest(context.copyOf())
            try {
                client = derive(cap, salt, "nifi2/handshake/client/v1")
                server = derive(cap, salt, "nifi2/handshake/server/v1")
            } finally { cap.fill(0); salt.fill(0) }
        }
        @Synchronized fun encode(type: Int, clientId: ByteArray, serverId: ByteArray = ByteArray(32),
                                 challenge: ByteArray = ByteArray(16)): ByteArray {
            check(!closed) { "codec-closed" }
            require(type in 1..3 && clientId.size == 32 && serverId.size == 32 && challenge.size == 16)
            require(type != 1 || serverId.all { it == 0.toByte() } && challenge.all { it == 0.toByte() })
            val bytes = ByteArray(120)
            "N2H1".toByteArray(Charsets.US_ASCII).copyInto(bytes); bytes[4] = type.toByte()
            clientId.copyInto(bytes, 8); serverId.copyInto(bytes, 40); challenge.copyInto(bytes, 72)
            val signature = hmac(if (type == 2) server else client, bytes.copyOfRange(0, 88))
            signature.copyInto(bytes, 88); signature.fill(0)
            return bytes
        }
        @Synchronized fun decode(bytes: ByteArray): Hello? {
            if (closed || bytes.size != 120 || !magic(bytes, "N2H1")) return null
            val b = bytes.copyOf(); val type = b[4].toInt()
            if (type !in 1..3 || b[5] != 0.toByte() || b[6] != 0.toByte() || b[7] != 0.toByte()) return null
            val expected = hmac(if (type == 2) server else client, b.copyOfRange(0, 88))
            val valid = try { MessageDigest.isEqual(expected, b.copyOfRange(88, 120)) }
                finally { expected.fill(0) }
            if (!valid || type == 1 && b.sliceArray(40 until 88).any { it != 0.toByte() }) return null
            return Hello(type, b.copyOfRange(8, 40), b.copyOfRange(40, 72), b.copyOfRange(72, 88))
        }
        @Synchronized override fun close() { closed = true; client.fill(0); server.fill(0) }
    }

    data class Record(val type: Int = 0, val ready: Boolean = false, val sequence: ULong = 0u,
                      val ack: ULong = 0u, val challenge: ByteArray, val echo: ByteArray,
                      val payload: ByteArray = byteArrayOf(), val counter: ULong = 0u)
    class Session(capability: ByteArray, context: ByteArray, clientId: ByteArray,
                  serverId: ByteArray, role: String) : AutoCloseable {
        private val id: ByteArray
        private val tx: ByteArray
        private val rx: ByteArray
        private var closed = false
        private var counter = 0uL
        private var highest = 0uL
        private var bits = BigInteger.ZERO
        val sessionId: ByteArray get() = id.copyOf()
        init {
            material(capability, context)
            require(clientId.size == 32 && serverId.size == 32 && role in listOf("client", "server"))
            val cap = capability.copyOf()
            val salt = digest(context.copyOf() + clientId.copyOf() + serverId.copyOf())
            try {
                id = digest("nifi2/session/v1".toByteArray(Charsets.UTF_8) + salt).copyOf(16)
                val c2s = derive(cap, salt, "nifi2/aes-256-gcm/client-to-server/v1")
                val s2c = derive(cap, salt, "nifi2/aes-256-gcm/server-to-client/v1")
                tx = if (role == "client") c2s else s2c
                rx = if (role == "client") s2c else c2s
            } finally { cap.fill(0); salt.fill(0) }
        }
        private fun eligible(n: ULong): Boolean {
            if (n == 0uL || n > maxCounter) return false
            if (n > highest) return true
            val age = highest - n
            return age < 4096u && !bits.testBit(age.toInt())
        }
        private fun commit(n: ULong) {
            if (n > highest) {
                val jump = n - highest
                bits = if (jump >= 4096u) BigInteger.ONE else bits.shiftLeft(jump.toInt()).or(BigInteger.ONE).and(replayMask)
                highest = n
            } else bits = bits.setBit((highest - n).toInt())
        }
        private fun cipher(mode: Int, key: ByteArray, header: ByteArray): Cipher {
            val iv = ByteArray(12); header.copyInto(iv, 4, 20, 28)
            return Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
                updateAAD(header)
            }
        }
        @Synchronized fun encode(record: Record): ByteArray {
            check(!closed) { "codec-closed" }; check(counter < maxCounter) { "packet-counter-exhausted" }
            require(record.type in 0..1 && record.challenge.size == 8 && record.echo.size == 8 && record.payload.size <= 1024)
            require(if (record.type == 0) record.sequence == 0uL && record.payload.isEmpty()
                else record.sequence > 0uL && record.payload.isNotEmpty())
            val header = ByteBuffer.allocate(28).put("N2D1".toByteArray(Charsets.US_ASCII)).put(id).putLong((++counter).toLong()).array()
            val plain = ByteBuffer.allocate(38 + record.payload.size).put(record.type.toByte())
                .put(if (record.ready) 1.toByte() else 0.toByte()).putShort(0)
                .putLong(record.sequence.toLong()).putLong(record.ack.toLong()).putShort(record.payload.size.toShort())
                .put(record.challenge).put(record.echo).put(record.payload).array()
            return try { header + cipher(Cipher.ENCRYPT_MODE, tx, header).doFinal(plain) }
            finally { plain.fill(0) }
        }
        @Synchronized fun decode(bytes: ByteArray): Record? {
            if (closed || bytes.size !in 82..MAX_PACKET || !magic(bytes, "N2D1")) return null
            val b = bytes.copyOf()
            if (!MessageDigest.isEqual(id, b.copyOfRange(4, 20))) return null
            val n = ByteBuffer.wrap(b, 20, 8).long.toULong()
            if (!eligible(n)) return null
            val plain = try { cipher(Cipher.DECRYPT_MODE, rx, b.copyOfRange(0, 28)).doFinal(b, 28, b.size - 28) }
                catch (_: java.security.GeneralSecurityException) { return null }
            try {
                if (plain.size < 38) return null
                val p = ByteBuffer.wrap(plain); val type = p.get().toInt(); val ready = p.get().toInt()
                if (type !in 0..1 || ready !in 0..1 || p.short != 0.toShort()) return null
                val seq = p.long.toULong(); val ack = p.long.toULong(); val length = p.short.toInt() and 65535
                if (length > 1024 || plain.size != 38 + length ||
                    (if (type == 0) seq != 0uL || length != 0 else seq == 0uL || length == 0)) return null
                val result = Record(type, ready == 1, seq, ack, plain.copyOfRange(22, 30),
                    plain.copyOfRange(30, 38), plain.copyOfRange(38, plain.size), n)
                commit(n); return result
            } finally { plain.fill(0) }
        }
        @Synchronized override fun close() { closed = true; tx.fill(0); rx.fill(0); bits = BigInteger.ZERO }
    }
}

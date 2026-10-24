package com.mpds.online

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.withLock

/** Ordered byte-carrier adapter only; no transport, files, timers or Android dependencies.
 * push is nonblocking, copies on acceptance, and returns false without consuming anything
 * on full/contended/closed rings. Owner must retry rejected input and split input to fit
 * bufferBytes. There is no pending input queue. read/frame share one reader; write/send
 * share one writer. Concurrent operations in either direction reject, never queue.
 *
 * send MUST complete within a bounded owner-selected wait, neither mutate bytes nor
 * accept any bytes on false. A gateway future timeout MUST throw, not return false:
 * its eventual acceptance is unknown, so retrying it could duplicate stream bytes.
 * Accepted arrays belong to the carrier. Call writable() when admission may succeed;
 * retries use a condition with bounded backoff, with one pending write of at most PACKET_BYTES
 * plus one carrier part of at most CHUNK_BYTES. A notification racing refusal is retained.
 * No callback is invoked with the ring lock held. close wakes waits, but cannot preempt
 * an in-flight callback or retract already accepted carrier bytes. Callback wait budgets
 * must respect the owner's overall deadline; this adapter cannot enforce them internally.
 *
 * Deadlines are absolute System.nanoTime values, shared across a whole operation.
 * Idle expiry is checked on access and timed waits, without a background thread.
 * ensureCurrent is called outside the lock, on workers, before/after I/O and between
 * bounded waits; it must be fast and throw on stale ownership. Owner must also close
 * on carrier termination/invitation expiry. After partial I/O failure
 * the wire closes to prevent reuse of a desynchronized stream. Auth reserves both I/O
 * directions until mutual proof completes; application ordering remains owner policy.
 */
internal class RoomAssetWire(
    private val send: (ByteArray) -> Boolean,
    private val ensureCurrent: () -> Unit = {},
    private val limits: Limits = Limits(),
    private val random: SecureRandom = SecureRandom()
) : AutoCloseable {
    class AssetError(val code: String) : Exception(code)
    data class Auth(val reuse: Boolean, val diagnostics: Boolean)
    data class Limits(val bufferBytes: Int = PACKET_BYTES, val idleMs: Long = 30_000, val retryMs: Long = 25) {
        init {
            require(bufferBytes in 1..PACKET_BYTES)
            require(idleMs in 1..30_000)
            require(retryMs in 1..100)
        }
    }

    /** Local, trusted lobby policy, never a length/type taken from an incoming header. */
    data class TrustedFrame(val type: Int, val maxLength: Int) {
        init { require(type in 16..18 && maxLength in 1..CHUNK_BYTES) }
    }

    companion object {
        const val CHUNK_BYTES = 65536
        const val PACKET_BYTES = CHUNK_BYTES + 5
        const val CHALLENGE = 1
        const val PROOF = 2
        const val AUTHENTICATED = 3
        const val REQUEST = 4
        const val DATA = 5
        const val END = 6
        const val COMMITTED = 0x7f
        private val REUSE = "N2ARv001".toByteArray(Charsets.US_ASCII)
        private val DIAGNOSTICS = "N2DGv001".toByteArray(Charsets.US_ASCII)
        fun deadlineAfter(timeoutMs: Long = 30_000): Long {
            require(timeoutMs in 1..28_800_000)
            return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        }
        private fun assetLimit(type: Int): Int = when (type) {
            CHALLENGE, AUTHENTICATED -> 32
            PROOF -> 64
            REQUEST, END -> 1024
            DATA -> CHUNK_BYTES
            else -> throw AssetError("asset-frame")
        }
        private fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            parts.forEach { mac.update(it) }
            return mac.doFinal()
        }
        private fun tag(secret: ByteArray, role: String, server: ByteArray, client: ByteArray) =
            hmac(secret, "nifi2-assets/$role/v1\u0000".toByteArray(Charsets.UTF_8), server, client)
    }

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val ring = ByteArray(limits.bufferBytes)
    private var head = 0
    private var size = 0
    private var reading = false
    private var writing = false
    private var failure: AssetError? = null
    private var writableEpoch = 0L
    private var lastProgress = System.nanoTime()
    private val idleNanos = TimeUnit.MILLISECONDS.toNanos(limits.idleMs)
    private val retryNanos = TimeUnit.MILLISECONDS.toNanos(limits.retryMs)
    /** Accepted, unread ring bytes only. Rejected input is never retained. */
    val pendingBytes: Int get() = lock.withLock { size }

    private fun closeLocked(code: String) {
        if (failure == null) {
            failure = AssetError(code); ring.fill(0); size = 0
            changed.signalAll()
        }
    }
    override fun close() = close("asset-closed")
    fun close(code: String) = lock.withLock { closeLocked(code) }
    fun ensure() = active(null)
    private fun active(deadline: Long?) {
        lock.withLock { ensureLocked(deadline) }
        try { ensureCurrent() } catch (e: Exception) {
            if (e is InterruptedException) Thread.currentThread().interrupt()
            close("asset-cancelled")
            throw lock.withLock { failure!! }
        }
        lock.withLock { ensureLocked(deadline) }
    }
    private fun ensureLocked(deadline: Long?) {
        val now = System.nanoTime()
        if (deadline != null && deadline - now <= 0) closeLocked("asset-timeout")
        if (now - lastProgress >= idleNanos) closeLocked("asset-idle-timeout")
        failure?.let { throw it }
    }
    private fun progress() { lastProgress = System.nanoTime() }
    private fun awaitChange(deadline: Long) {
        ensureLocked(deadline)
        changed.awaitNanos(minOf(retryNanos, deadline - System.nanoTime(), idleNanos - (System.nanoTime() - lastProgress)))
        ensureLocked(deadline)
    }

    fun push(bytes: ByteArray): Boolean {
        require(bytes.size in 1..PACKET_BYTES)
        if (!lock.tryLock()) return false
        try {
            if (System.nanoTime() - lastProgress >= idleNanos) closeLocked("asset-idle-timeout")
            if (failure != null || bytes.size > ring.size - size) return false
            val tail = (head + size) % ring.size
            val first = minOf(bytes.size, ring.size - tail)
            bytes.copyInto(ring, tail, 0, first)
            bytes.copyInto(ring, 0, first, bytes.size)
            size += bytes.size; progress(); changed.signalAll()
            return true
        } finally { lock.unlock() }
    }

    /** Notify after carrier capacity changes, including when a send just returned false. */
    fun writable() = lock.withLock { writableEpoch++; changed.signalAll() }

    private fun <T> operation(read: Boolean, write: Boolean, deadline: Long, body: () -> T): T {
        active(deadline)
        lock.withLock {
            ensureLocked(deadline)
            if (read && reading) throw AssetError("asset-concurrent-read")
            if (write && writing) throw AssetError("asset-concurrent-write")
            if (read) reading = true
            if (write) writing = true
        }
        try {
            val result = body()
            active(deadline)
            return result
        } catch (e: Exception) {
            if (e is InterruptedException) Thread.currentThread().interrupt()
            close(if (e is AssetError) e.code else if (e is InterruptedException) "asset-cancelled" else "asset-connection")
            throw lock.withLock { failure!! }
        } finally {
            lock.withLock { if (read) reading = false; if (write) writing = false }
        }
    }

    private fun readExact(length: Int, deadline: Long): ByteArray {
        val result = ByteArray(length)
        var offset = 0
        while (offset < length) {
            active(deadline)
            lock.withLock {
                ensureLocked(deadline)
                if (size == 0) awaitChange(deadline)
                else {
                    val count = minOf(length - offset, size, ring.size - head)
                    ring.copyInto(result, offset, head, head + count)
                    ring.fill(0, head, head + count)
                    head = (head + count) % ring.size; size -= count; offset += count
                    progress()
                }
            }
        }
        return result
    }
    fun read(length: Int, deadlineNanos: Long = deadlineAfter()): ByteArray {
        require(length in 1..CHUNK_BYTES)
        return operation(true, false, deadlineNanos) { readExact(length, deadlineNanos) }
    }
    private fun readFrame(type: Int, maximum: Int, deadline: Long): ByteArray {
        val header = readExact(5, deadline)
        var length = 0L
        for (i in 1..4) length = (length shl 8) or (header[i].toLong() and 255)
        if ((header[0].toInt() and 255) != type || length !in 1L..maximum.toLong()) throw AssetError("asset-frame")
        return readExact(length.toInt(), deadline)
    }
    fun frame(expected: Int, maxLength: Int = assetLimit(expected), deadlineNanos: Long = deadlineAfter()): ByteArray {
        require(maxLength in 1..assetLimit(expected))
        return operation(true, false, deadlineNanos) { readFrame(expected, maxLength, deadlineNanos) }
    }
    fun frame(limit: TrustedFrame, deadlineNanos: Long = deadlineAfter()): ByteArray =
        operation(true, false, deadlineNanos) { readFrame(limit.type, limit.maxLength, deadlineNanos) }

    private fun writeOwned(bytes: ByteArray, deadline: Long) {
        var offset = 0
        while (offset < bytes.size) {
            val part = bytes.copyOfRange(offset, minOf(offset + CHUNK_BYTES, bytes.size))
            while (true) {
                active(deadline)
                val epoch = lock.withLock { ensureLocked(deadline); writableEpoch }
                val accepted = send(part)
                active(deadline)
                lock.withLock {
                    ensureLocked(deadline)
                    if (accepted) progress()
                    else if (writableEpoch == epoch) awaitChange(deadline)
                }
                if (accepted) break
            }
            offset += part.size
        }
    }
    fun write(bytes: ByteArray, deadlineNanos: Long = deadlineAfter()) {
        require(bytes.size in 1..PACKET_BYTES)
        operation(false, true, deadlineNanos) { writeOwned(bytes.copyOf(), deadlineNanos) }
    }
    private fun sendFrame(type: Int, bytes: ByteArray, deadline: Long) {
        val packet = ByteArray(bytes.size + 5)
        packet[0] = type.toByte()
        for (i in 1..4) packet[i] = (bytes.size ushr ((4 - i) * 8)).toByte()
        bytes.copyInto(packet, 5)
        writeOwned(packet, deadline)
    }
    fun send(type: Int, bytes: ByteArray, deadlineNanos: Long = deadlineAfter()) {
        require(bytes.size in 1..assetLimit(type))
        operation(false, true, deadlineNanos) { sendFrame(type, bytes, deadlineNanos) }
    }
    fun send(limit: TrustedFrame, bytes: ByteArray, deadlineNanos: Long = deadlineAfter()) {
        require(bytes.size in 1..limit.maxLength)
        operation(false, true, deadlineNanos) { sendFrame(limit.type, bytes, deadlineNanos) }
    }

    /** Guest proves possession, then verifies host proof. Flags return ONLY after verification.
     * Key is borrowed, copied and never erased in caller storage. SecureRandom injection is
     * for deterministic tests only; production must retain the default secure generator.
     */
    fun authenticateReceiver(key: ByteArray, diagnostics: Boolean = false,
                             deadlineNanos: Long = deadlineAfter()): Auth =
        operation(true, true, deadlineNanos) {
            if (key.size != 32 || key.all { it == 0.toByte() }) throw AssetError("asset-key")
            val owned = key.copyOf()
            var secret: ByteArray? = null
            try {
                // RFC 5869: empty salt is HashLen zeros; a single expand block suffices.
                val prk = try { hmac(ByteArray(32), owned) } finally { owned.fill(0) }
                val derived = try { hmac(prk, "nifi2-assets/local-proof/v1".toByteArray(Charsets.UTF_8), byteArrayOf(1)) }
                    finally { prk.fill(0) }
                secret = derived
                val client = ByteArray(32).also { random.nextBytes(it) }
                val server = readFrame(CHALLENGE, 32, deadlineNanos)
                if (server.size != 32) throw AssetError("asset-authentication")
                val negotiated = diagnostics && server.copyOfRange(0, 8).contentEquals(DIAGNOSTICS)
                if (negotiated) DIAGNOSTICS.copyInto(client)
                sendFrame(PROOF, client + tag(derived, "client", server, client), deadlineNanos)
                val confirmation = readFrame(AUTHENTICATED, 32, deadlineNanos)
                val expected = tag(derived, "server", server, client)
                val valid = try { MessageDigest.isEqual(expected, confirmation) } finally { expected.fill(0) }
                if (!valid) throw AssetError("asset-authentication")
                Auth(server.copyOfRange(0, 8).contentEquals(REUSE), negotiated)
            } finally { owned.fill(0); secret?.fill(0) }
        }
    /** Host proof follows exactly asset-wire.mjs; no game protocol is synthesized. */
    fun authenticateSender(key:ByteArray, reuse:Boolean=true, diagnostics:Boolean=false,
        deadlineNanos:Long=deadlineAfter()):Auth = operation(true,true,deadlineNanos) {
        if(key.size!=32 || key.all {it==0.toByte()})throw AssetError("asset-key")
        val owned=key.copyOf()
        val prk=try { hmac(ByteArray(32),owned) } finally {owned.fill(0)}
        val secret=try {hmac(prk,"nifi2-assets/local-proof/v1".toByteArray(Charsets.UTF_8),byteArrayOf(1))}
            finally {prk.fill(0)}
        try {
            val server=ByteArray(32).also {random.nextBytes(it)}
            if(diagnostics)DIAGNOSTICS.copyInto(server) else if(reuse)REUSE.copyInto(server)
            sendFrame(CHALLENGE,server,deadlineNanos)
            val proof=readFrame(PROOF,64,deadlineNanos)
            if(proof.size!=64)throw AssetError("asset-authentication")
            val client=proof.copyOfRange(0,32)
            val expected=tag(secret,"client",server,client)
            val valid=try {MessageDigest.isEqual(expected,proof.copyOfRange(32,64))}
                finally {expected.fill(0);proof.fill(0)}
            if(!valid)throw AssetError("asset-authentication")
            sendFrame(AUTHENTICATED,tag(secret,"server",server,client),deadlineNanos)
            Auth(reuse&&!diagnostics,diagnostics&&client.copyOfRange(0,8).contentEquals(DIAGNOSTICS))
        } finally {secret.fill(0)}
    }
}

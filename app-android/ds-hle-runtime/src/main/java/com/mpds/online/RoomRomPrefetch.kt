package com.mpds.online

import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** ROM-only preparation owner. The final signed manifest remains authoritative. */
internal interface RoomPreparation : Closeable {
    val offer: RoomDirectory.RomPrefetchOffer
    val expired: Boolean
    fun matches(next: RoomDirectory.Snapshot): Boolean
    fun sameOffer(next: RoomDirectory.RomPrefetchOffer): Boolean
    fun acceptsManifest(admission: RoomInvitation.Admission): Boolean
    fun localRom(admission: RoomInvitation.Admission, checkRound: () -> Unit): File?
}

internal class RoomRomPrefetch(
    override val offer: RoomDirectory.RomPrefetchOffer,
    val snapshot: RoomDirectory.Snapshot,
    root: File,
    ticket: RoomDirectory.GatewayTicket
) : RoomPreparation {
    private val cancelled = AtomicBoolean(false)
    private val directory = Files.createTempDirectory(root.toPath(), "room-prefetch-").toFile()
    private val file = File(directory, "rom.bin")
    private val result = CompletableFuture<File?>()
    private val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(offer.expiresAtUnixMs - System.currentTimeMillis())
    @Volatile private var channel: RoomGatewayChannel? = null
    @Volatile private var wire: RoomAssetWire? = null
    private fun current() = !cancelled.get() && System.currentTimeMillis() < offer.expiresAtUnixMs && System.nanoTime() < deadline
    override val expired: Boolean get() = System.currentTimeMillis() >= offer.expiresAtUnixMs || System.nanoTime() >= deadline
    private fun ensure() { check(current()) { "prefetch-retired" } }

    init { Thread({ receive(ticket) }, "wantap-prefetch").apply { isDaemon = true; start() } }

    override fun matches(next: RoomDirectory.Snapshot) = matches(snapshot, offer, next)

    override fun sameOffer(next: RoomDirectory.RomPrefetchOffer) = canonical(offer) == canonical(next)

    override fun acceptsManifest(admission: RoomInvitation.Admission): Boolean =
        RoomAssetManifest.entries(admission).any {
            it.kind == "rom" && it.bytes == offer.bytes && it.sha256 == offer.sha256
        }

    /** Only offered to the normal receiver after authenticating the final invitation. */
    override fun localRom(admission: RoomInvitation.Admission, checkRound: () -> Unit): File? {
        if (!acceptsManifest(admission)) { close(); return null }
        while (!cancelled.get()) {
            checkRound()
            if (expired) { close(); return null }
            try { return result.get(100, TimeUnit.MILLISECONDS) }
            catch (_: TimeoutException) { if (!current()) { close(); return null } }
        }
        return null
    }

    private fun receive(ticket: RoomDirectory.GatewayTicket) {
        var success = false
        val key = proofKey(offer)
        try {
            ensure()
            val active = RoomAssetWire(send = { bytes ->
                ensure(); channel?.send(bytes)?.get(10, TimeUnit.SECONDS) == true
            }, ensureCurrent = ::ensure)
            wire = active
            channel = RoomGatewayChannel.openPreparation(offer, ticket, ::current, active::push,
                { active.close("prefetch-carrier-closed") })
            channel!!.ready.get(30, TimeUnit.SECONDS)
            active.authenticateReceiver(key)
            receivePayload(offer, active, file, ::ensure)
            ensure(); check(active.pendingBytes == 0) { "prefetch-trailing-data" }
            active.write(byteArrayOf(RoomAssetWire.COMMITTED.toByte()))
            val receiptDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (!channel!!.drained().get(10, TimeUnit.SECONDS)) {
                ensure(); check(System.nanoTime() < receiptDeadline); Thread.sleep(20)
            }
            ensure(); check(active.pendingBytes == 0)
            success = true
        } catch (_: Exception) { /* Optional transfer falls back to signed normal transfer. */ }
        finally {
            key.fill(0); wire?.close(); channel?.close()
            synchronized(result) {
                if (!success || cancelled.get()) { try { cleanup() } finally { result.complete(null) } }
                else result.complete(file)
            }
        }
    }

    override fun close() {
        cancelled.set(true); wire?.close(); channel?.close()
        synchronized(result) { if (result.isDone) cleanup() }
    }
    private fun cleanup() {
        // Cleanup failure is isolated from gameplay. Subsequent close retries it.
        runCatching { Files.deleteIfExists(file.toPath()); Files.deleteIfExists(directory.toPath()) }
    }
    companion object {
        internal fun matches(snapshot: RoomDirectory.Snapshot, offer: RoomDirectory.RomPrefetchOffer,
                             next: RoomDirectory.Snapshot): Boolean =
            next.code == snapshot.code && next.seat == snapshot.seat && next.settings == snapshot.settings &&
                next.roster == snapshot.roster && next.generation ==
                (if (next.phase == RoomDirectory.Phase.WAITING) snapshot.generation else offer.generation)
        internal fun metadata(o: RoomDirectory.RomPrefetchOffer) =
            RoomAssetManifest.Entry("rom", o.bytes, o.sha256).metadata()

        /** Narrow ROM codec, called only after the offer-bound mutual proof. */
        internal fun receivePayload(o: RoomDirectory.RomPrefetchOffer, wire: RoomAssetWire,
                                    file: File, ensure: () -> Unit) {
            val meta = metadata(o)
            ensure(); wire.send(RoomAssetWire.REQUEST, meta)
            val hash = MessageDigest.getInstance("SHA-256")
            file.outputStream().use { output ->
                var received = 0L
                while (received < o.bytes) {
                    ensure()
                    val chunk = wire.frame(RoomAssetWire.DATA)
                    try {
                        check(chunk.size.toLong() == minOf(65536L, o.bytes - received)) { "prefetch-size" }
                        output.write(chunk); hash.update(chunk); received += chunk.size
                    } finally { chunk.fill(0) }
                }
            }
            check(wire.frame(RoomAssetWire.END).contentEquals(meta)) { "prefetch-metadata" }
            check(RoomInvitation.hex(hash.digest()) == o.sha256) { "prefetch-hash" }
            ensure(); check(wire.pendingBytes == 0) { "prefetch-trailing-data" }
        }

        internal fun canonical(o: RoomDirectory.RomPrefetchOffer) = RoomInvitation.json(listOf(
            1, o.attempt, o.generation, o.bytes, o.sha256, o.expiresAtUnixMs, o.seat,
            o.room, o.invitationId, o.capability))
        internal fun proofKey(o: RoomDirectory.RomPrefetchOffer): ByteArray {
            fun hmac(key: ByteArray, bytes: ByteArray) = Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(key, "HmacSHA256")); doFinal(bytes)
            }
            val cap = ByteArray(32) { o.capability.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            val prk = try { hmac(ByteArray(32), cap) } finally { cap.fill(0) }
            val info = ("nifi2-rom-prefetch/local-proof/v1\u0000" + canonical(o)).toByteArray(Charsets.UTF_8)
            return try { hmac(prk, info + byteArrayOf(1)) } finally { prk.fill(0); info.fill(0) }
        }
    }
}

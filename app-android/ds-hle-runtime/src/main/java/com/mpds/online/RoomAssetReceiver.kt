package com.mpds.online

import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Downloads into private, per-attempt files, never into a supplied player save.
 * Hashes and exact lengths are checked before COMMITTED and before publication.
 * Call on the loading worker; the gateway keeps its own heartbeat executor.
 */
internal object RoomAssetReceiver {
    class Assets internal constructor(
        private val directory: File,
        val files: Map<String, File>,
        val manifest: List<RoomAssetManifest.Entry>,
        private val current: () -> Boolean
    ) : Closeable {
        private val retired = AtomicBoolean(false)
        fun isCurrent() = !retired.get() && current()
        override fun close() {
            if (!retired.compareAndSet(false, true)) return
            // Only these exact files and this newly created directory are owned.
            files.values.forEach { Files.deleteIfExists(it.toPath()) }
            Files.deleteIfExists(directory.toPath())
        }
    }
    fun receive(round: RoomGuestSession.Round, admission: RoomInvitation.Admission,
                privateRoot: File, localRom: File? = null,
                onProgress: (Long, Long) -> Unit = { _, _ -> }): Assets {
        val entries = RoomAssetManifest.entries(admission)
        val total = entries.sumOf { it.bytes }
        require(privateRoot.isDirectory) { "asset-private-directory" }
        val directory = Files.createTempDirectory(privateRoot.toPath(), "room-assets-").toFile()
        val files = entries.associate { it.kind to File(directory, it.kind + ".bin") }
        val owner = Assets(directory, files, entries) { !round.isCancelled && admission.isCurrent() }
        var channel: RoomGatewayChannel? = null
        var wire: RoomAssetWire? = null
        val timer = Executors.newSingleThreadScheduledExecutor { Thread(it, "wantap-assets").apply { isDaemon = true } }
        val key = admission.assets.capability.copyOf()
        var deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5)
        var completed = false
        fun checkOwner() {
            round.checkCurrent(); check(admission.isCurrent()) { "asset-cancelled" }
            check(System.nanoTime() < deadline) { "asset-timeout" }
            check(channel?.isClosed != true) { "asset-carrier-closed" }
        }
        fun ensure() { checkOwner(); wire?.ensure() }
        fun hashFile(file: File, entry: RoomAssetManifest.Entry): Boolean {
            if (!file.isFile || file.length() != entry.bytes) return false
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    ensure(); val n = input.read(buffer); if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return RoomInvitation.hex(digest.digest()) == entry.sha256
        }
        try {
            ensure()
            // The sender may wait for this opted-in seat's prefetch before normal
            // transfer. Wait before opening the normal carrier/its idle deadline.
            val prefetched = round.prefetch?.localRom(admission) {
                round.checkCurrent(); check(admission.isCurrent()) { "asset-cancelled" }
            }
            val candidate = prefetched ?: localRom
            deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5)
            val activeWire = RoomAssetWire(send = { bytes ->
                ensure(); channel?.send(bytes)?.get(10, TimeUnit.SECONDS) == true
            }, ensureCurrent = ::checkOwner)
            wire = activeWire
            val activeChannel = RoomGatewayChannel.open(round, admission, admission.assets, true,
                onData = activeWire::push, onClosed = { activeWire.close("asset-carrier-closed") })
            channel = activeChannel
            timer.scheduleWithFixedDelay({
                try { ensure(); activeWire.writable() }
                catch (_: Exception) { activeWire.close("asset-cancelled"); activeChannel.close() }
            }, 20, 20, TimeUnit.MILLISECONDS)
            activeChannel.ready.get(30, TimeUnit.SECONDS); ensure()
            val features = activeWire.authenticateReceiver(key)
            val mustSupplyRom = admission.room["sendROM"] == false
            var done = 0L
            for (entry in entries) {
                ensure()
                val output = files.getValue(entry.kind)
                var reuse = false
                if (entry.kind == "rom" && candidate != null && features.reuse) {
                    try {
                    // Private copy avoids a user changing the source after its hash.
                    require(candidate.isFile && candidate.length() == entry.bytes) { "asset-local-rom-invalid" }
                    candidate.inputStream().use { input -> output.outputStream().use { target ->
                        val buffer = ByteArray(65536); var copied = 0L
                        while (copied < entry.bytes) {
                            ensure(); val n = input.read(buffer, 0, minOf(buffer.size.toLong(), entry.bytes - copied).toInt())
                            check(n > 0) { "asset-local-rom-invalid" }; target.write(buffer, 0, n); copied += n
                        }
                        check(input.read() == -1) { "asset-local-rom-invalid" }
                    } }
                    reuse = hashFile(output, entry)
                    } catch (e: Exception) {
                        ensure()
                        if (prefetched == null) throw e
                        // A missing/damaged optional candidate uses normal ROM DATA.
                        reuse = false
                    }
                }
                check(!mustSupplyRom || entry.kind != "rom" || reuse) { "asset-local-rom-required" }
                activeWire.send(RoomAssetWire.REQUEST, entry.metadata(reuse))
                if (!reuse) {
                    val digest = MessageDigest.getInstance("SHA-256")
                    output.outputStream().use { target ->
                        var received = 0L
                        while (received < entry.bytes) {
                            ensure(); val chunk = activeWire.frame(RoomAssetWire.DATA)
                            check(chunk.size.toLong() == minOf(65536L, entry.bytes - received)) { "asset-chunk-size" }
                            target.write(chunk); digest.update(chunk); received += chunk.size; done += chunk.size
                            chunk.fill(0); onProgress(done, total)
                        }
                    }
                    check(RoomInvitation.hex(digest.digest()) == entry.sha256) { "asset-download-hash" }
                } else { done += entry.bytes; onProgress(done, total) }
                ensure()
                check(activeWire.frame(RoomAssetWire.END).contentEquals(entry.metadata())) { "asset-metadata" }
            }
            check(done == total && activeWire.pendingBytes == 0) { "asset-trailing-data" }
            for (entry in entries) check(hashFile(files.getValue(entry.kind), entry)) { "asset-staged-hash" }
            ensure(); check(activeWire.pendingBytes == 0) { "asset-trailing-data" }
            activeWire.write(byteArrayOf(RoomAssetWire.COMMITTED.toByte()))
            // An empty journal after accepting COMMITTED proves its transport ACK.
            val receiptDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (!activeChannel.drained().get(10, TimeUnit.SECONDS)) {
                ensure(); check(System.nanoTime() < receiptDeadline) { "asset-receipt-timeout" }; Thread.sleep(20)
            }
            ensure(); check(activeWire.pendingBytes == 0) { "asset-trailing-data" }
            completed = true; return owner
        } finally {
            timer.shutdownNow(); wire?.close(); channel?.close(); key.fill(0)
            round.prefetch?.close()
            if (!completed) owner.close()
        }
    }
}

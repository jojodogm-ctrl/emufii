package com.mpds.online

import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Outer room lifecycle, independent of both displays and the emulator thread.
 * Polls continue while a round downloads/boots. One serial round executor retires
 * its old owner before starting another; late completions cannot regain control.
 */
internal class RoomGuestSession(
    private val directory: RoomDirectory = RoomDirectory(),
    private val startRound: (Round) -> Closeable,
    private val onSnapshot: (RoomDirectory.Snapshot) -> Unit,
    private val onDisconnected: (RoomDirectory.Player) -> Unit,
    private val onError: (String) -> Unit,
    private val prefetchRoot: java.io.File? = null,
    private val prepare: (RoomDirectory.RomPrefetchOffer, RoomDirectory.Snapshot, java.io.File,
                          RoomDirectory.GatewayTicket) -> RoomPreparation = ::RoomRomPrefetch
) : Closeable {
    internal class Round internal constructor(
        val snapshot: RoomDirectory.Snapshot,
        internal val launch: RoomDirectory.Launch,
        private val directory: RoomDirectory?,
        internal val prefetch: RoomPreparation? = null,
        private val hostCurrent: (() -> Boolean)? = null,
        private val hostTicket: ((String, Int, String, Long) -> String)? = null
    ) {
        private val cancelled = AtomicBoolean(false)
        val isCancelled: Boolean get() = cancelled.get()
        fun checkCurrent() { check(!isCancelled && hostCurrent?.invoke() != false) { "round-retired" } }
        internal fun cancel() { cancelled.set(true); prefetch?.close() }
        internal fun ticket(room: String, seat: Int, role: String, expiresAt: Long): RoomDirectory.GatewayTicket {
            checkCurrent()
            val result = if (hostTicket != null) RoomDirectory.GatewayTicket(hostTicket.invoke(room, seat, role, expiresAt))
                else requireNotNull(directory).gatewayTicket(snapshot.generation, room, seat, role, expiresAt)
            checkCurrent(); return result
        }
        override fun toString() = "Round(generation=${snapshot.generation}, capabilities=redacted)"
    }

    private val roomWorker = Executors.newSingleThreadScheduledExecutor { Thread(it, "wantap-room") }
    private val roundWorker = Executors.newSingleThreadExecutor { Thread(it, "wantap-round") }
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val termination = CompletableFuture<Unit>()
    private val lock = Any()
    private var current: Round? = null
    private var previous: RoomDirectory.Snapshot? = null
    private var waitingPollFailures = 0
    private var playerName = ""
    private var preparation: RoomPreparation? = null
    // Bound history and stop opting in when full; a retired route is never reopened.
    private val attempted = mutableSetOf<String>()
    // Only roundWorker accesses this handle.
    private var handle: Closeable? = null

    fun join(code: String, name: String) {
        check(started.compareAndSet(false, true) && !closed.get()) { "room-already-started" }
        playerName = name
        roomWorker.execute {
            try {
                if (!closed.get()) adopt(directory.join(code, name))
                if (!closed.get()) roomWorker.scheduleWithFixedDelay({
                    if (!closed.get()) try {
                        val snapshot = directory.poll(name)
                        waitingPollFailures = 0
                        adopt(snapshot)
                    } catch (e: Exception) {
                        val code = errorCode(e)
                        // Waiting has no game owner or startup side effects. A
                        // temporary lost HTTP response must not kick the player.
                        // Never retry revoked credentials, invalid JSON, TLS
                        // failures, or a live round using stale directory state.
                        val transient = code in setOf("directory_timeout", "directory_connection")
                        if (previous?.phase != RoomDirectory.Phase.WAITING || !transient ||
                            ++waitingPollFailures >= 3) fail(code)
                    }
                }, 2, 2, TimeUnit.SECONDS)
            } catch (e: Exception) { fail(errorCode(e)) }
        }
    }

    private fun adopt(snapshot: RoomDirectory.Snapshot, pollPrefetch: Boolean = true) {
        synchronized(lock) {
            if (closed.get()) return
            val old = previous
            if (old?.phase == RoomDirectory.Phase.RUNNING) {
                for (member in old.roster) {
                    if (member.connected && snapshot.roster.none { it.seat == member.seat && it.connected })
                        observe { onDisconnected(member) }
                    if (closed.get()) return
                }
            }
            previous = snapshot
            observe { onSnapshot(snapshot) }
            // An observer may synchronously leave the room. Its close has
            // already shut down the round queue; never enqueue another start.
            if (closed.get()) return
            // Snapshot edits fence preparation even when the generation is unchanged.
            preparation?.let {
                if (!it.matches(snapshot)) {
                    it.close(); preparation = null
                }
            }
            if (pollPrefetch && snapshot.phase == RoomDirectory.Phase.WAITING && prefetchRoot != null &&
                (preparation != null || attempted.size < 256)) {
                try {
                    val offer = directory.readRomPrefetch()
                    val active = preparation
                    if (offer == null && active != null) {
                        // Successful launch removes offer metadata but preserves its
                        // routes. A read can straddle launch after our waiting poll.
                        val refreshed = directory.poll(playerName)
                        if (refreshed.phase != RoomDirectory.Phase.RUNNING || !active.matches(refreshed)) {
                            active.close(); preparation = null
                        }
                        adopt(refreshed, pollPrefetch = false)
                        return
                    }
                    if (offer == null || active != null && !active.sameOffer(offer)) {
                        active?.close(); preparation = null
                    }
                    if (offer != null && preparation == null && attempted.size < 256 && attempted.add(offer.attempt)) {
                        preparation = prepare(offer, snapshot, prefetchRoot,
                            directory.preparationTicket(offer))
                    }
                } catch (_: Exception) {
                    // A failed optional read says nothing about an already owned
                    // candidate. Keep it until an explicit change, removal or expiry.
                    preparation?.let {
                        if (!it.matches(snapshot) || it.expired) { it.close(); preparation = null }
                    }
                }
            }
            current?.prefetch?.let { if (!it.matches(snapshot) || it.expired) it.close() }
            val nextGeneration = snapshot.takeIf { it.phase == RoomDirectory.Phase.RUNNING }?.generation
            if (current?.snapshot?.generation == nextGeneration) return
            current?.cancel()
            current = nextGeneration?.let {
                Round(snapshot, directory.launchForRunner(it), directory, preparation).also { preparation = null }
            }
            val next = current
            roundWorker.execute {
                try {
                    handle?.close(); handle = null
                    if (next != null && !closed.get() && !next.isCancelled) {
                        next.checkCurrent()
                        val opened = startRound(next)
                        if (closed.get() || next.isCancelled) opened.close() else handle = opened
                    }
                } catch (e: Exception) {
                    // A failed retirement is terminal; do not launch a second
                    // emulator alongside an owner whose cleanup is uncertain.
                    observe { onError("round_failed") }
                    if (!closed.get()) try {
                        roomWorker.execute {
                            try {
                                val actual = directory.snapshot
                                if (next != null && actual?.phase == RoomDirectory.Phase.RUNNING &&
                                    actual.generation == next.snapshot.generation)
                                    directory.failed(actual.generation)
                            } catch (_: Exception) { /* leave still revokes our local capability */ }
                            closeAsync()
                        }
                    } catch (_: RejectedExecutionException) { /* closing */ }
                }
            }
        }
    }

    private fun fail(code: String) {
        if (!closed.get()) observe { onError(code) }
        closeAsync()
    }

    fun closeAsync(): CompletableFuture<Unit> {
        if (!closed.compareAndSet(false, true)) return termination
        synchronized(lock) {
            current?.cancel(); current = null
            preparation?.close(); preparation = null
        }
        // Already queued starts observe cancellation. Retirement precedes
        // capability deletion. UI callers never wait on either network or NDS.
        roundWorker.execute {
            var failure: Exception? = null
            try { handle?.close(); handle = null } catch (e: Exception) { failure = e }
            roomWorker.execute {
                try { directory.leave() } catch (_: Exception) { /* remote lease expires */ }
                roomWorker.shutdownNow()
                if (failure == null) termination.complete(Unit)
                else termination.completeExceptionally(IllegalStateException("round-retirement-failed"))
            }
        }
        roundWorker.shutdown()
        return termination
    }
    override fun close() { closeAsync() }
    private fun observe(callback: () -> Unit) { try { callback() } catch (_: Exception) { /* UI observer */ } }
    private fun errorCode(error: Exception): String =
        (error as? RoomDirectory.DirectoryError)?.code ?: "room_connection_failed"
}

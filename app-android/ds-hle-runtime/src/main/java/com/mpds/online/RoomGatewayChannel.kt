package com.mpds.online

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Production endpoint is application-owned, never taken from an invitation.
 * Open on the room worker, not UI. A single serial executor owns the peer; every
 * external callback is count/byte bounded. No packet, key or ticket is logged.
 */
internal class RoomGatewayChannel private constructor(
    private val isCurrent: () -> Boolean,
    private val channel: RoomInvitation.Channel,
    private val stream: Boolean,
    private val onData: (ByteArray) -> Boolean,
    private val onClosed: (String) -> Unit,
    private val factory: WebSocket.Factory,
    private val monotonic: () -> Double,
    private val wall: () -> Long
) : Closeable {
    internal class Failure(val code:String) : Exception(code)
    val ready = CompletableFuture<Unit>()
    private val stopped = AtomicBoolean(false)
    private val pending = AtomicInteger()
    private val pendingBytes = AtomicInteger()
    private val worker = ScheduledThreadPoolExecutor(1, java.util.concurrent.ThreadFactory {
        Thread(it, "wantap-relay").apply { isDaemon = true }
    })
    @Volatile private var socket: WebSocket? = null
    private var admitted = false
    private var opened = false
    private var peer: RoomRelayPeer? = null
    private val lock = java.lang.Object()
    init {
        worker.setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
        worker.setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
        worker.removeOnCancelPolicy = true
    }

    companion object {
        private const val ENDPOINT = BuildConfig.GATEWAY_ENDPOINT
        private val http by lazy {
            OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(false).connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS).writeTimeout(10, TimeUnit.SECONDS).build()
        }
        fun open(round: RoomGuestSession.Round, admission: RoomInvitation.Admission,
                 channel: RoomInvitation.Channel, stream: Boolean,
                 onData: (ByteArray) -> Boolean, onClosed: (String) -> Unit): RoomGatewayChannel {
            round.checkCurrent(); check(admission.isCurrent())
            require(channel === admission.sdk || channel === admission.lobby ||
                channel === admission.assets || channel === admission.authority)
            val c = channel.config
            val ticket = round.ticket(c.room, c.seat, c.role, c.expiresAtUnixMs)
            round.checkCurrent(); check(admission.isCurrent())
            return openAdmitted(admission, channel, stream, ticket.ticket, onData, onClosed, http)
        }
        /** Host-only carrier; config/key must derive from the accepted signed seat admission. */
        internal fun openHost(round:RoomGuestSession.Round, admission:RoomInvitation.Admission,
            original:RoomInvitation.Channel, stream:Boolean, onData:(ByteArray)->Boolean,
            onClosed:(String)->Unit):RoomGatewayChannel {
            round.checkCurrent();check(admission.isCurrent())
            require(original === admission.sdk || original === admission.lobby ||
                original === admission.assets || original === admission.authority)
            val config=original.config.server()
            val channel=RoomInvitation.Channel(config,original.capability,original.authenticationKey)
            val ticket=round.ticket(config.room,config.seat,"server",config.expiresAtUnixMs).ticket
            require(Regex("[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]").matches(ticket))
            round.checkCurrent();check(admission.isCurrent())
            return RoomGatewayChannel({!round.isCancelled && admission.isCurrent()},channel,stream,
                onData,onClosed,http,{System.nanoTime()/1000000.0},System::currentTimeMillis).also {it.connect(ticket)}
        }
        internal fun openCheckpointOwner(round:RoomGuestSession.Round,admission:RoomInvitation.Admission,
            keys:RoomCheckpointLane.Keys,onData:(ByteArray)->Boolean,onClosed:(String)->Unit):RoomGatewayChannel {
            round.checkCurrent();check(admission.isCurrent())
            val channel=keys.channel;val c=channel.config
            require(c.room.startsWith("K")&&c.room!=admission.config.room&&c.seat==admission.config.seat&&
                c.relayAddress==admission.config.relayAddress&&c.relayPort==admission.config.relayPort&&
                c.expiresAtUnixMs==admission.config.expiresAtUnixMs)
            val ticket=round.ticket(c.room,c.seat,c.role,c.expiresAtUnixMs)
            round.checkCurrent();check(admission.isCurrent())
            return RoomGatewayChannel(admission::isCurrent,channel,true,onData,onClosed,http,
                {System.nanoTime()/1000000.0},System::currentTimeMillis).also { it.connect(ticket.ticket) }
        }
        // Replacement routes are validated independently of the initial lanes.
        // Gateway URL remains application-owned and tickets remain single-use.
        internal fun openCheckpointReplacement(round:RoomGuestSession.Round,admission:RoomInvitation.Admission,
            route:RoomCheckpointRoutes.Route,stream:Boolean,onData:(ByteArray)->Boolean,onClosed:(String)->Unit):RoomGatewayChannel {
            round.checkCurrent();check(admission.isCurrent())
            val channel=route.channel;val c=channel.config
            require(c.seat==admission.config.seat&&c.relayAddress==admission.config.relayAddress&&
                c.relayPort==admission.config.relayPort&&c.expiresAtUnixMs<=admission.config.expiresAtUnixMs&&
                c.expiresAtUnixMs>System.currentTimeMillis()&&c.room!=admission.config.room)
            val ticket=round.ticket(c.room,c.seat,c.role,c.expiresAtUnixMs)
            round.checkCurrent();check(admission.isCurrent())
            return RoomGatewayChannel(admission::isCurrent,channel,stream,onData,onClosed,http,
                {System.nanoTime()/1000000.0},System::currentTimeMillis).also { it.connect(ticket.ticket) }
        }
        // Injectable carrier for JVM tests only. Endpoint stays fixed and secure.
        internal fun openAdmitted(admission: RoomInvitation.Admission, channel: RoomInvitation.Channel,
                                 stream: Boolean, ticket: String, onData: (ByteArray) -> Boolean,
                                 onClosed: (String) -> Unit, factory: WebSocket.Factory,
                                 monotonic: () -> Double = { System.nanoTime() / 1000000.0 },
                                 wall: () -> Long = System::currentTimeMillis): RoomGatewayChannel {
            require(Regex("[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]").matches(ticket))
            require(channel === admission.sdk || channel === admission.lobby ||
                channel === admission.assets || channel === admission.authority)
            check(admission.isCurrent())
            return RoomGatewayChannel(admission::isCurrent, channel, stream, onData, onClosed, factory, monotonic, wall).also { it.connect(ticket) }
        }
        /** Independent preparation route. No Admission can be obtained from this API. */
        internal fun openPreparation(offer: RoomDirectory.RomPrefetchOffer,
                                     ticket: RoomDirectory.GatewayTicket, current: () -> Boolean,
                                     onData: (ByteArray) -> Boolean, onClosed: (String) -> Unit): RoomGatewayChannel {
            check(current())
            val config = RoomInvitation.Config("party.lepast.fr", 443, offer.room, offer.seat - 1,
                offer.invitationId, 600, offer.expiresAtUnixMs, 0)
            val capability = ByteArray(32) { offer.capability.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            return RoomGatewayChannel(current, RoomInvitation.Channel(config, capability), true,
                onData, { reason -> capability.fill(0); onClosed(reason) }, http,
                { System.nanoTime() / 1000000.0 }, System::currentTimeMillis).also { it.connect(ticket.ticket) }
        }
    }
    private fun dispatch(bytes: Int = 0, onDiscard: () -> Unit = {}, action: () -> Unit): Boolean {
        synchronized(lock) {
            if (stopped.get() || pending.get() >= 64 || pendingBytes.get() + bytes > 131072) return false
            pending.incrementAndGet(); pendingBytes.addAndGet(bytes)
            worker.execute {
                try { if (!stopped.get()) action() else onDiscard() }
                catch (_: Exception) { onDiscard(); stop("carrier-operation-failed") }
                finally { synchronized(lock) {
                    pending.decrementAndGet(); pendingBytes.addAndGet(-bytes); lock.notifyAll()
                } }
            }
            return true
        }
    }
    // OkHttp invokes socket messages serially on its reader, not our peer
    // executor. Backpressure that reader when the bounded queue is full: TCP
    // then slows the sender instead of closing a healthy asset transfer.
    // Keep send() nonblocking and keep all peer operations on their executor.
    private fun receiveDispatch(bytes: Int, action: () -> Unit): Boolean {
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
        synchronized(lock) {
            while(!stopped.get()) {
                if(dispatch(bytes,action=action))return true
                val remaining=deadline-System.nanoTime()
                if(remaining<=0)return false
                try { TimeUnit.NANOSECONDS.timedWait(lock,remaining) }
                catch(_:InterruptedException) { Thread.currentThread().interrupt();return false }
            }
            return false
        }
    }
    private fun current(): Boolean {
        if (stopped.get()) return false
        if (!isCurrent()) { stop("round-retired"); return false }
        return true
    }
    private fun connect(ticketValue: String) {
        // Ticket is single-use; erase this reference after its first socket open.
        var ticket: String? = ticketValue
        dispatch {
            if (!current()) return@dispatch
            peer = RoomRelayPeer(channel.config, channel.capability, stream, { bytes ->
                val s = socket
                current() && admitted && s != null && s.queueSize() + bytes.size <= 64L * RoomRelayCodec.MAX_PACKET &&
                    s.send(bytes.toByteString())
            }, { bytes -> current() && onData(bytes) }, { event ->
                when (event) {
                    "stream-ready", "datagram-ready" -> if (current()) ready.complete(Unit)
                    "closed" -> stop(peer?.reason ?: "carrier-closed")
                }
            }, now = monotonic, wall = wall)
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (!dispatch openEvent@{
                        if (!current()) return@openEvent
                        if (opened || response.header("Sec-WebSocket-Protocol") != "nifi2-ticket-v1") {
                            stop("gateway-admission-rejected"); return@openEvent
                        }
                        opened = true
                        val t = ticket; ticket = null
                        if (t == null || !webSocket.send("NIFI2-TICKET-1 $t")) stop("gateway-admission-rejected")
                    }) webSocket.cancel()
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.length > 64 || !dispatch(text.length) textEvent@{
                        if (!current()) return@textEvent
                        if (!opened || admitted || text != "NIFI2-ADMITTED-1") stop("gateway-admission-rejected")
                        else { admitted = true; peer!!.start() }
                    }) stop("carrier-message")
                }
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    if (bytes.size > RoomRelayCodec.MAX_PACKET || !receiveDispatch(bytes.size) binaryEvent@{
                        if (!current()) return@binaryEvent
                        if (!admitted) stop("gateway-admission-rejected") else peer!!.receive(bytes.toByteArray())
                    }) stop("carrier-input-overflow")
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = stop("carrier-closed")
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = stop("carrier-closed")
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = stop("carrier-failed")
            }
            val created = factory.newWebSocket(Request.Builder().url(ENDPOINT)
                // Browsers supply this automatically; OkHttp does not. The
                // public gateway requires our fixed application origin before
                // it upgrades, then independently verifies the one-use ticket.
                .header("Origin", BuildConfig.GATEWAY_ORIGIN)
                .header("Sec-WebSocket-Protocol", "nifi2-ticket-v1").build(), listener)
            socket = created
            if (stopped.get()) created.cancel()
        }
        synchronized(lock) {
            if (stopped.get()) return
            worker.schedule({ if (!admitted) stop("carrier-connect-timeout") }, 30, TimeUnit.SECONDS)
            worker.scheduleAtFixedRate({
                try { if (current() && admitted) peer?.tick() }
                catch (_: Exception) { stop("carrier-tick-failed") }
            }, 20, 20, TimeUnit.MILLISECONDS)
        }
    }
    /** Atomic enqueue result. false means caller retains all bytes and may retry.
     * Do not wait on this future from an onData/onClosed callback. */
    fun send(bytes: ByteArray): CompletableFuture<Boolean> {
        require(bytes.size in 1..if (stream) 65536 else 1024)
        val result = CompletableFuture<Boolean>()
        val copy = bytes.copyOf()
        if (!dispatch(copy.size, { copy.fill(0); result.complete(false) }) {
            try { result.complete(current() && ready.isDone && !ready.isCompletedExceptionally && peer?.send(copy) == true) }
            finally { copy.fill(0) }
        }) { copy.fill(0); result.complete(false) }
        return result
    }
    private val terminated = CompletableFuture<Unit>()
    val isClosed: Boolean get() = stopped.get()
    fun drained(): CompletableFuture<Boolean> {
        val result = CompletableFuture<Boolean>()
        if (!dispatch(onDiscard = { result.complete(false) }) {
            result.complete(current() && ready.isDone && !ready.isCompletedExceptionally && peer?.outgoingRecords == 0)
        }) result.complete(false)
        return result
    }
    private fun stop(reason: String) {
        synchronized(lock) {
            if (!stopped.compareAndSet(false, true)) return
            lock.notifyAll()
            runCatching { android.util.Log.w("Wantap-room", "Gateway closed: $reason") }
            socket?.cancel()
            ready.completeExceptionally(Failure(reason))
            worker.execute {
                try { peer?.close(reason) } finally {
                    terminated.complete(Unit)
                    try { onClosed(reason) } catch (_: Exception) { }
                }
            }
            worker.shutdown()
        }
    }
    override fun close() = stop("stopped")
}

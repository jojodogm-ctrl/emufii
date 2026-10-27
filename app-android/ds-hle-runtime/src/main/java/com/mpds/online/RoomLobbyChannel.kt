package com.mpds.online

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal interface RoomLobbyPort : Closeable {
    val ready: CompletableFuture<Unit>
    val diagnostics: Boolean
    fun send(message: Map<String, Any?>): CompletableFuture<Unit>
}

/** Authenticated JSON lane. Downloads/game startup MUST run outside onMessage.
 * Owns two small workers (blocking reader and serialized writer), while the
 * gateway heartbeat lives on its independent executor. No UI thread blocks.
 */
internal class RoomLobbyChannel private constructor(
    private val round: RoomGuestSession.Round,
    private val admission: RoomInvitation.Admission,
    private val onMessage: (Map<String, Any?>) -> Unit,
    private val onClosed: (String) -> Unit,
    private val host: Boolean = false
) : RoomLobbyPort {
    override val ready = CompletableFuture<Unit>()
    @Volatile override var diagnostics = false; private set
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val heartbeatPending = AtomicBoolean(false)
    private val queueLock = Any()
    private var queuedBytes = 0
    private val writer = ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,ArrayBlockingQueue(32),
        java.util.concurrent.ThreadFactory { Thread(it,"wantap-lobby-writer").apply { isDaemon=true } })
    private val maintenance = Executors.newSingleThreadScheduledExecutor {
        Thread(it,"wantap-lobby-heartbeat").apply { isDaemon=true }
    }
    @Volatile private var gateway: RoomGatewayChannel? = null
    private val wire = RoomAssetWire(send = { bytes ->
        ensure(); gateway?.send(bytes)?.get(10,TimeUnit.SECONDS) == true
    },ensureCurrent=::ensure)
    private val incomingRate = Rate()
    private val controlRate = Rate()
    private val outgoingRate = Rate()
    private class Rate {
        private var tokens = 32.0
        private var at = System.nanoTime()
        @Synchronized fun take() {
            val now=System.nanoTime()
            tokens=minOf(32.0,tokens+maxOf(0L,now-at)/1e9*16.0);at=now
            check(tokens>=1) { "lobby-rate-limit" };tokens--
        }
    }
    // The host's lane becomes ready only once a guest reaches the gateway. A
    // guest may still be installing, joining or pressing Play, so the host waits
    // visibly (its lobby keeps "waiting for players") instead of closing the room
    // after 30 s. Still bounded, and released as soon as the lobby is closed.
    private fun awaitGateway(ready: CompletableFuture<Unit>, host: Boolean) {
        val deadline = System.nanoTime() + gatewayWaitNanos(host)
        while (true) {
            ensure()
            try { ready.get(1, TimeUnit.SECONDS); return }
            catch (timeout: java.util.concurrent.TimeoutException) {
                if (System.nanoTime() - deadline >= 0) throw timeout
            }
        }
    }
    companion object {
        internal const val GUEST_GATEWAY_WAIT_SECONDS = 30L
        internal const val HOST_GATEWAY_WAIT_SECONDS = 15 * 60L
        internal fun gatewayWaitNanos(host: Boolean) =
            TimeUnit.SECONDS.toNanos(if (host) HOST_GATEWAY_WAIT_SECONDS else GUEST_GATEWAY_WAIT_SECONDS)
        fun open(round: RoomGuestSession.Round, admission: RoomInvitation.Admission,
                 onMessage: (Map<String,Any?>)->Unit, onClosed:(String)->Unit,
                 startImmediately:Boolean = true): RoomLobbyChannel {
            require(admission.room["lobbyProtocol"] in listOf(1.0,2.0))
            require(admission.lobby.authenticationKey?.size==32)
            return RoomLobbyChannel(round,admission,onMessage,onClosed).also { if(startImmediately)it.start() }
        }
        fun openHost(round:RoomGuestSession.Round,admission:RoomInvitation.Admission,
            onMessage:(Map<String,Any?>)->Unit,onClosed:(String)->Unit):RoomLobbyChannel {
            require(admission.room["lobbyProtocol"] in listOf(1.0,2.0))
            require(admission.lobby.authenticationKey?.size==32)
            return RoomLobbyChannel(round,admission,onMessage,onClosed,true).also {it.start()}
        }
    }
    private fun ensure() {
        check(!closed.get()) { "lobby-closed" }
        round.checkCurrent();check(admission.isCurrent()) { "lobby-retired" }
    }
    private fun frame(): Pair<Int,ByteArray> {
        val header=wire.read(5);val h=ByteBuffer.wrap(header)
        val type=h.get().toInt() and 255;val size=h.int
        check(type==17 && size in 1..32768 || type in listOf(16,18) && size==1) { "lobby-protocol" }
        (if(type==17) incomingRate else controlRate).take()
        return type to wire.read(size)
    }
    internal fun start() {
        check(started.compareAndSet(false,true));ensure()
        maintenance.scheduleWithFixedDelay({
            try { ensure();wire.writable() } catch (_:Exception) { stop("lobby-retired") }
        },50,50,TimeUnit.MILLISECONDS)
        Thread({
            val key=admission.lobby.authenticationKey!!.copyOf()
            try {
                ensure()
                val opened=if(host)RoomGatewayChannel.openHost(round,admission,admission.lobby,true,wire::push,
                    {reason->stop(reason)}) else RoomGatewayChannel.open(round,admission,admission.lobby,true,wire::push,
                    { reason -> stop(reason) })
                gateway=opened
                if(closed.get()) { opened.close();return@Thread }
                awaitGateway(opened.ready, host);ensure();wire.carrierReady()
                diagnostics=if(host)wire.authenticateSender(key,reuse=false,diagnostics=true).diagnostics
                    else wire.authenticateReceiver(key,diagnostics=true).diagnostics
                wire.send(RoomAssetWire.TrustedFrame(16,1),byteArrayOf(1))
                val confirmation=frame()
                check(confirmation.first==16 && confirmation.second.contentEquals(byteArrayOf(1))) { "lobby-authentication" }
                key.fill(0);ensure();ready.complete(Unit)
                if(closed.get()) return@Thread
                maintenance.scheduleWithFixedDelay({
                    if(!closed.get() && heartbeatPending.compareAndSet(false,true))
                        enqueue(18,byteArrayOf(0)).whenComplete { _,error ->
                            heartbeatPending.set(false)
                            if(error!=null && !closed.get()) stop("lobby-heartbeat-failed")
                        }
                },2,2,TimeUnit.SECONDS)
                while(!closed.get()) {
                    val (type,bytes)=frame();ensure()
                    if(type==18) { check(bytes[0]==0.toByte());continue }
                    check(type==17) { "lobby-protocol" }
                    val json=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                    val value=RoomDirectory.parseStrictJson(json)
                    check(value is Map<*,*> && value.keys.all { it is String }) { "lobby-message" }
                    @Suppress("UNCHECKED_CAST")
                    onMessage(value as Map<String,Any?>)
                }
            } catch (e:Exception) {
                runCatching { android.util.Log.w("Wantap-room", "Lobby reader: " + e.javaClass.simpleName + " " +
                    e.stackTrace.filter { it.className.startsWith("com.mpds.online.") }.take(4).joinToString(" ")) }
                val cause=if(e is java.util.concurrent.ExecutionException)e.cause else e
                stop(when(cause) {
                    is RoomGatewayChannel.Failure -> cause.code
                    is RoomAssetWire.AssetError -> cause.code
                    else -> if(ready.isDone) "lobby-connection" else "lobby-authentication"
                })
            }
            finally { key.fill(0) }
        },"wantap-lobby-reader").apply { isDaemon=true;start() }
    }
    override fun send(message: Map<String,Any?>): CompletableFuture<Unit> {
        return try {
            ensure();check(ready.isDone && !ready.isCompletedExceptionally) { "lobby-not-ready" }
            val bytes=RoomJson.encode(message).toByteArray(Charsets.UTF_8)
            require(bytes.size in 1..32768);outgoingRate.take();enqueue(17,bytes)
        } catch(e:Exception) { CompletableFuture<Unit>().also { it.completeExceptionally(e) } }
    }
    private fun enqueue(type:Int,bytes:ByteArray): CompletableFuture<Unit> {
        val result=CompletableFuture<Unit>()
        synchronized(queueLock) {
            if(closed.get() || queuedBytes+bytes.size+5>131072) {
                result.completeExceptionally(IllegalStateException("lobby-queue-full"));return result
            }
            queuedBytes+=bytes.size+5
            try { writer.execute {
                try {
                    ensure();wire.send(RoomAssetWire.TrustedFrame(type,if(type==17)32768 else 1),bytes)
                    ensure();result.complete(Unit)
                } catch(e:Exception) { result.completeExceptionally(e);stop("lobby-send-failed") }
                finally { synchronized(queueLock) { queuedBytes-=bytes.size+5 } }
            } } catch(e:Exception) {
                queuedBytes-=bytes.size+5;result.completeExceptionally(IllegalStateException("lobby-queue-full"))
            }
        }
        return result
    }
    private fun stop(reason:String) {
        if(!closed.compareAndSet(false,true))return
        wire.close("lobby-closed");gateway?.close();maintenance.shutdownNow();writer.shutdown()
        ready.completeExceptionally(IllegalStateException(reason))
        try { onClosed(reason) } catch (_:Exception) { }
    }
    override fun close()=stop("stopped")
}

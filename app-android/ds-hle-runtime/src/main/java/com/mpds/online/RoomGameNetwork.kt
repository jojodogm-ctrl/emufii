package com.mpds.online

import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Carries native RPC and state bytes independently of the frame lock. The
 * native ticket must be configured first in this game's fresh Android process.
 * Never calls nativeRunFrame, never patches payloads or synthesizes responses.
 */
internal class RoomGameNetwork private constructor(
    private val round:RoomGuestSession.Round,
    private val admission:RoomInvitation.Admission,
    private val ticket:Long,
    private val onFailure:(String)->Unit,
    checkpoint:CheckpointConfig?,
    private val replacementSdk:RoomCheckpointRoutes.Route?=null
) : RoomCheckpointOwner.Network, RoomNativeNetwork {
    override val ready=CompletableFuture<Unit>()
    private val closed=AtomicBoolean(false)
    private val control=RoomControlFrames()
    private val states=ArrayDeque<RoomAuthorityWire.Packet>()
    private val lock=Any()
    private val codec=admission.authority?.let {
        RoomAuthorityWire((admission.room["seat"] as Double).toInt()-1,
            (admission.room["players"] as Double).toInt(),admission.room["epoch"] as String)
    }
    private val pump=Executors.newSingleThreadScheduledExecutor { Thread(it,"wantap-native-network").apply { isDaemon=true } }
    @Volatile private var sdk:RoomGatewayChannel?=null
    @Volatile private var authority:RoomGatewayChannel?=null
    private val replacementAuthorityClaimed=AtomicBoolean(false)
    @Volatile private var replacementAuthority:RoomCheckpointRoutes.Route?=null
    private var incoming:ByteArray?=null
    private var outgoing:ByteArray?=null
    private var pressureSince=0L
    private val checkpointCommands=ConcurrentHashMap.newKeySet<CompletableFuture<*>>()
    internal data class CheckpointConfig(val incarnation:String,val restore:RoomAuthorityCheckpoint.Image?=null)
    private val reliable=checkpoint?.let { config->
        require(admission.authority!=null)
        RoomCheckpointPump((admission.room["seat"] as Double).toInt()-1,
            (admission.room["players"] as Double).toInt(),admission.room["epoch"] as String,
            config.incarnation,config.restore,
            deliver={RoomEngine.nativeDeliverRoomPacket(ticket,1,it.source,it.bytes)},
            takeHeld={RoomEngine.nativeTakeHeldRoomState(ticket)?.let { bytes->
                require(bytes.size in 2..545)
                RoomAuthorityWire.Packet(bytes[0].toInt() and 255,bytes.copyOfRange(1,bytes.size))
            }})
    }

    companion object {
        fun open(round:RoomGuestSession.Round,admission:RoomInvitation.Admission,ticket:Long,
                 onFailure:(String)->Unit,checkpoint:CheckpointConfig?=null):RoomGameNetwork {
            require(ticket>0 && !admission.room.containsKey("lobbyProtocol"))
            return RoomGameNetwork(round,admission,ticket,onFailure,checkpoint).also { it.start() }
        }
        /** Fresh native ticket is held, old owner is retired. SDK connection
         * must complete before the cohort attaches fresh authority routes. */
        fun openRestoredSdk(round:RoomGuestSession.Round,admission:RoomInvitation.Admission,ticket:Long,
            route:RoomCheckpointRoutes.Route,checkpoint:CheckpointConfig,onFailure:(String)->Unit):RoomGameNetwork {
            try {
                require(ticket>0&&checkpoint.restore!=null&&!admission.room.containsKey("lobbyProtocol"))
                return RoomGameNetwork(round,admission,ticket,onFailure,checkpoint,route).also { it.start() }
            }catch(e:Exception){route.close();throw e}
        }
    }
    private fun current() { check(!closed.get());round.checkCurrent();check(admission.isCurrent()) }
    private fun start() {
        try {
            current()
            val controlData:(ByteArray)->Boolean={bytes->
                try { current();control.offer(bytes) } catch (_:Exception) { fail("game-control-input");false }
            }
            val controlClosed:(String)->Unit={fail("game-control-closed")}
            val commands=if(replacementSdk==null)RoomGatewayChannel.open(round,admission,admission.sdk,true,controlData,controlClosed)
                else RoomGatewayChannel.openCheckpointReplacement(round,admission,replacementSdk,true,controlData,controlClosed)
            sdk=commands
            if(closed.get()) { commands.close();return }
            val link=if(replacementSdk!=null)null else admission.authority?.let { route ->
                RoomGatewayChannel.open(round,admission,route,false,{bytes->
                    try {
                        current()
                        if(reliable!=null){
                            val accepted=reliable.receive(bytes)
                            if(reliable.phase()=="closed")fail("game-authority-checkpoint-input")
                            accepted
                        }else{
                            val packet=codec!!.decode(bytes)
                            synchronized(lock) { if(states.size==64)states.removeFirst();states.addLast(packet) };true
                        }
                    } catch (_:Exception) { if(reliable!=null)fail("game-authority-checkpoint-input");false }
                },{fail("game-authority-closed")})
            }
            authority=link
            if(closed.get()) { link?.close();commands.close();return }
            CompletableFuture.allOf(commands.ready,link?.ready ?: CompletableFuture.completedFuture(Unit))
                .whenComplete { _,error -> if(error!=null)fail("game-network-connect") else {
                    try {
                        current();ready.complete(Unit)
                        pump.scheduleWithFixedDelay({try { tick() } catch (_:Exception) { fail("game-network-failed") }},0,2,TimeUnit.MILLISECONDS)
                    } catch (_:Exception) { fail("game-network-retired") }
                } }
        } catch (_:Exception) { fail("game-network-connect") }
    }
    private fun tick() {
        current()
        // Polling uses only the carrier mutex, never the NDS/frame mutex.
        for(i in 0 until 64) {
            if(incoming==null)incoming=control.poll()
            val bytes=incoming ?: break
            when(RoomEngine.nativeDeliverRoomPacket(ticket,0,0,bytes)) {
                1 -> incoming=null
                0 -> break
                else -> error("native-control-rejected")
            }
        }
        if(reliable!=null&&authority!=null){
            for(wire in reliable.wireTick()){
                if(authority!!.send(wire).get(5,TimeUnit.SECONDS))reliable.admitted(wire)
                current()
            }
            reliable.inputTick()
        }else if(reliable==null)for(i in 0 until 64) {
            val packet=synchronized(lock) { states.pollFirst() } ?: break
            // State admission pressure is loss; native journals retransmit.
            check(RoomEngine.nativeDeliverRoomPacket(ticket,1,packet.source,packet.bytes)>=0)
        }
        for(i in 0 until 64) {
            if(outgoing==null)outgoing=RoomEngine.nativeTakeRoomPacket(ticket)
            if(outgoing==null){reliable?.outgoingDrained();break}
            val bytes=outgoing!!
            require(bytes.size>=3)
            val lane=bytes[0].toInt() and 255;val peer=bytes[1].toInt() and 255;val payload=bytes.copyOfRange(2,bytes.size)
            if(lane==0) {
                val accepted=sdk!!.send(RoomControlFrames.encode(payload)).get(5,TimeUnit.SECONDS)
                current()
                if(!accepted) {
                    val now=System.nanoTime();if(pressureSince==0L)pressureSince=now
                    check(now-pressureSince<TimeUnit.SECONDS.toNanos(5))
                    return // retain exactly this complete record for the next tick
                }
                pressureSince=0
            } else {
                require(lane==1 && codec!=null && authority!=null)
                if(reliable!=null){
                    when(reliable.send(peer,payload)){
                        1->Unit
                        0->return // Already taken from native: never discard on pressure.
                        else->error("checkpoint-native-outgoing-rejected")
                    }
                }else authority!!.send(codec.encode(peer,payload)).get(5,TimeUnit.SECONDS)
                current()
            }
            outgoing=null
        }
    }
    override fun attachRestoredAuthority(route:RoomCheckpointRoutes.Route):CompletableFuture<Unit>{
        try {
            current();check(replacementSdk!=null&&reliable!=null&&ready.isDone&&!ready.isCompletedExceptionally)
            check(replacementAuthorityClaimed.compareAndSet(false,true))
            replacementAuthority=route
            val opened=RoomGatewayChannel.openCheckpointReplacement(round,admission,route,false,{bytes->
                try { current();val accepted=reliable.receive(bytes)
                    if(reliable.phase()=="closed")fail("game-authority-checkpoint-input")
                    accepted
                }catch(_:Exception){fail("game-authority-checkpoint-input");false}
            },{fail("game-authority-closed")})
            authority=opened
            if(closed.get()){opened.close();route.close();error("game-network-closed")}
            opened.ready.whenComplete { _,error->if(error!=null)fail("game-authority-connect") }
            return opened.ready
        }catch(e:Exception){route.close();fail("game-checkpoint-attach");return CompletableFuture<Unit>().also { it.completeExceptionally(e) }}
    }
    private fun <T> checkpointCommand(action:(RoomCheckpointPump)->T):CompletableFuture<T>{
        val result=CompletableFuture<T>()
        checkpointCommands.add(result)
        result.whenComplete { _,_->checkpointCommands.remove(result) }
        try{pump.execute {
            try{current();check(ready.isDone&&!ready.isCompletedExceptionally);result.complete(action(checkNotNull(reliable)))}
            catch(e:Exception){result.completeExceptionally(e);fail("game-checkpoint-network")}
        }}catch(e:Exception){result.completeExceptionally(e)}
        return result
    }
    // Called after native park completes on the owner executor, never while
    // holding up the network pump needed by a pending emulation frame.
    override fun parkAuthorityAfterNativeHeld(operation:String)=checkpointCommand { it.parkAfterNativeHeld(operation) }.thenCompose { it }
    override fun captureAuthority()=checkpointCommand { it.capture() }
    override fun releaseAuthority()=checkpointCommand { it.release() }.thenCompose { it }
    override fun authorityReleasedMask()=checkpointCommand { it.releasedMask() }
    override fun resumeAuthority()=checkpointCommand { it.resume() }
    private fun fail(reason:String) {
        if(!closed.get()) { close();try { onFailure(reason) } catch (_:Exception) { } }
    }
    override fun close() {
        if(!closed.compareAndSet(false,true))return
        checkpointCommands.forEach { it.completeExceptionally(IllegalStateException("game-network-closed")) }
        runCatching { RoomEngine.nativeCloseRoomCarrier(ticket) }
        ready.completeExceptionally(IllegalStateException("game-network-closed"))
        reliable?.close();sdk?.close();authority?.close();control.close();synchronized(lock) { states.clear() };pump.shutdownNow()
        replacementSdk?.close();replacementAuthority?.close()
    }
}

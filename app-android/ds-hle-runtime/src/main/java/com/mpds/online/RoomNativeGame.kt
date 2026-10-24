package com.mpds.online

import java.io.Closeable
import android.content.Context
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Owns a fresh native service process while the lobby remains in the UI process.
 * onReady attaches rendering/audio; on attachment failure it must call the
 * provided abort function before draining any partially started renderer.
 * Its returned close must drain rendering/audio before returning. That contract
 * keeps native teardown from racing the frame owner.
 * MainActivity/process dispatch is responsible for providing that adapter.
 */
internal class RoomNativeGame private constructor(
    private val round:RoomGuestSession.Round,
    private val request:RoomGuestLobby.GameRequest
) : RoomGuestLobby.GameHandle {
    override val done=CompletableFuture<Unit>()
    @Volatile override var checkpointOwner:RoomCheckpointOwner?=null;private set
    private val closed=AtomicBoolean(false)
    private val failure=AtomicReference<String?>(null)
    private val watcher=Executors.newSingleThreadScheduledExecutor { Thread(it,"wantap-round-watch").apply { isDaemon=true } }
    @Volatile private var ticket=0L
    private var assets:RoomAssetReceiver.Assets?=null
    private var files:RoomLaunchFiles?=null
    @Volatile private var network:RoomNativeNetwork?=null
    private var rendering:Closeable?=null
    private var engine:Closeable?=null
    private var booted=false
    private var startStage=RoomStartFailure.Stage.ENGINE

    companion object {
        /** Blocking startup on the round worker, not Android's UI/event thread. */
        fun start(context:Context,round:RoomGuestSession.Round,request:RoomGuestLobby.GameRequest,
                  privateRoot:File,localRom:File?=null,
                  progress:(String,Long,Long)->Unit={_,_,_->},
                  onReady:(Int,()->Unit)->Closeable,
                  hostAssets:(()->RoomAssetReceiver.Assets)?=null,
                  hostNetwork:((Long,(String)->Unit)->RoomNativeNetwork)?=null):RoomNativeGame {
            val owner=RoomNativeGame(round,request)
            try {
                owner.engine=RoomEngine.open(context.applicationContext)
                owner.start(privateRoot,localRom,progress,onReady,hostAssets,hostNetwork)
                return owner
            } catch(error:Throwable) {
                // Code locations/types only: exception messages may contain
                // invitation material or private paths. Preserve the origin
                // before teardown replaces the native process.
                android.util.Log.w("Wantap-room", "Startup ${owner.startStage.code}: " +
                    error.javaClass.simpleName + " " + error.stackTrace.take(3).joinToString(" "))
                try { owner.close() } catch(cleanup:Throwable) { error.addSuppressed(cleanup) }
                throw RoomStartFailure(owner.startStage,error)
            }
        }
    }
    private fun current() {
        check(!closed.get()) { "game-closed" };round.checkCurrent();request.checkCurrent()
        check(failure.get()==null) { failure.get() ?: "game-failed" }
    }
    private fun fail(reason:String) {
        if(failure.compareAndSet(null,reason)) {
            // Only locally selected error codes, never throwable messages or
            // peer payloads: retain the first cause before carrier teardown.
            android.util.Log.w("Wantap-room", "Round stopped: $reason")
            checkpointOwner?.close()
            val active=ticket;if(active>0)runCatching { RoomEngine.nativeCloseRoomCarrier(active) }
            done.completeExceptionally(IllegalStateException(reason))
        }
    }
    private fun await(future:CompletableFuture<Unit>,seconds:Long) {
        val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds)
        while(true) {
            current();check(System.nanoTime()<end) { "game-start-timeout" }
            try { future.get(100,TimeUnit.MILLISECONDS);current();return }
            catch(_:TimeoutException) { /* recheck generation/cancellation */ }
        }
    }
    private fun start(root:File,localRom:File?,progress:(String,Long,Long)->Unit,onReady:(Int,()->Unit)->Closeable,
        hostAssets:(()->RoomAssetReceiver.Assets)?,hostNetwork:((Long,(String)->Unit)->RoomNativeNetwork)?) {
        current()
        watcher.scheduleWithFixedDelay({
            if(!closed.get())try { current() } catch(_:Exception) { fail("game-retired") }
        },0,50,TimeUnit.MILLISECONDS)
        val admission=request.admitted;val room=admission.room
        startStage=RoomStartFailure.Stage.ASSETS
        assets=hostAssets?.invoke() ?: RoomAssetReceiver.receive(round,admission,root,localRom) { received,total ->
            current();progress("download",received,total)
        }
        current();progress("preparation",0,0)
        startStage=RoomStartFailure.Stage.PREPARATION
        files=RoomLaunchFiles.stage(admission,assets!!,request.names,request.emblems,
            request.personalSave,root,::current)
        current();progress("waiting-for-host",0,0)
        startStage=RoomStartFailure.Stage.BARRIER
        // The host releases after real transfer commits, not elapsed time.
        await(request.startBarrier,300)
        startStage=RoomStartFailure.Stage.CONFIGURE
        val game=room["game"] as? String ?: "mpds"
        val gameId=listOf("nsmb","mkds","mpds","dkr","experimental").indexOf(game)
        require(gameId>=0)
        val prepared=game=="mpds" || game=="nsmb" || room["mkdsMenu"]==true
        val boot=if(prepared)1 else if(room["guestBoot"]=="download-play")2 else 0
        val generated=when(room["profile"]) {
            "nsmb-eu-generated-cartridge-v1","mkds-menu-amce-generated-v1" -> room["setSha256"] as String
            else -> ""
        }
        val seat=RoomInvitation.number(room["seat"]).toInt()
        val directory=files!!.directory
        ticket=RoomEngine.nativeConfigureRoom(gameId,boot,RoomInvitation.number(room["players"]).toInt(),
            seat,RoomInvitation.number(room["bufferTicks"]).toInt(),1,room["epoch"] as String,
            request.names[seat-1],if(game=="experimental")room["romSha256"] as String else "",
            if(game=="experimental")RoomInvitation.number(room["romBytes"])else 0L,
            request.personalSave!=null,game in listOf("nsmb","mkds","mpds"),generated,
            if(prepared && game!="mpds")File(directory,"preparation").absolutePath else "")
        check(ticket>0) { "game-native-configuration" };current()
        startStage=RoomStartFailure.Stage.NETWORK
        require(!request.authorityCheckpoints||game in listOf("mkds","nsmb"))
        network=hostNetwork?.invoke(ticket,::fail) ?: RoomGameNetwork.open(round,admission,ticket,{reason->
            if(checkpointOwner?.retiring!=true)fail(reason)
        },if(request.authorityCheckpoints)RoomGameNetwork.CheckpointConfig("1")else null)
        await(network!!.ready,30);progress("starting",0,0)
        startStage=RoomStartFailure.Stage.BOOT
        check(RoomEngine.nativeBootRoom(ticket,directory.absolutePath)==1) { "game-native-boot" }
        booted=true
        if(request.authorityCheckpoints){
            val owner=RoomCheckpointOwner(admission.config,room,ticket,network as RoomGameNetwork,
                openReplacement={id,route,config->RoomGameNetwork.openRestoredSdk(round,admission,id,route,config,{reason->
                    // On a later restore this replacement becomes the source.
                    // Ignore only its intentional retirement, never a failure
                    // of the new connection that has not yet been promoted.
                    if(!(checkpointOwner?.retiring==true&&ticket==id))fail(reason)
                })},
                promote={id,replacement->ticket=id;network=replacement as RoomGameNetwork},
                current=::current,onFailure={fail("game-checkpoint-failed")},
                onNotice={state->current();GameLink.notices.checkpoint(state)})
            checkpointOwner=owner
            if(closed.get()){owner.close();current()}
        }
        startStage=RoomStartFailure.Stage.DISPLAY
        // A failed renderer attachment must wake a frame waiting on WM before
        // the UI adapter attempts to drain it. Normal retirement uses the same order.
        current();rendering=onReady(seat,::closeCarrier);current();progress("playing",0,0)
    }
    private fun closeCarrier() {
        checkpointOwner?.close()
        try { network?.close() }
        finally { if(ticket>0)runCatching { RoomEngine.nativeCloseRoomCarrier(ticket) } }
    }
    override fun close() {
        if(!closed.compareAndSet(false,true))return
        watcher.shutdownNow()
        // Closing the carrier FIRST wakes any frame blocked on a WM RPC.
        closeCarrier()
        var drainFailure:Exception?=null
        try { rendering?.close() } catch(e:Exception) { drainFailure=e }
        rendering=null
        // Preserve raw cartridge memory outside cache before destroying its owner.
        // Failed export leaves the paused native lease and source files intact.
        if(booted) { RoomEngine.exportRoomSave();booted=false }
        // Service retirement owns native shutdown and confirms process death.
        // If that fails, keep private files and the reservation intact.
        engine?.close();engine=null
        files?.close();files=null
        assets?.close();assets=null
        drainFailure?.let { throw it }
        done.complete(Unit)
    }
}

package com.mpds.online

import java.io.Closeable
import java.util.concurrent.CompletableFuture

/** Production network-worker boundary for reliable authority rooms. Native
 * callbacks touch ONLY RoomCarrier, never the frame lock or owner executor.
 * The owner must finish native park/hold before parkAfterNativeHeld(). SDK
 * traffic and wireTick() continue until the collective barriers complete. */
internal class RoomCheckpointPump(
    owner:Int,private val players:Int,epoch:String,incarnation:String,
    restore:RoomAuthorityCheckpoint.Image?=null,
    private val deliver:(RoomAuthorityWire.Packet)->Int,
    private val takeHeld:()->RoomAuthorityWire.Packet?,
    private val now:()->Long={System.nanoTime()/1_000_000}
) : Closeable {
    private val mesh=RoomAuthorityCheckpoint(owner,players,epoch,incarnation,restore,now=now)
    private var pending:RoomAuthorityWire.Packet?=null
    private var prefix=mutableListOf<RoomAuthorityWire.Packet>()
    private var operation:String?=null
    private var deadline=0L
    private var parking:CompletableFuture<Unit>?=null
    private var markerStarted=false
    private var closed=false
    private fun live(){check(!closed&&mesh.phase()!="closed")}
    @Synchronized fun receive(bytes:ByteArray):Boolean {live();return mesh.receive(bytes)}
    @Synchronized fun phase():String=if(closed) "closed" else if(operation!=null&&!markerStarted) "draining" else mesh.phase()
    @Synchronized fun inputTick(){
        live();if(operation!=null)return
        repeat(64){
            if(pending==null)pending=mesh.poll()
            val packet=pending ?: return
            when(deliver(packet)){
                1->pending=null
                0->return // Held/full native queue: retain this exact packet.
                else->error("checkpoint-native-state-rejected")
            }
        }
    }
    @Synchronized fun send(peer:Int,bytes:ByteArray):Int {live();return mesh.send(peer,bytes)}
    @Synchronized fun wireTick():List<ByteArray>{
        live()
        if(operation!=null&&!markerStarted&&now()>=deadline){close();error("checkpoint-native-drain-timeout")}
        return mesh.tick()
    }
    @Synchronized fun admitted(bytes:ByteArray){if(!closed)mesh.admitted(bytes)}
    @Synchronized fun parkAfterNativeHeld(op:String):CompletableFuture<Unit>{
        live();check(operation==null)
        operation=op;deadline=now()+30_000
        val result=CompletableFuture<Unit>();parking=result
        try{
            // Packets already admitted natively precede the one polled by this
            // worker; both precede newer packets still held by the mesh.
            while(true){val packet=takeHeld() ?: break
                check(prefix.size<256);prefix.add(packet)
            }
            pending?.let { prefix.add(it);pending=null }
        }catch(e:Exception){close();throw e}
        return result
    }
    /** Call only after pending outgoing bytes AND the native outgoing queue
     * have been drained into their respective SDK/carrier or reliable journal.
     * ACKs/retries can continue; journal admission, not wire send, is required. */
    @Synchronized fun outgoingDrained(){
        live();val op=operation ?: return
        if(markerStarted)return
        try{
            val frozen=mesh.park(op,prefix);markerStarted=true
            prefix.forEach { it.bytes.fill(0) };prefix.clear()
            frozen.whenComplete { _,error->if(error==null)parking?.complete(Unit) else parking?.completeExceptionally(error) }
        }catch(e:Exception){close();throw e}
    }
    @Synchronized fun capture():RoomAuthorityCheckpoint.Image {live();check(markerStarted);return mesh.capture()}
    @Synchronized fun release():CompletableFuture<Unit> {live();return mesh.release()}
    @Synchronized fun releasedMask():Int {live();check(mesh.phase()=="released");return (1 shl players)-1}
    @Synchronized fun resume(){
        live();check(markerStarted);mesh.resume();operation=null;markerStarted=false;parking=null
    }
    @Synchronized override fun close(){
        if(closed)return;closed=true;mesh.close()
        pending?.bytes?.fill(0);pending=null
        prefix.forEach { it.bytes.fill(0) };prefix.clear()
        parking?.completeExceptionally(IllegalStateException("checkpoint-network-closed"));parking=null
    }
}

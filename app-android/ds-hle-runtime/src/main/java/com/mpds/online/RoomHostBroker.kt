package com.mpds.online

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/** The native broker consumes SDK records, never DS game data or ROM bytes.
 * One serial owner drives pump; framing callbacks only admit bounded chunks. */
internal class RoomHostBroker(players:Int, coordinatedSnapshots:Boolean=false, sdkBlocks:Boolean=false) : Closeable {
    init { require(!sdkBlocks || !coordinatedSnapshots) }
    private val ticket=(if(sdkBlocks) HostBrokerBridge.createSdkBlocks(players,0,40)
        else HostBrokerBridge.create(players,0,coordinatedSnapshots)).also {check(it>0){"host-broker-init"}}
    private val stopped=AtomicBoolean(false)
    private val peers=linkedMapOf<Long,Peer>()
    internal inner class Peer internal constructor(val id:Long,private val send:(ByteArray)->Boolean) : Closeable {
        private val input=RoomControlFrames()
        private var closed=false
        @Synchronized fun receive(bytes:ByteArray):Boolean = !closed&&!stopped.get()&&input.offer(bytes)
        internal fun input()=input.poll()
        internal fun output(bytes:ByteArray)=send(RoomControlFrames.encode(bytes))
        internal fun drained()=input.drained()
        override fun close()=synchronized(this@RoomHostBroker) {
            if(closed)return@synchronized
            closed=true;input.close();peers.remove(id)
            if(!stopped.get())check(HostBrokerBridge.detach(ticket,id)){"host-broker-detach"}
        }
    }
    @Synchronized fun attach(send:(ByteArray)->Boolean):Peer {
        check(!stopped.get())
        val handle=HostBrokerBridge.attach(ticket);check(handle>0&&!peers.containsKey(handle)){"host-broker-capacity"}
        return Peer(handle,send).also {peers[handle]=it}
    }
    private fun flush() {
        for(peer in peers.values.toList()) for(index in 0 until 128) {
            val bytes=HostBrokerBridge.peek(ticket,peer.id) ?: break
            // Keep native output until complete framed bytes were admitted.
            check(peer.output(bytes)){"host-broker-output-refused"}
            check(HostBrokerBridge.pop(ticket,peer.id)){"host-broker-output-pop"}
        }
    }
    @Synchronized fun pump(nowMs:Long=System.nanoTime()/1_000_000) {
        check(!stopped.get())
        check(HostBrokerBridge.tick(ticket,nowMs)){"host-broker-tick"};flush()
        for(peer in peers.values.toList()) for(index in 0 until 128) {
            val bytes=peer.input() ?: break
            check(HostBrokerBridge.receive(ticket,peer.id,bytes,nowMs)){"host-broker-input"};flush()
        }
        check(!HostBrokerBridge.failed(ticket)){"host-broker-failed"}
    }
    @Synchronized fun snapshotBegin(operation:Long):Int {
        require(operation!=0L);pump()
        if(peers.values.any {!it.drained()})return 1
        return HostBrokerBridge.snapshotStatus(ticket,operation,0)
    }
    @Synchronized override fun close() {
        if(!stopped.compareAndSet(false,true))return
        peers.values.toList().forEach {it.close()};peers.clear();HostBrokerBridge.close(ticket)
    }
}

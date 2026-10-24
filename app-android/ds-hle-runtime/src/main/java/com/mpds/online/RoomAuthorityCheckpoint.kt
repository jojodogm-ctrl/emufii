package com.mpds.online

import java.io.Closeable
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.ArrayDeque
import java.util.Base64
import java.util.TreeMap
import java.util.concurrent.CompletableFuture

/** Guest half of authority-checkpoint.mjs v1. Only authenticated host bytes may
 * enter receive(). The native owner MUST be parked before park() is called.
 * Caller drives tick() on its network executor, including while frames stop.
 * No sockets or timers are owned here. Never enables a lobby capability itself.
 */
internal class RoomAuthorityCheckpoint(
    private val owner:Int, private val players:Int, private val epoch:String,
    private val incarnation:String, restore:Image?=null,
    private val timeoutMs:Long=30_000,
    private val now:()->Long={System.nanoTime()/1_000_000}
) : Closeable {
    data class Image(val version:Int,val epoch:String,val incarnation:String,val operation:String,
        val owner:Int,val players:Int,val cursor:Int,val queues:List<List<ByteArray>>)
    private data class Entry(val body:Map<String,Any?>,var sentAt:Long?=null)
    private val codec=RoomAuthorityWire(owner,players,epoch)
    private val outgoing=TreeMap<BigInteger,Entry>()
    private val incoming=TreeMap<BigInteger,Map<String,Any?>>()
    private val queues=List(players){ArrayDeque<ByteArray>()}
    private var next=BigInteger.ONE
    private var received=BigInteger.ZERO
    private var acked=BigInteger.ZERO
    private var ack=false
    private var cursor=0
    private var last=BigInteger.ZERO
    private var operation:String?=null
    private var parked:String?=null
    private var released:String?=null
    private var marker:BigInteger?=null
    private var releaseMarker:BigInteger?=null
    private var restoring=restore!=null
    private var phase=if(restoring) "restored" else "running"
    private var deadline=0L
    private var wait:CompletableFuture<Unit>?=null
    private var closed=false
    init {
        uint(incarnation);require(timeoutMs in 10..120_000)
        if(restore!=null){
            require(restore.version==1&&restore.epoch==epoch&&restore.owner==owner&&restore.players==players&&
                uint(restore.incarnation)<uint(incarnation)&&restore.cursor in 0 until players&&restore.queues.size==players)
            uint(restore.operation)
            for((peer,q)in restore.queues.withIndex()){
                require(q.size<=128&&(peer!=owner||q.isEmpty()))
                for(bytes in q){codec.validate(bytes,peer,owner);queues[peer].add(bytes.copyOf())}
            }
            cursor=restore.cursor
        }
    }
    companion object {
        private val max=BigInteger("18446744073709551615")
        private fun uint(s:String,zero:Boolean=false):BigInteger {
            require(Regex(if(zero) "(0|[1-9][0-9]{0,19})" else "[1-9][0-9]{0,19}").matches(s))
            return BigInteger(s).also { require(it<=max) }
        }
        private fun fields(m:Map<*,*>,vararg names:String){require(m.keys==names.toSet())}
        private fun encode(m:Map<String,Any?>)=RoomJson.encode(m).toByteArray(Charsets.UTF_8)
        @Suppress("UNCHECKED_CAST")
        private fun parse(bytes:ByteArray):Map<String,Any?>{
            require(bytes.size in 1..1024)
            val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            return RoomDirectory.parseStrictJson(text) as? Map<String,Any?> ?: error("authority-checkpoint-message")
        }
    }
    private fun ensure(){check(!closed){"authority-checkpoint-closed"}}
    private fun enqueue(body:Map<String,Any?>):BigInteger?{
        if(outgoing.size>=128)return null
        check(next<=max){"authority-checkpoint-sequence"}
        val seq=next;next+=BigInteger.ONE;outgoing[seq]=Entry(body);return seq
    }
    /** 1 transfers ownership, 0 is pressure without ownership, -1 is held/invalid. */
    @Synchronized fun send(target:Int,bytes:ByteArray):Int{
        if(closed||operation!=null||restoring)return -1
        return try{
            codec.validate(bytes,owner,target)
            if(enqueue(mapOf("type" to "data","origin" to owner,"target" to target,
                    "bytes" to Base64.getEncoder().encodeToString(bytes)))==null)0 else 1
        }catch(_:Exception){-1}
    }
    @Synchronized fun poll():RoomAuthorityWire.Packet?{
        ensure();if(operation!=null||restoring)return null
        repeat(players){val peer=cursor;cursor=(cursor+1)%players
            queues[peer].pollFirst()?.let { return RoomAuthorityWire.Packet(peer,it) }}
        return null
    }
    /** Output must go only to the authenticated host. Rejected admissions remain
     * journaled and are retransmitted; even an ACK can be safely sent again. */
    @Synchronized fun tick():List<ByteArray>{
        ensure()
        try{
            if(wait!=null&&now()>=deadline)error("authority-checkpoint-timeout")
            advance();val result=mutableListOf<ByteArray>()
            if(ack)result.add(encode(mapOf("v" to 1,"incarnation" to incarnation,"ack" to received.toString())))
            // Budget actual sends, not recent entries still awaiting an ACK.
            for((seq,e)in outgoing.entries.asSequence().filter { it.value.sentAt==null||now()-it.value.sentAt!!>=100 }.take(8)){
                e.sentAt=now();result.add(encode(mapOf("v" to 1,"incarnation" to incarnation,"seq" to seq.toString(),"body" to e.body)))
            }
            return result
        }catch(e:Exception){terminate(e);throw e}
    }
    /** Call only after the carrier accepts the exact tick() output. A newer
     * received sequence while send was pending must retain its newer ACK. */
    @Synchronized fun admitted(wire:ByteArray){
        if(closed)return
        val m=parse(wire)
        if(m["incarnation"]==incarnation&&m["ack"]==received.toString())ack=false
    }
    @Synchronized fun receive(wire:ByteArray):Boolean{
        if(closed)return false
        try{
            val m=parse(wire);require(m["v"]==1.0)
            val inc=m["incarnation"] as? String ?: error("authority-checkpoint-incarnation")
            if(uint(inc)<uint(incarnation))return false
            require(inc==incarnation)
            if(m.containsKey("ack")){
                fields(m,"v","incarnation","ack")
                val n=uint(m["ack"] as String,true);require(n<next)
                if(n>acked){acked=n;outgoing.headMap(n,true).clear()}
            }else{
                fields(m,"v","incarnation","seq","body")
                val n=uint(m["seq"] as String)
                if(n>received){
                    require(n-received<=BigInteger.valueOf(128))
                    @Suppress("UNCHECKED_CAST")
                    val body=m["body"] as? Map<String,Any?> ?: error("authority-checkpoint-message")
                    incoming.putIfAbsent(n,body)
                    while(incoming.containsKey(received+BigInteger.ONE)){
                        val following=received+BigInteger.ONE
                        deliver(incoming.remove(following)!!);received=following
                    }
                }
                ack=true
            }
            advance();return true
        }catch(e:Exception){terminate(e);return false}
    }
    private fun deliver(body:Map<String,Any?>){
        if(body["type"]=="data"){
            fields(body,"type","origin","target","bytes")
            require(parked==null||(phase=="released"&&released==operation))
            val origin=body["origin"] as? Double ?: error("authority-checkpoint-owner")
            require(origin==origin.toInt().toDouble()&&body["target"]==owner.toDouble())
            val text=body["bytes"] as String;require(text.length in 1..728)
            val bytes=Base64.getDecoder().decode(text)
            require(Base64.getEncoder().encodeToString(bytes)==text)
            codec.validate(bytes,origin.toInt(),owner)
            val q=queues[origin.toInt()];require(q.size<128);q.add(bytes)
        }else{
            fields(body,"type","operation");val op=body["operation"] as String
            require(uint(op)>last&&(operation==null||operation==op))
            if(body["type"]=="park"){require(parked==null);parked=op}
            else{require(body["type"]=="release"&&operation!=null&&phase!="parking"&&parked==op&&released==null);released=op}
        }
    }
    private fun advance(){
        val op=operation ?: return
        if(phase=="parking"){
            if(marker==null)marker=enqueue(mapOf("type" to "park","operation" to op))
            if(parked==op&&marker?.let { acked>=it }==true){phase="frozen";complete()}
        }else if(phase=="releasing"){
            if(releaseMarker==null)releaseMarker=enqueue(mapOf("type" to "release","operation" to op))
            if(released==op&&releaseMarker?.let { acked>=it }==true){phase="released";complete()}
        }
    }
    private fun complete(){val pending=wait;wait=null;pending?.complete(Unit)}
    @Synchronized fun park(op:String,pendingNative:List<RoomAuthorityWire.Packet> = emptyList()):CompletableFuture<Unit>{
        ensure();require(operation==null&&uint(op)>last&&(parked==null||parked==op))
        // Native frame is parked and its carrier is held before this handoff.
        // These packets predate this mesh's waiting data. Validate the complete
        // prefix before mutating any queue; preserve FIFO per source.
        // Native carrier holds 256 records plus one already polled by the
        // Kotlin worker under admission pressure. Per-source cap stays 128.
        require(pendingNative.size<=257)
        val prefix=List(players){mutableListOf<ByteArray>()}
        for(packet in pendingNative){
            codec.validate(packet.bytes,packet.source,owner)
            prefix[packet.source].add(packet.bytes.copyOf())
            require(prefix[packet.source].size+queues[packet.source].size<=128)
        }
        for(peer in 0 until players)for(bytes in prefix[peer].asReversed())queues[peer].addFirst(bytes)
        operation=op;phase="parking";deadline=now()+timeoutMs
        val pending=CompletableFuture<Unit>();wait=pending
        try{advance()}catch(e:Exception){terminate(e)};return pending
    }
    @Synchronized fun capture():Image{
        ensure();check(phase=="frozen"&&outgoing.isEmpty()&&incoming.isEmpty())
        return Image(1,epoch,incarnation,operation!!,owner,players,cursor,queues.map { q->q.map { it.copyOf() } })
    }
    @Synchronized fun release():CompletableFuture<Unit>{
        ensure();check(phase=="frozen");phase="releasing";deadline=now()+timeoutMs
        val pending=CompletableFuture<Unit>();wait=pending
        try{advance()}catch(e:Exception){terminate(e)};return pending
    }
    @Synchronized fun resume(){
        ensure();check(phase=="released");last=uint(operation!!);operation=null;parked=null;released=null
        marker=null;releaseMarker=null;restoring=false;phase="running"
    }
    @Synchronized fun phase():String=phase
    private fun terminate(error:Exception){
        if(closed)return;closed=true;phase="closed"
        val pending=wait;wait=null
        queues.forEach { q->q.forEach { it.fill(0) };q.clear() };incoming.clear();outgoing.clear()
        pending?.completeExceptionally(error)
    }
    @Synchronized override fun close(){terminate(IllegalStateException("authority-checkpoint-closed"))}
}

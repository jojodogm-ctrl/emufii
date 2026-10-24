package com.mpds.online

import java.io.Closeable
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Guest half of checkpoint-owner-channel.mjs. Only call receive from the
 * authenticated binary lane's one reader. Commands execute on the owner, not
 * the frame or network pump. Packet bytes never select code, paths or URLs. */
internal class RoomCheckpointRpc(
    private val command:(String,String,String,RoomCheckpointCapture.Parts?,String?,String?,Any?)->CompletableFuture<Any>,
    private val send:(ByteArray)->Unit,private val stop:()->Unit,
    private val current:()->Unit={},private val timeoutMs:Long=120000
):Closeable {
    private val closed=AtomicBoolean(false)
    private val timer=Executors.newSingleThreadScheduledExecutor { Thread(it,"wantap-checkpoint-rpc-timeout").apply { isDaemon=true } }
    private var deadline:java.util.concurrent.ScheduledFuture<*>?=null
    private var sequence=0L
    private var receiving:Upload?=null
    private val limits=intArrayOf(64*1024*1024,8*1024*1024,65536,512)
    private val actions=setOf("park","prepare","frozen","capture","release","released","resume","retire","materializeAuthority","attach","attachAuthorityRoutes")
    private class Upload(val request:Map<*,*>,val sizes:List<Int>,val hashes:List<String>) {
        val parts=ArrayList<ByteArray>();var index=0;var offset=0
        var buffer:ByteArray?=ByteArray(sizes[0])
        fun clear(){buffer?.fill(0);buffer=null;parts.forEach { it.fill(0) };parts.clear()}
    }
    init { require(timeoutMs in 10..120000) }
    private fun live(){check(!closed.get());current()}
    private fun fields(m:Map<*,*>,keys:Set<String>){require(m.keys==keys)}
    private fun number(v:Any?,max:Long):Long {
        val n=v as? Double ?: error("checkpoint-rpc-number")
        require(n.isFinite()&&n>=1&&n<=max.toDouble()&&n==n.toLong().toDouble());return n.toLong()
    }
    private fun uint(v:Any?):String {
        val s=v as? String ?: error("checkpoint-rpc-id")
        require(Regex("[1-9][0-9]{0,19}").matches(s)&&BigInteger(s).bitLength()<=64);return s
    }
    private fun hash(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}
    private fun json(value:Map<String,Any?>){
        live();val bytes=RoomJson.encode(value).toByteArray(Charsets.UTF_8);require(bytes.size<=4096)
        val packet=ByteArray(bytes.size+1);bytes.copyInto(packet,1)
        try{send(packet);live()}finally{packet.fill(0);bytes.fill(0)}
    }
    fun receive(packet:ByteArray){
        try{
            live();require(packet.isNotEmpty())
            if(packet[0]==1.toByte()){chunk(packet);return}
            require(packet[0]==0.toByte()&&packet.size in 2..4097)
            val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(packet,1,packet.size-1)).toString()
            val m=RoomDirectory.parseStrictJson(text) as? Map<*,*> ?: error("checkpoint-rpc-json")
            val id=number(m["id"],0xffffffffL)
            if(m["type"]=="end"){
                fields(m,setOf("type","id"));val upload=checkNotNull(receiving)
                require(id==sequence&&upload.index==4);receiving=null
                try{execute(upload.request,RoomCheckpointCapture.Parts(upload.parts[0],upload.parts[1],upload.parts[2],upload.parts[3]))}
                finally{upload.clear()}
            }else{
                require(m["type"]=="request"&&receiving==null&&deadline==null&&id==sequence+1)
                val action=m["action"] as? String ?: error("checkpoint-rpc-action");require(action in actions)
                uint(m["operation"]);uint(m["incarnation"])
                val extra=when(action){"materializeAuthority"->setOf("parts","capturedOperation","capturedIncarnation")
                    "attach"->setOf("route");"attachAuthorityRoutes"->setOf("routes");else->emptySet()}
                fields(m,setOf("type","id","action","operation","incarnation")+extra)
                sequence=id;deadline=timer.schedule({close()},timeoutMs,TimeUnit.MILLISECONDS)
                if(action=="materializeAuthority"){
                    uint(m["capturedOperation"]);uint(m["capturedIncarnation"])
                    val list=m["parts"] as? List<*> ?: error("checkpoint-rpc-parts");require(list.size==4)
                    val sizes=ArrayList<Int>();val hashes=ArrayList<String>()
                    for(i in 0..3){val d=list[i] as? Map<*,*> ?: error("checkpoint-rpc-descriptor")
                        fields(d,setOf("bytes","sha256"));sizes.add(number(d["bytes"],limits[i].toLong()).toInt())
                        val h=d["sha256"] as? String ?: error("checkpoint-rpc-hash");require(Regex("[a-f0-9]{64}").matches(h));hashes.add(h)}
                    receiving=Upload(m,sizes,hashes)
                }else execute(m,null)
            }
        }catch(e:Exception){close();throw e}
        finally{if(closed.get()){receiving?.clear();receiving=null}}
    }
    private fun chunk(packet:ByteArray){
        val u=checkNotNull(receiving);require(packet.size in 11..49162&&u.index<4)
        val b=ByteBuffer.wrap(packet);b.get()
        require((b.int.toLong() and 0xffffffffL)==sequence&&(b.get().toInt() and 255)==u.index&&
            (b.int.toLong() and 0xffffffffL)==u.offset.toLong())
        val target=checkNotNull(u.buffer);require(packet.size-10<=target.size-u.offset)
        packet.copyInto(target,u.offset,10);u.offset+=packet.size-10
        if(u.offset==target.size){require(hash(target)==u.hashes[u.index]);live()
            u.parts.add(target);u.index++;u.offset=0;u.buffer=if(u.index<4)ByteArray(u.sizes[u.index])else null}
    }
    private fun execute(m:Map<*,*>,parts:RoomCheckpointCapture.Parts?){
        live();val action=m["action"] as String
        val answer=command(action,m["operation"] as String,m["incarnation"] as String,parts,
            m["capturedOperation"] as? String,m["capturedIncarnation"] as? String,
            if(action=="attachAuthorityRoutes")m["routes"]else m["route"]).get(timeoutMs,TimeUnit.MILLISECONDS)
        if(action=="capture"){
            val image=answer as? RoomCheckpointCapture.Parts ?: error("checkpoint-rpc-owner-result")
            try{
                live();val arrays=listOf(image.core,image.sdk,image.transport,image.frontend)
                arrays.forEachIndexed { i,b->require(b.isNotEmpty()&&b.size<=limits[i]) }
                json(mapOf("type" to "image","id" to sequence,"parts" to arrays.map { mapOf("bytes" to it.size,"sha256" to hash(it)) }))
                arrays.forEachIndexed { i,bytes->
                    var offset=0
                    while(offset<bytes.size){live();val size=minOf(49152,bytes.size-offset)
                        val packet=ByteBuffer.allocate(size+10).put(1.toByte()).putInt(sequence.toInt()).put(i.toByte()).putInt(offset)
                            .put(bytes,offset,size).array()
                        try{send(packet)}finally{packet.fill(0)}
                        offset+=size;Thread.sleep(5)
                    }
                }
                json(mapOf("type" to "end","id" to sequence))
            }finally{image.clear()}
        }else{
            live();require(answer==true||(action in setOf("frozen","released")&&answer==false))
            json(mapOf("type" to "result","id" to sequence,"value" to answer))
        }
        live();deadline?.cancel(false);deadline=null
    }
    override fun close(){
        if(!closed.compareAndSet(false,true))return
        timer.shutdownNow();runCatching { stop() }
        // The reader owns upload arrays. Closing its lane wakes read/command
        // waits; its finally calls dispose after it can no longer use them.
    }
    fun dispose(){close();receiving?.clear();receiving=null}
}

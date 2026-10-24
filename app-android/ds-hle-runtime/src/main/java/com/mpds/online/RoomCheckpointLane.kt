package com.mpds.online

import java.io.Closeable
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Separate, binding-specific authenticated binary connection. Never sends
 * snapshot chunks through the small lobby/control queue. */
internal interface RoomCheckpointPort:Closeable { val ready:CompletableFuture<Unit> }
internal class RoomCheckpointLane private constructor(
    private val round:RoomGuestSession.Round,private val admission:RoomInvitation.Admission,
    private val binding:String,private val owner:RoomCheckpointOwner,
    private val current:()->Unit,private val onClosed:(String)->Unit
):RoomCheckpointPort {
    internal class Keys private constructor(val channel:RoomInvitation.Channel):Closeable {
        override fun close(){channel.capability.fill(0);channel.authenticationKey?.fill(0)}
        companion object {
            fun derive(c:RoomInvitation.Config,transport:ByteArray,binding:String,now:Long=System.currentTimeMillis()):Keys {
                require(transport.size==32&&transport.any { it!=0.toByte() }&&Regex("[a-f0-9]{64}").matches(binding))
                require(c.seat in 1..7&&c.expiresAtUnixMs>now)
                val domain="nifi2-room-checkpoint"
                val salt=RoomJson.encode(listOf("$domain/v1",c.room,c.seat,c.invitationId,c.ttlSeconds,c.expiresAtUnixMs,binding)).toByteArray(Charsets.UTF_8)
                fun mac(key:ByteArray,data:ByteArray)=Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key,"HmacSHA256"));doFinal(data) }
                val prk=mac(salt,transport);val owned=ArrayList<ByteArray>()
                fun derive(purpose:String,n:Int):ByteArray {
                    val block=mac(prk,"$domain/$purpose/v1".toByteArray(Charsets.UTF_8)+byteArrayOf(1))
                    return try{block.copyOf(n).also { owned.add(it) }}finally{block.fill(0)}
                }
                try{
                    val key=derive("relay",32);val proof=derive("local-proof",32)
                    val route="K"+Base64.getUrlEncoder().withoutPadding().encodeToString(derive("route",11)).take(14)
                    val invitation=derive("invitation",16).joinToString(""){"%02x".format(it)}
                    require(route!=c.room&&invitation!=c.invitationId)
                    val result=Keys(RoomInvitation.Channel(c.route(route,invitation),key,proof))
                    owned.remove(key);owned.remove(proof);return result
                }finally{prk.fill(0);salt.fill(0);owned.forEach { it.fill(0) }}
            }
        }
    }
    override val ready=CompletableFuture<Unit>()
    private val closed=AtomicBoolean(false)
    private val maintenance=Executors.newSingleThreadScheduledExecutor { Thread(it,"wantap-checkpoint-heartbeat").apply { isDaemon=true } }
    private val writeLock=Any()
    @Volatile private var gateway:RoomGatewayChannel?=null
    @Volatile private var keys:Keys?=null
    private val wire=RoomAssetWire({bytes->ensure();gateway?.send(bytes)?.get(10,TimeUnit.SECONDS)==true},::ensure)
    private val dataRate=Rate(256.0,256.0)
    private val controlRate=Rate(32.0,16.0)
    private val rpc=RoomCheckpointRpc(owner::command,::send,{stop("checkpoint-rpc-closed")},::ensure)
    private class Rate(private val capacity:Double,private val perSecond:Double){
        private var tokens=capacity;private var at=System.nanoTime()
        fun take(){val now=System.nanoTime();tokens=minOf(capacity,tokens+maxOf(0L,now-at)/1e9*perSecond);at=now
            check(tokens>=1);tokens--}
    }
    companion object {
        fun open(round:RoomGuestSession.Round,admission:RoomInvitation.Admission,binding:String,owner:RoomCheckpointOwner,
            current:()->Unit,onClosed:(String)->Unit):RoomCheckpointLane =
            RoomCheckpointLane(round,admission,binding,owner,current,onClosed).also { it.start() }
    }
    private fun ensure(){check(!closed.get());round.checkCurrent();check(admission.isCurrent());current()}
    private fun frame():Pair<Int,ByteArray>{
        val header=ByteBuffer.wrap(wire.read(5));val type=header.get().toInt() and 255;val size=header.int
        require(type==17&&size in 1..65531||type in listOf(16,18)&&size==1)
        (if(type==17)dataRate else controlRate).take();return type to wire.read(size)
    }
    private fun send(bytes:ByteArray)=synchronized(writeLock){
        ensure();require(bytes.size in 1..65531);wire.send(RoomAssetWire.TrustedFrame(17,65531),bytes);ensure()
    }
    private fun start(){
        Thread({
            var proof:ByteArray?=null
            try{
                ensure();owner.bind(binding).get(30,TimeUnit.SECONDS);ensure()
                val derived=Keys.derive(admission.config,admission.transportKey,binding);keys=derived
                if(closed.get()){derived.close();ensure()}
                proof=derived.channel.authenticationKey!!.copyOf()
                val opened=RoomGatewayChannel.openCheckpointOwner(round,admission,derived,wire::push,{stop("checkpoint-disconnected")})
                gateway=opened;if(closed.get()){opened.close();ensure()}
                maintenance.scheduleWithFixedDelay({try{ensure();wire.writable()}catch(_:Exception){stop("checkpoint-retired")}},50,50,TimeUnit.MILLISECONDS)
                opened.ready.get(30,TimeUnit.SECONDS);ensure();wire.authenticateReceiver(proof,diagnostics=true)
                wire.send(RoomAssetWire.TrustedFrame(16,1),byteArrayOf(1))
                val confirm=frame();require(confirm.first==16&&confirm.second.contentEquals(byteArrayOf(1)))
                proof.fill(0);proof=null;ensure();ready.complete(Unit)
                maintenance.scheduleWithFixedDelay({
                    try{synchronized(writeLock){ensure();wire.send(RoomAssetWire.TrustedFrame(18,1),byteArrayOf(0))}}
                    catch(_:Exception){stop("checkpoint-heartbeat")}
                },2,2,TimeUnit.SECONDS)
                while(!closed.get()){
                    val (type,bytes)=frame()
                    try{ensure();if(type==18)require(bytes[0]==0.toByte())else{require(type==17);rpc.receive(bytes)}}
                    finally{bytes.fill(0)}
                }
            }catch(_:Exception){stop("checkpoint-connection")}
            finally{proof?.fill(0);rpc.dispose()}
        },"wantap-checkpoint-reader").apply { isDaemon=true;start() }
    }
    private fun stop(reason:String){
        if(!closed.compareAndSet(false,true))return
        wire.close("checkpoint-closed");gateway?.close();maintenance.shutdownNow();keys?.close();owner.close();rpc.close()
        ready.completeExceptionally(IllegalStateException(reason));runCatching { onClosed(reason) }
    }
    override fun close()=stop("checkpoint-stopped")
}

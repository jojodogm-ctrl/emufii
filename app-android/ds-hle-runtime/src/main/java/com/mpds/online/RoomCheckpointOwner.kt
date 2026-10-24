package com.mpds.online

import java.io.Closeable
import java.math.BigInteger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** One bound Android owner. Runs native commands on a separate coordinator,
 * never on the network pump or UI thread. The authenticated room RPC supplies
 * commands; this class never selects a protocol or advertises capability. */
internal class RoomCheckpointOwner(
    private val original:RoomInvitation.Config,private val room:Map<String,Any?>,
    initialTicket:Long,initialNetwork:Network,
    private val openReplacement:(Long,RoomCheckpointRoutes.Route,RoomGameNetwork.CheckpointConfig)->Network,
    private val promote:(Long,Network)->Unit,private val current:()->Unit,
    private val onFailure:()->Unit,private val native:Native=Engine,
    private val timeoutMs:Long=30_000,
    private val onNotice:(RoomGameNotices.Checkpoint)->Unit={}
):Closeable {
    interface Network:Closeable {
        val ready:CompletableFuture<Unit>
        fun parkAuthorityAfterNativeHeld(operation:String):CompletableFuture<Unit>
        fun captureAuthority():CompletableFuture<RoomAuthorityCheckpoint.Image>
        fun releaseAuthority():CompletableFuture<Unit>
        fun authorityReleasedMask():CompletableFuture<Int>
        fun resumeAuthority():CompletableFuture<Unit>
        fun attachRestoredAuthority(route:RoomCheckpointRoutes.Route):CompletableFuture<Unit>
    }
    interface Native {
        fun status(ticket:Long,action:Int,op:String,inc:String,mask:Int=0,accounted:Boolean=false):Int
        fun read(ticket:Long,op:String,inc:String,component:Int,offset:Int):ByteArray?
        fun upload(ticket:Long,op:String,fresh:String,captured:String,c:Int,total:Int,offset:Int,bytes:ByteArray):Int
        fun materialize(ticket:Long,op:String,fresh:String,captured:String,endpoint:String,frame:Long):Int
        fun newCarrier(ticket:Long,op:String,fresh:String):Long
        fun closeCarrier(ticket:Long)
    }
    private object Engine:Native {
        override fun status(ticket:Long,action:Int,op:String,inc:String,mask:Int,accounted:Boolean)=RoomEngine.nativeCheckpointStatus(ticket,action,op,inc,mask,accounted)
        override fun read(ticket:Long,op:String,inc:String,component:Int,offset:Int)=RoomEngine.nativeCheckpointPart(ticket,op,inc,component,offset)
        override fun upload(ticket:Long,op:String,fresh:String,captured:String,c:Int,total:Int,offset:Int,bytes:ByteArray)=RoomEngine.nativeCheckpointUpload(ticket,op,fresh,captured,c,total,offset,bytes)
        override fun materialize(ticket:Long,op:String,fresh:String,captured:String,endpoint:String,frame:Long)=RoomEngine.nativeCheckpointMaterialize(ticket,op,fresh,captured,endpoint,frame)
        override fun newCarrier(ticket:Long,op:String,fresh:String)=RoomEngine.nativeCheckpointNewCarrier(ticket,op,fresh)
        override fun closeCarrier(ticket:Long)=RoomEngine.nativeCloseRoomCarrier(ticket)
    }
    private val worker=Executors.newSingleThreadExecutor { Thread(it,"wantap-checkpoint-owner").apply { isDaemon=true } }
    private val timer=Executors.newSingleThreadScheduledExecutor { Thread(it,"wantap-checkpoint-timeout").apply { isDaemon=true } }
    private val closed=AtomicBoolean(false)
    private val busy=AtomicBoolean(false)
    private val pending=AtomicReference<CompletableFuture<*>?>(null)
    @Volatile private var ticket=initialTicket
    @Volatile private var network=initialNetwork
    @Volatile private var replacement:Network?=null
    @Volatile private var replacementTicket=0L
    @Volatile var retiring=false;private set
    private val players=(room["players"] as Double).toInt()
    private val seat=(room["seat"] as Double).toInt()
    private val epoch=room["epoch"] as String
    private var binding:String?=null
    private var phase="running"
    private var incarnation="1"
    private var operation:String?=null
    private var lastOperation=BigInteger.ZERO
    private var barrier:CompletableFuture<Unit>?=null
    private var staged:RoomCheckpointRestore.Image?=null
    private var fresh:String?=null
    private var sdkRoom:String?=null
    private var restoring=false
    private fun notice(state:RoomGameNotices.Checkpoint){runCatching { live();onNotice(state) }}
    init {
        require(initialTicket>0&&players in 2..8&&seat in 2..players&&original.seat==seat-1&&timeoutMs in 10..120000)
        require(room["game"] in listOf("mkds","nsmb")&&(room["game"]!="nsmb"||players==2));uint(epoch)
    }
    private fun uint(s:String):BigInteger {
        require(Regex("[1-9][0-9]{0,19}").matches(s));return BigInteger(s).also { require(it.bitLength()<=64) }
    }
    private fun live(){check(!closed.get());current()}
    private fun <T> await(f:CompletableFuture<T>):T {live();return f.get(timeoutMs,TimeUnit.MILLISECONDS).also { live() }}
    private fun barrierDone():Boolean {
        val b=checkNotNull(barrier);if(!b.isDone)return false;await(b);return true
    }
    private fun ok(value:Int){check(value==0){"checkpoint-native-status-$value"}}
    private fun poll(action:Int,op:String,inc:String):Boolean {
        val status=native.status(ticket,action,op,inc);live();check(status in 0..1);return status==0
    }
    private fun exact(op:String,inc:String){check(operation==op&&incarnation==inc)}
    private fun <T> submit(action:()->T):CompletableFuture<T>{
        val result=CompletableFuture<T>()
        if(closed.get()||!busy.compareAndSet(false,true)){
            result.completeExceptionally(IllegalStateException("checkpoint-owner-unavailable"));return result
        }
        pending.set(result)
        try {
            val deadline=timer.schedule({
                if(result.completeExceptionally(IllegalStateException("checkpoint-owner-timeout")))fault()
            },timeoutMs,TimeUnit.MILLISECONDS)
            worker.execute {
                try {
                    live();val answer=action()
                    try{live()}catch(e:Exception){if(answer is RoomCheckpointCapture.Parts)answer.clear();throw e}
                    deadline.cancel(false);busy.set(false)
                    pending.compareAndSet(result,null);result.complete(answer)
                }catch(e:Exception){deadline.cancel(false);busy.set(false);fault();result.completeExceptionally(e)}
                finally{pending.compareAndSet(result,null)}
            }
        }catch(e:Exception){fault();result.completeExceptionally(e)}
        return result
    }
    fun bind(sha256:String)=submit {
        check(binding==null&&phase=="running");require(Regex("[a-f0-9]{64}").matches(sha256));binding=sha256;true
    }
    fun command(action:String,op:String,inc:String,parts:RoomCheckpointCapture.Parts?=null,
        capturedOperation:String?=null,capturedIncarnation:String?=null,route:Any?=null):CompletableFuture<Any> = submit {
        uint(op);uint(inc);check(binding!=null)
        when(action){
            "park"->{check(phase=="running"&&inc==incarnation&&uint(op)>lastOperation)
                ok(native.status(ticket,0,op,inc));live();operation=op;phase="parked";notice(RoomGameNotices.Checkpoint.PAUSING);true}
            "prepare"->{exact(op,inc);check(phase=="parked");ok(native.status(ticket,1,op,inc));live()
                barrier=network.parkAuthorityAfterNativeHeld(op);phase="preparing";true}
            "frozen"->{exact(op,inc);check(phase in listOf("preparing","frozen"))
                val done=poll(2,op,inc)&&barrierDone();if(done){phase="frozen"
                    notice(if(restoring)RoomGameNotices.Checkpoint.RESTORING else RoomGameNotices.Checkpoint.PAUSED)};done}
            "capture"->{exact(op,inc);check(phase=="frozen"&&poll(2,op,inc)&&barrierDone())
                notice(RoomGameNotices.Checkpoint.CAPTURING)
                ok(native.status(ticket,3,op,inc));live();val image=await(network.captureAuthority())
                try{RoomCheckpointCapture.capture(binding!!,image,{c,o->native.read(ticket,op,inc,c,o)},::live)
                    .also{notice(RoomGameNotices.Checkpoint.CAPTURED)}}
                finally{image.queues.flatten().forEach { it.fill(0) }}}
            "release"->{exact(op,inc);check(phase=="frozen");ok(native.status(ticket,4,op,inc));live()
                barrier=network.releaseAuthority();phase="releasing";notice(RoomGameNotices.Checkpoint.RESUMING);true}
            "released"->{exact(op,inc);check(phase in listOf("releasing","released"))
                val done=poll(5,op,inc)&&barrierDone();if(done)phase="released";done}
            "resume"->{exact(op,inc);check(phase=="released"&&poll(5,op,inc)&&barrierDone())
                val mask=await(network.authorityReleasedMask());check(mask==(1 shl players)-1)
                await(network.resumeAuthority());ok(native.status(ticket,6,op,inc,mask,true));live()
                if(retiring){promote(ticket,network);live();replacement=null;replacementTicket=0;retiring=false}
                lastOperation=uint(op);operation=null;barrier=null;phase="running";restoring=false
                notice(RoomGameNotices.Checkpoint.RUNNING);true}
            "materializeAuthority"->{check(phase=="frozen"&&operation==op&&staged==null&&uint(inc)>uint(incarnation))
                val savedOp=checkNotNull(capturedOperation);val savedInc=checkNotNull(capturedIncarnation)
                require(uint(savedInc)<=uint(incarnation))
                restoring=true;notice(RoomGameNotices.Checkpoint.RESTORING)
                val image=RoomCheckpointRestore.decode(checkNotNull(parts),
                    RoomCheckpointRestore.Binding(binding!!,epoch,players,seat,savedOp,savedInc),inc,::live)
                try{
                    for((c,bytes)in image.native.withIndex()){
                        var offset=0
                        do {live();val chunk=bytes.copyOfRange(offset,minOf(offset+65536,bytes.size))
                            try{ok(native.upload(ticket,op,inc,savedInc,c,bytes.size,offset,chunk))}finally{chunk.fill(0)}
                            offset+=chunk.size
                        }while(offset<bytes.size)
                    }
                    live();ok(native.materialize(ticket,op,inc,savedInc,image.endpoint,image.frame));live()
                    image.native.forEach { it.fill(0) };staged=image;fresh=inc;true
                }catch(e:Exception){image.clear();throw e}}
            "retire"->{exact(op,inc);check(phase=="frozen"&&staged!=null);retiring=true
                ok(native.status(ticket,8,op,inc));live();network.close();phase="retired";true}
            "attach"->{check(phase=="retired"&&operation==op&&fresh==inc&&replacement==null)
                val validated=RoomCheckpointRoutes.validate(route,original)
                try {
                    replacementTicket=native.newCarrier(ticket,op,inc);live();check(replacementTicket>0)
                    val opened=openReplacement(replacementTicket,validated,RoomGameNetwork.CheckpointConfig(inc,staged!!.authority))
                    replacement=opened
                    if(closed.get()){opened.close();live()}
                    await(opened.ready);ok(native.status(replacementTicket,10,op,inc));live()
                    sdkRoom=validated.channel.config.room;phase="sdk-attached";true
                }catch(e:Exception){validated.close();throw e}}
            "attachAuthorityRoutes"->{check(phase=="sdk-attached"&&operation==op&&fresh==inc)
                val validated=RoomCheckpointRoutes.validateAuthority(route,original,room,sdkRoom!!)
                val next=checkNotNull(replacement)
                await(next.attachRestoredAuthority(validated))
                ok(native.status(replacementTicket,9,op,inc,((1 shl players)-1) xor (1 shl (seat-1))));live()
                while(true){live();val status=native.status(replacementTicket,2,op,inc);check(status in 0..1)
                    if(status==0)break;Thread.sleep(10)}
                live();ticket=replacementTicket;network=next;incarnation=inc
                barrier=next.parkAuthorityAfterNativeHeld(op);phase="preparing";staged?.clear();staged=null;fresh=null;true}
            else->error("checkpoint-owner-command")
        }
    }
    private fun fault(){notice(RoomGameNotices.Checkpoint.FAILED);close();runCatching { onFailure() }}
    override fun close(){
        if(!closed.compareAndSet(false,true))return
        val stopped=pending.getAndSet(null)
        runCatching { native.closeCarrier(ticket) };if(replacementTicket>0)runCatching { native.closeCarrier(replacementTicket) }
        runCatching { network.close() };runCatching { replacement?.close() }
        timer.shutdownNow()
        // The worker owns staging bytes; do not erase them concurrently with
        // an in-progress decoder/upload. Closed carrier wakes pending native IO.
        runCatching { worker.execute { staged?.clear();staged=null } };worker.shutdown()
        stopped?.completeExceptionally(IllegalStateException("checkpoint-owner-closed"))
    }
}

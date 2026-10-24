package com.mpds.online

import java.util.ArrayDeque
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** P1 owns ONE console. A separate real native SDK broker serves the guest
 * carriers; authenticated authority envelopes are forwarded as opaque bytes. */
internal class RoomHostGameNetwork(
    private val round:RoomGuestSession.Round,
    private val seats:List<RoomInvitation.Admission>,
    private val ticket:Long,
    private val onFailure:(String)->Unit
) : RoomNativeNetwork {
    override val ready=CompletableFuture<Unit>()
    private val closed=AtomicBoolean(false)
    private val broker=RoomHostBroker(seats.size, sdkBlocks=BuildConfig.SDK_BLOCKS &&
        seats.first().room["game"]=="experimental" && seats.first().room["guestBoot"]=="cartridge")
    private val carriers=mutableListOf<RoomGatewayChannel>()
    private val authorities=mutableMapOf<Int,RoomGatewayChannel>()
    private val inputs=RoomControlFrames()
    private val local=broker.attach(inputs::offer)
    private val worker=Executors.newSingleThreadScheduledExecutor {Thread(it,"hle-host-network").apply {isDaemon=true}}
    private val stateLock=Any()
    private val states=ArrayDeque<Pair<Int,ByteArray>>()
    private val codec=RoomAuthorityWire(0,seats.size,seats[0].room["epoch"] as String)
    private var incoming:ByteArray?=null
    private var outgoing:ByteArray?=null
    init { require(ticket>0&&RoomInvitation.number(seats[0].room["seat"])==1L) }
    private fun current(){check(!closed.get());round.checkCurrent();check(seats.all {it.isCurrent()})}
    fun start():RoomHostGameNetwork {
        try {
            current()
            val pending=mutableListOf<CompletableFuture<Unit>>()
            for(index in 1 until seats.size) {
                val admission=seats[index];var carrier:RoomGatewayChannel?=null
                val peer=broker.attach {bytes->current();carrier?.send(bytes)?.get(5,TimeUnit.SECONDS)==true}
                val opened=RoomGatewayChannel.openHost(round,admission,admission.sdk,true,
                    {bytes->try{current();peer.receive(bytes)}catch(_:Exception){fail("host-control-input");false}},
                    {fail("host-control-closed")})
                carrier=opened;carriers.add(opened);pending.add(opened.ready)
                admission.authority?.let {route->
                    val authority=RoomGatewayChannel.openHost(round,admission,route,false,
                        {bytes->try{current();authorityInput(index,bytes);true}catch(_:Exception){false}},
                        {fail("host-authority-closed")})
                    authorities[index]=authority;carriers.add(authority);pending.add(authority.ready)
                }
            }
            CompletableFuture.allOf(*pending.toTypedArray()).whenComplete {_,error->
                if(error!=null)fail("host-network-admission") else try{current();ready.complete(Unit)}
                    catch(_:Exception){fail("host-network-retired")}
            }
            // Pump before native boot: real guest SDK joins may already be pending.
            worker.scheduleWithFixedDelay({try{tick()}catch(_:Exception){fail("host-network-pump")}},0,2,TimeUnit.MILLISECONDS)
            return this
        } catch(e:Throwable){close();throw e}
    }
    private fun authorityInput(source:Int,bytes:ByteArray) {
        require(bytes.size in 1..1024)
        val value=RoomDirectory.parseStrictJson(bytes.toString(Charsets.UTF_8)) as? Map<*,*> ?: error("authority-json")
        require(value.keys==setOf("target","bytes"))
        val target=RoomInvitation.number(value["target"],0,(seats.size-1).toLong()).toInt()
        val encoded=value["bytes"] as? String ?: error("authority-bytes")
        require(encoded.length in 1..728)
        val payload=Base64.getDecoder().decode(encoded)
        require(Base64.getEncoder().encodeToString(payload)==encoded)
        codec.validate(payload,source,target)
        synchronized(stateLock){if(states.size==256)states.removeFirst();states.addLast(target to bytes.copyOf())}
    }
    private fun tick() {
        current();broker.pump()
        for(index in 0 until 64) {
            if(incoming==null)incoming=inputs.poll()
            val bytes=incoming ?: break
            when(RoomEngine.nativeDeliverRoomPacket(ticket,0,0,bytes)) {
                1->incoming=null
                0->break
                else->error("host-native-control")
            }
        }
        for(index in 0 until 64) {
            val state=synchronized(stateLock){states.pollFirst()} ?: break
            if(state.first==0){val packet=codec.decode(state.second)
                check(RoomEngine.nativeDeliverRoomPacket(ticket,1,packet.source,packet.bytes)>=0)}
            else authorities.getValue(state.first).send(state.second).get(5,TimeUnit.SECONDS)
        }
        for(index in 0 until 64) {
            if(outgoing==null)outgoing=RoomEngine.nativeTakeRoomPacket(ticket)
            val bytes=outgoing ?: break
            require(bytes.size>=3)
            val lane=bytes[0].toInt() and 255;val target=bytes[1].toInt() and 255
            val payload=bytes.copyOfRange(2,bytes.size)
            if(lane==0){if(!local.receive(RoomControlFrames.encode(payload)))break}
            else {require(lane==1&&target in 1 until seats.size)
                authorities.getValue(target).send(codec.encode(target,payload)).get(5,TimeUnit.SECONDS)}
            outgoing=null;broker.pump()
        }
    }
    private fun fail(reason:String){if(!closed.get()){close();try{onFailure(reason)}catch(_:Exception){}}}
    override fun close() {
        if(!closed.compareAndSet(false,true))return
        ready.completeExceptionally(IllegalStateException("host-network-closed"))
        runCatching {RoomEngine.nativeCloseRoomCarrier(ticket)}
        carriers.forEach {it.close()};worker.shutdownNow();broker.close();inputs.close()
        synchronized(stateLock){states.clear()}
    }
}

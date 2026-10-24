package com.mpds.online

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Base64

/** Validate durable bytes BEFORE parking a live game. Expected identity comes
 * from the authenticated room manifest, never from the uploaded envelope.
 * Decoding does not attach routes, retire an owner, or release the cohort. */
internal object RoomCheckpointRestore {
    data class Binding(val sha256:String,val epoch:String,val players:Int,val seat:Int,
        val operation:String,val incarnation:String)
    data class Image(val native:List<ByteArray>,val authority:RoomAuthorityCheckpoint.Image,
        val endpoint:String,val frame:Long) {
        fun clear(){native.forEach { it.fill(0) };authority.queues.flatten().forEach { it.fill(0) }}
    }
    private fun uint(s:String):BigInteger {
        require(Regex("[1-9][0-9]{0,19}").matches(s))
        return BigInteger(s).also { require(it.bitLength()<=64) }
    }
    private fun fields(v:Any?,vararg keys:String):Map<*,*> {
        val m=v as? Map<*,*> ?: error("checkpoint-object")
        require(m.keys==keys.toSet());return m
    }
    private fun json(b:ByteArray):Any?=RoomDirectory.parseStrictJson(Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(b)).toString())
    private fun hash(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}
    private fun integer(v:Any?,max:Long):Long {
        val n=v as? Double ?: error("checkpoint-integer")
        require(n.isFinite()&&n>=0&&n<=max.toDouble()&&n==n.toLong().toDouble());return n.toLong()
    }
    fun decode(parts:RoomCheckpointCapture.Parts,expected:Binding,freshIncarnation:String,
        current:()->Unit={}):Image {
        require(Regex("[a-f0-9]{64}").matches(expected.sha256))
        uint(expected.epoch);uint(expected.operation)
        require(uint(freshIncarnation)>uint(expected.incarnation))
        require(expected.players in 2..8&&expected.seat in 2..expected.players)
        val owned=mutableListOf<ByteArray>()
        fun copy(b:ByteArray)=b.copyOf().also { owned.add(it) }
        try {
            current()
            require(parts.core.size in 1..32*1024*1024&&parts.sdk.size in 16..8*1024*1024&&
                parts.transport.size in 1..65536&&parts.frontend.size in 1..512)
            // Own all bytes across cancellation callbacks and native staging.
            val core=copy(parts.core);val durable=copy(parts.sdk)
            val transport=copy(parts.transport);val frontend=copy(parts.frontend)
            val h=ByteBuffer.wrap(durable).order(ByteOrder.LITTLE_ENDIAN)
            require(h.int==0x31414357&&h.getInt(12)==0) // WCA1
            val ns=h.getInt(4);val es=h.getInt(8)
            require(ns>0&&es in 1..768*1024&&16L+ns+es==durable.size.toLong())
            val packed=durable.copyOfRange(16,16+ns).also { owned.add(it) }
            val envelopeBytes=durable.copyOfRange(16+ns,durable.size).also { owned.add(it) }
            val envelope=fields(json(envelopeBytes),"version","binding","hashes","authority")
            require(envelope["version"]=="WAT1")
            val binding=fields(envelope["binding"],"bindingSha256","epoch","players","seat","operation","incarnation")
            require(binding==mapOf("bindingSha256" to expected.sha256,"epoch" to expected.epoch,
                "players" to expected.players.toDouble(),"seat" to expected.seat.toDouble(),
                "operation" to expected.operation,"incarnation" to expected.incarnation))
            val hashes=fields(envelope["hashes"],"core","sdk","transport","frontend")
            for((name,bytes)in mapOf("core" to core,"sdk" to packed,"transport" to transport,"frontend" to frontend)){
                require(hashes[name]==hash(bytes)){"checkpoint-component-hash"};current()
            }
            val a=fields(envelope["authority"],"version","epoch","incarnation","operation","owner","players","cursor","queues")
            require(a["version"]==1.0&&a["epoch"]==expected.epoch&&a["incarnation"]==expected.incarnation&&
                a["operation"]==expected.operation&&a["owner"]==(expected.seat-1).toDouble()&&a["players"]==expected.players.toDouble())
            val cursor=integer(a["cursor"],(expected.players-1).toLong()).toInt()
            val qs=a["queues"] as? List<*> ?: error("checkpoint-queues")
            require(qs.size==expected.players)
            val codec=RoomAuthorityWire(expected.seat-1,expected.players,expected.epoch)
            val queues=qs.mapIndexed { peer,value->
                val q=value as? List<*> ?: error("checkpoint-queue")
                require(q.size<=128&&(peer!=expected.seat-1||q.isEmpty()))
                q.map { encoded->
                    val s=encoded as? String ?: error("checkpoint-packet")
                    require(s.length in 1..728)
                    val bytes=Base64.getDecoder().decode(s).also { owned.add(it) }
                    require(bytes.size<=544&&Base64.getEncoder().encodeToString(bytes)==s)
                    codec.validate(bytes,peer,expected.seat-1);bytes
                }
            }
            require(packed.size>=20)
            val p=ByteBuffer.wrap(packed).order(ByteOrder.LITTLE_ENDIAN)
            require(p.int==0x324b4357) // WCK2: authority and MPDS services cannot coexist.
            val lengths=IntArray(4){p.int}
            require(lengths[0] in 1..65536&&lengths[1]==0&&lengths[2] in 1..256&&lengths[3] in 1..7*1024*1024)
            require(20L+lengths.sumOf { it.toLong() }==packed.size.toLong())
            var at=20
            val sections=lengths.map { size->packed.copyOfRange(at,at+size).also { at+=size;owned.add(it) } }
            val f=fields(json(frontend),"version","policy","endpoint","frame")
            require(f["version"]==1.0&&f["policy"]=="resample-input-clear-output")
            val endpoint=f["endpoint"] as? String ?: error("checkpoint-endpoint");uint(endpoint)
            val frame=integer(f["frame"],4294967295L)
            val native=listOf(core,sections[0],sections[1],sections[3],sections[2],transport)
            val authority=RoomAuthorityCheckpoint.Image(1,expected.epoch,expected.incarnation,expected.operation,
                expected.seat-1,expected.players,cursor,queues)
            current()
            val retained=native+queues.flatten()
            owned.filter { b->retained.none { it===b } }.forEach { it.fill(0) }
            return Image(native,authority,endpoint,frame)
        }catch(e:Exception){owned.forEach { it.fill(0) };throw e}
    }
}

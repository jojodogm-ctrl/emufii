package com.mpds.online

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.util.ArrayDeque
import java.util.Base64

/** Existing desktop/browser N2 control framing; no game-specific payload edits. */
internal class RoomControlFrames {
    private var partial=ByteArray(1028)
    private var used=0
    private var need=4
    private val queue=ArrayDeque<ByteArray>()
    private var closed=false
    @Synchronized fun offer(bytes:ByteArray):Boolean {
        check(!closed);require(bytes.size in 1..65536)
        // A refused carrier chunk must have NO partial effects: the reliable
        // relay retries that entire chunk until we acknowledge local admission.
        val next=partial.copyOf();var fill=used;var extent=need;var at=0
        val completed=mutableListOf<ByteArray>()
        while(at<bytes.size) {
            val n=minOf(extent-fill,bytes.size-at);bytes.copyInto(next,fill,at,at+n);fill+=n;at+=n
            if(fill!=extent)continue
            if(extent==4) {
                val size=(next[2].toInt() and 255) or ((next[3].toInt() and 255) shl 8)
                require(next[0]==78.toByte() && next[1]==50.toByte() && size in 1..1024)
                extent=size+4
            } else {
                if(queue.size+completed.size>=128)return false
                completed.add(next.copyOfRange(4,extent));fill=0;extent=4
            }
        }
        partial=next;used=fill;need=extent;completed.forEach(queue::addLast);return true
    }
    @Synchronized fun poll():ByteArray? { check(!closed);return queue.pollFirst() }
    @Synchronized fun drained() = !closed && used==0 && queue.isEmpty()
    @Synchronized fun close() { closed=true;partial.fill(0);queue.clear();used=0;need=4 }
    companion object {
        fun encode(bytes:ByteArray):ByteArray {
            require(bytes.size in 1..1024)
            return byteArrayOf(78,50,(bytes.size and 255).toByte(),(bytes.size ushr 8).toByte())+bytes
        }
    }
}

/** Authority JSON envelope transported via the authenticated HOST route. The
 * host may forward another player's packet; no direct client identity is inferred.
 */
internal class RoomAuthorityWire(private val owner:Int,private val players:Int,epoch:String) {
    data class Packet(val source:Int,val bytes:ByteArray)
    private val epochBits:Long
    init {
        require(players in 2..8 && owner in 0 until players)
        require(Regex("[1-9][0-9]{0,19}").matches(epoch))
        val value=BigInteger(epoch);require(value.bitLength()<=64);epochBits=value.toLong()
    }
    internal fun validate(bytes:ByteArray,origin:Int,target:Int) {
        require(bytes.size in 33..544 && origin in 0 until players && target in 0 until players && origin!=target)
        val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(b.int==0x3153324e && (b.get().toInt() and 255)==origin && b.get()==0.toByte() &&
            (b.short.toInt() and 65535)==bytes.size-32 && b.long==epochBits && b.long!=0L)
    }
    fun encode(target:Int,bytes:ByteArray):ByteArray {
        validate(bytes,owner,target)
        return RoomJson.encode(mapOf("target" to target,"bytes" to Base64.getEncoder().encodeToString(bytes))).toByteArray(Charsets.UTF_8)
    }
    fun decode(bytes:ByteArray):Packet {
        require(bytes.size in 1..1024)
        val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val m=RoomDirectory.parseStrictJson(text) as? Map<*,*> ?: error("authority-message")
        require(m.keys==setOf("target","bytes") && m["target"]==owner.toDouble())
        val encoded=m["bytes"] as? String ?: error("authority-message")
        require(encoded.length in 1..728)
        val payload=Base64.getDecoder().decode(encoded)
        require(Base64.getEncoder().encodeToString(payload)==encoded && payload.size>=33)
        val source=payload[4].toInt() and 255;validate(payload,source,owner)
        return Packet(source,payload)
    }
}

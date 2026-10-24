package com.mpds.online

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/** Byte-identical native NPF1/NPF3 and NSP2 containers. Never edits ROMs or the
 * original player's save. The native boot adapter validates/applies these bytes.
 */
internal object RoomPlayerProfile {
    fun files(room:Map<String,Any?>,names:List<String>,emblems:List<String?>?):Map<String,ByteArray> {
        val players=(room["players"] as Double).toInt();val seat=(room["seat"] as Double).toInt()
        require(players in 2..8 && names.size==players && seat in 1..players)
        names.forEach { require(it.isNotEmpty());RoomDirectory.name(it) }
        val game=room["game"] ?: "mpds"
        if(game=="dkr" || game=="experimental")return emptyMap()
        require(game in listOf("mpds","mkds","nsmb"))
        if(game=="mpds")require(names.all { it.length<=8 })
        val images=if(game=="mkds")emblems ?: if(room["mkdsMenu"]==true)List(players){null}else null else null
        val packed=images?.let { all ->
            require(all.size==players)
            all.map { text -> text?.let {
                require(it.length==684);val bytes=Base64.getDecoder().decode(it)
                require(bytes.size==512 && Base64.getEncoder().encodeToString(bytes)==it);bytes
            } }
        }
        val npf=ByteBuffer.allocate(8+22*players+(packed?.count { it!=null } ?: 0)*512).order(ByteOrder.LITTLE_ENDIAN)
        npf.put((if(packed==null)"NPF1" else "NPF3").toByteArray(Charsets.US_ASCII))
        npf.put(players.toByte()).put(seat.toByte())
        var mask=0;packed?.forEachIndexed { i,b -> if(b!=null)mask=mask or (1 shl i) }
        npf.put(mask.toByte()).put(0)
        names.forEach { name -> npf.putShort(name.length.toShort());repeat(10) { i -> npf.putShort((name.getOrNull(i)?.code ?: 0).toShort()) } }
        packed?.forEach { if(it!=null)npf.put(it) }
        if(game!="nsmb")return mapOf("players.npf" to npf.array())
        require(players==2)
        val rom=room["romSha256"] as String;val set=room["setSha256"] as String
        require(Regex("[0-9a-f]{64}").matches(rom) && Regex("[0-9a-f]{64}").matches(set))
        val nsp=ByteBuffer.allocate(180).order(ByteOrder.LITTLE_ENDIAN)
        nsp.put("NSP2".toByteArray(Charsets.US_ASCII)).put(2).put(seat.toByte()).putShort(0)
        nsp.put(rom.toByteArray(Charsets.US_ASCII)).put(set.toByteArray(Charsets.US_ASCII)).put(npf.array(),8,44)
        return mapOf("players.nsp" to nsp.array())
    }
}

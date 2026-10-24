package com.mpds.online

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Base64

/** Capture-side WCK2/WCA1 composition shared with checkpoint-game-owner.mjs
 * and checkpoint-authority-owner.mjs. Caller supplies the frozen native owner
 * and reliable network image from the SAME completed collective operation.
 * No files, network credentials, automatic capability or synthetic game state.
 */
internal object RoomCheckpointCapture {
    data class Parts(val core:ByteArray,val sdk:ByteArray,val transport:ByteArray,val frontend:ByteArray) {
        fun clear(){listOf(core,sdk,transport,frontend).forEach { it.fill(0) }}
    }
    private val limits=intArrayOf(32*1024*1024,65536,65536,7*1024*1024,256,65536,512)
    private fun text(bytes:ByteArray)=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    private fun uint(s:String){require(Regex("[1-9][0-9]{0,19}").matches(s)&&BigInteger(s).bitLength()<=64)}
    fun readComponent(component:Int,read:(Int,Int)->ByteArray?,current:()->Unit):ByteArray {
        require(component in limits.indices);current()
        var result:ByteArray?=null;var offset=0
        try{
            do{
                current();val chunk=checkNotNull(read(component,offset)){"checkpoint-component-missing"};current()
                require(chunk.size in 4..65540)
                val total=ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).int
                require(total in 0..limits[component]&&(total>0||component==2))
                if(result==null)result=ByteArray(total)
                require(total==result.size&&offset<=total&&chunk.size-4==minOf(65536,total-offset))
                chunk.copyInto(result,offset,4);offset+=chunk.size-4
            }while(offset<result!!.size)
            current();return result
        }catch(e:Exception){result?.fill(0);throw e}
    }
    fun capture(bindingSha256:String,network:RoomAuthorityCheckpoint.Image,
        read:(Int,Int)->ByteArray?,current:()->Unit):Parts {
        require(Regex("[a-f0-9]{64}").matches(bindingSha256));uint(network.epoch);uint(network.incarnation);uint(network.operation)
        require(network.version==1&&network.players in 2..8&&network.owner in 1 until network.players&&
            network.cursor in 0 until network.players&&network.queues.size==network.players)
        val codec=RoomAuthorityWire(network.owner,network.players,network.epoch)
        val queues=network.queues.mapIndexed { peer,q->
            require(q.size<=128&&(peer!=network.owner||q.isEmpty()))
            q.map { codec.validate(it,peer,network.owner);Base64.getEncoder().encodeToString(it) }
        }
        val native=mutableListOf<ByteArray>();var output:Parts?=null
        try{
            for(component in limits.indices)native.add(readComponent(component,read,current))
            require(native[2].isEmpty()) { "checkpoint-service-conflict" }
            val f=RoomDirectory.parseStrictJson(text(native[6])) as? Map<*,*> ?: error("checkpoint-frontend")
            require(f.keys==setOf("endpoint","frame"));val endpoint=f["endpoint"] as String;uint(endpoint)
            val frame=f["frame"] as? Double ?: error("checkpoint-frontend")
            require(frame.isFinite()&&frame in 0.0..4294967295.0&&frame==frame.toLong().toDouble())
            val frontend=RoomJson.encode(mapOf("version" to 1,"policy" to "resample-input-clear-output",
                "endpoint" to endpoint,"frame" to frame.toLong())).toByteArray()
            val packed=ByteBuffer.allocate(20+native[1].size+native[4].size+native[3].size).order(ByteOrder.LITTLE_ENDIAN)
                .put("WCK2".toByteArray()).putInt(native[1].size).putInt(0).putInt(native[4].size).putInt(native[3].size)
                .put(native[1]).put(native[4]).put(native[3]).array()
            val envelope=RoomJson.encode(mapOf("version" to "WAT1","binding" to mapOf(
                "bindingSha256" to bindingSha256,"epoch" to network.epoch,"players" to network.players,
                "seat" to network.owner+1,"operation" to network.operation,"incarnation" to network.incarnation),
                "hashes" to mapOf("core" to hash(native[0]),"sdk" to hash(packed),"transport" to hash(native[5]),"frontend" to hash(frontend)),
                "authority" to mapOf("version" to 1,"epoch" to network.epoch,"incarnation" to network.incarnation,
                    "operation" to network.operation,"owner" to network.owner,"players" to network.players,"cursor" to network.cursor,"queues" to queues))).toByteArray()
            require(envelope.size<=768*1024&&16+packed.size+envelope.size<=8*1024*1024)
            val sdk=ByteBuffer.allocate(16+packed.size+envelope.size).order(ByteOrder.LITTLE_ENDIAN)
                .put("WCA1".toByteArray()).putInt(packed.size).putInt(envelope.size).putInt(0).put(packed).put(envelope).array()
            packed.fill(0);envelope.fill(0)
            output=Parts(native[0].copyOf(),sdk,native[5].copyOf(),frontend);current();return output
        }catch(e:Exception){output?.clear();throw e}
        finally{native.forEach { it.fill(0) }}
    }
}

package com.mpds.online

import java.io.Closeable

/** Routes supplied by the authenticated checkpoint owner lane. Never uses a
 * peer-selected gateway URL. Pins original seat, relay and lease; fresh room
 * capabilities are owned and erased independently of the live admission. */
internal object RoomCheckpointRoutes {
    class Route private constructor(val channel:RoomInvitation.Channel):Closeable {
        override fun close(){channel.capability.fill(0)}
        companion object {
            internal fun validated(config:RoomInvitation.Config,key:ByteArray)=Route(RoomInvitation.Channel(config,key))
        }
    }
    private fun fields(value:Any?,vararg names:String):Map<*,*> {
        val map=value as? Map<*,*> ?: error("checkpoint-route")
        require(map.keys==names.toSet());return map
    }
    private fun integer(value:Any?,low:Long,high:Long):Long {
        val n=value as? Double ?: error("checkpoint-route-number")
        require(n.isFinite()&&n>=low.toDouble()&&n<=high.toDouble()&&n==n.toLong().toDouble());return n.toLong()
    }
    fun validate(value:Any?,original:RoomInvitation.Config,now:Long=System.currentTimeMillis()):Route {
        val m=fields(value,"config","capability")
        val c=fields(m["config"],"version","role","relayAddress","relayPort","room","seat","invitationId",
            "ttlSeconds","expiresAtUnixMs","capabilityFile","localPort")
        require(c["version"]==1.0&&c["role"]=="client"&&c["seat"]==original.seat.toDouble()&&
            c["relayAddress"]==original.relayAddress&&c["relayPort"]==original.relayPort.toDouble()&&c["capabilityFile"]=="capability.bin")
        val room=c["room"] as? String ?: error("checkpoint-route-room")
        val id=c["invitationId"] as? String ?: error("checkpoint-route-id")
        require(Regex("[A-Za-z0-9_-]{1,15}").matches(room)&&room!=original.room&&Regex("[a-f0-9]{32}").matches(id))
        val ttl=integer(c["ttlSeconds"],1,original.ttlSeconds.toLong()).toInt()
        val expiry=integer(c["expiresAtUnixMs"],1,original.expiresAtUnixMs)
        require(expiry>now)
        val localPort=integer(c["localPort"],0,65535).toInt()
        val values=m["capability"] as? List<*> ?: error("checkpoint-route-key")
        require(values.size==32)
        val key=ByteArray(32)
        try {
            for(i in key.indices)key[i]=integer(values[i],0,255).toByte()
            require(key.any { it!=0.toByte() })
            return Route.validated(RoomInvitation.Config(original.relayAddress,original.relayPort,room,original.seat,id,ttl,expiry,localPort),key)
        }catch(e:Exception){key.fill(0);throw e}
    }
    fun validateAuthority(value:Any?,original:RoomInvitation.Config,room:Map<String,Any?>,
        sdkRoom:String,now:Long=System.currentTimeMillis()):Route {
        val m=fields(value,"room","route")
        val next=fields(m["room"],"game","seat","players","epoch","authorityTransport","relayAddress","relayPort","ttlSeconds","expiresAtUnixMs","room")
        for(key in listOf("game","seat","players","authorityTransport","relayAddress","relayPort"))require(next[key]==room[key])
        val epoch=next["epoch"] as? String ?: error("checkpoint-route-epoch")
        require(Regex("[1-9][0-9]{0,19}").matches(epoch)&&java.math.BigInteger(epoch).bitLength()<=64)
        val nextRoom=next["room"] as? String ?: error("checkpoint-route-room")
        require(Regex("[A-Za-z0-9_-]{1,15}").matches(nextRoom))
        val route=validate(m["route"],original,now)
        try {
            val c=route.channel.config
            require(c.room.startsWith("D")&&c.room!=sdkRoom&&c.room!=nextRoom&&next["expiresAtUnixMs"]==c.expiresAtUnixMs.toDouble()&&
                next["ttlSeconds"]==c.ttlSeconds.toDouble()&&c.expiresAtUnixMs<=(room["expiresAtUnixMs"] as Double).toLong())
            return route
        }catch(e:Exception){route.close();throw e}
    }
}

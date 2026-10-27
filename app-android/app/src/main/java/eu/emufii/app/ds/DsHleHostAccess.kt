package eu.emufii.app.ds

import android.content.Context
import eu.emufii.app.profile.Profile
import eu.emufii.dshle.DsHleRuntime
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

typealias DsHleSignedTransport = suspend (path:String,method:String,body:JSONObject?,bearer:String?,maxBytes:Int)->Result<ByteArray>

/** Application-owned signed coordinator transport. The runtime receives no
 * endpoint or profile credential; every call is bound to this private session. */
class DsHleHostAccess(
    @Suppress("UNUSED_PARAMETER") context:Context?,
    private val code:String,
    private val token:String,
    profile:Profile,
    private val identityKey:String,
    private val transportSigned:DsHleSignedTransport
) : DsHleRuntime.HostAccess {
    private val profileId=profile.id
    private val stopped=AtomicBoolean(false)
    @Volatile private var generation=0L
    private val prefix="/sessions/$code/ds/gold"
    init {
        require(Regex("[A-Z0-9-]{3,16}").matches(code))
        require(token.length in 32..128&&token.none {it.code<33||it.code>126})
        require(Regex("[0-9A-HJKMNP-TV-Z]{12}").matches(profileId)&&HASH.matches(identityKey))
    }
    private fun current(){check(!stopped.get()){"ds-host-retired"}}
    private fun body()=JSONObject().put("id",profileId).put("key",identityKey)
    private fun call(operation:String,value:JSONObject,maxBytes:Int=262144):ByteArray {
        current()
        val response=runBlocking {transportSigned("$prefix/$operation","POST",value,token,maxBytes).getOrThrow()}
        try{current();require(response.size<=maxBytes);return response}catch(e:Throwable){response.fill(0);throw e}
    }
    override fun prepare(romSha256:String,romBytes:Long):DsHleRuntime.HostPlan {
        require(HASH.matches(romSha256)&&romBytes in 512..67108864)
        check(generation==0L){"ds-host-already-prepared"}
        val value=json(call("prepare",body().put("romSha256",romSha256).put("romBytes",romBytes)))
        fields(value,setOf("version","generation","lobbyInvitations","gameInvitations"))
        require(integer(value,"version",1,1)==1L)
        val next=integer(value,"generation",1,1000000)
        fun invitations(key:String):List<String> {
            val array=value.getJSONArray(key);require(array.length() in 2..8)
            return (0 until array.length()).map {index->
                val text=array.get(index) as? String ?: error("ds-host-invitation")
                require(text.length in 1..30000&&text.startsWith("https://party.lepast.fr/nifi2/join/#"))
                text
            }
        }
        val lobby=invitations("lobbyInvitations");val game=invitations("gameInvitations")
        require(lobby.size==game.size);current();generation=next
        return DsHleRuntime.HostPlan(next,lobby,game)
    }
    override fun gatewayTicket(generation:Long,room:String,seat:Int,role:String,expiresAtUnixMs:Long):String {
        require(generation==this.generation&&generation>0&&ROUTE.matches(room)&&seat in 0..7&&
            role=="server"&&expiresAtUnixMs>System.currentTimeMillis())
        val value=json(call("gateway-ticket",body().put("generation",generation).put("room",room)
            .put("seat",seat).put("role",role).put("expiresAtUnixMs",expiresAtUnixMs),4096))
        fields(value,setOf("ticket"))
        return value.get("ticket").let {it as? String ?: error("ds-host-ticket")}
            .also {require(TICKET.matches(it));current()}
    }
    override fun openPreparation(seat:Int,kind:String,bytes:Long,sha256:String):InputStream {
        require(generation>0&&seat in 1..8&&kind in setOf("seat","boot")&&bytes in 1..MAX_PREPARATION_BYTES&&HASH.matches(sha256))
        // No ROM source/bytes enter this route. The signed invitation selects
        // the exact private preparation; runtime revalidates it before staging.
        val result=call("preparation",body().put("generation",generation).put("seat",seat).put("kind",kind)
            .put("bytes",bytes).put("sha256",sha256),bytes.toInt())
        try {
            require(result.size.toLong()==bytes&&MessageDigest.getInstance("SHA-256").digest(result)
                .joinToString(""){"%02x".format(it.toInt() and 255)}==sha256)
            current()
            return object:FilterInputStream(ByteArrayInputStream(result)) {
                private val done=AtomicBoolean(false)
                override fun close(){if(done.compareAndSet(false,true)){try{super.close()}finally{result.fill(0)}}}
            }
        }catch(e:Throwable){result.fill(0);throw e}
    }
    override fun publish(generation:Long) {
        current()
        require(generation==this.generation&&generation>0)
        val value=json(call("publish",body().put("generation",generation),4096));fields(value,setOf("ok"));require(value.get("ok")==true)
    }
    override fun close() {
        if(!stopped.compareAndSet(false,true))return
        // Closing is allowed after a failed/partial prepare. Endpoint ownership
        // remains fixed by CoordinatorClient, and credentials are never logged.
        val result=runBlocking {transportSigned("$prefix/close","POST",body(),token,4096).getOrThrow()}
        val value=json(result);fields(value,setOf("ok"));require(value.get("ok")==true)
        generation=0L
    }
    override fun toString()="DsHleHostAccess(redacted)"
    companion object {
        // Wide-window NSMB generated bundles: two cores plus two 64 KiB metas.
        private const val MAX_PREPARATION_BYTES=38619610L
        private val HASH=Regex("[0-9a-f]{64}")
        private val ROUTE=Regex("[A-Za-z0-9_-]{1,15}")
        private val TICKET=Regex("[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]")
        private fun json(bytes:ByteArray):JSONObject=try {
            val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            JSONObject(text)
        }finally{bytes.fill(0)}
        private fun fields(value:JSONObject,expected:Set<String>){require(value.keys().asSequence().toSet()==expected)}
        private fun integer(value:JSONObject,key:String,min:Long,max:Long):Long {
            val raw=value.get(key) as? Number ?: error("ds-host-number")
            val number=raw.toDouble();require(number.isFinite()&&number==number.toLong().toDouble()&&number>=min&&number<=max)
            return number.toLong()
        }
    }
}

data class DsHleGuestAdmission(val code:String,val seat:Int)

/** Private membership remains the outer credential. Native directory tokens
 * are forwarded only inside signed requests bound to this session/profile. */
class DsHleGuestAccess(
    @Suppress("UNUSED_PARAMETER") context:Context?,
    private val code:String,
    private val token:String,
    profile:Profile,
    private val identityKey:String,
    private val transportSigned:DsHleSignedTransport
) : DsHleRuntime.GuestAccess {
    private val profileId=profile.id
    private val stopped=AtomicBoolean(false)
    private val prefix="/sessions/$code/ds/gold"
    @Volatile private var admission:DsHleGuestAdmission?=null
    init {
        require(Regex("[A-Z0-9-]{3,16}").matches(code))
        require(token.length in 32..128&&token.none {it.code<33||it.code>126})
        require(Regex("[0-9A-HJKMNP-TV-Z]{12}").matches(profileId)&&HASH.matches(identityKey))
    }
    private fun current(){check(!stopped.get()){"ds-guest-retired"}}
    private fun identity()=JSONObject().put("id",profileId).put("key",identityKey)
    private fun call(path:String,body:JSONObject,maxBytes:Int):ByteArray {
        current()
        val bytes=runBlocking {transportSigned(path,"POST",body,token,maxBytes).getOrThrow()}
        try{current();require(bytes.size<=maxBytes);return bytes}catch(e:Throwable){bytes.fill(0);throw e}
    }
    /** Invoke on the application's worker before openGuest; this does not join
     * an anonymous public room or expose either private credential in an Intent. */
    @Synchronized fun admission():DsHleGuestAdmission {
        current();admission?.let {return it}
        val value=json(call("$prefix/admission",identity(),4096))
        require(value.keys().asSequence().toSet()==setOf("protocol","code","seat")&&
            value.get("protocol")=="emufii-ds-session-v1")
        val nativeCode=value.get("code") as? String ?: error("ds-guest-code")
        val rawSeat=value.get("seat") as? Number ?: error("ds-guest-seat")
        val seat=rawSeat.toDouble()
        require(CODE.matches(nativeCode)&&seat.isFinite()&&seat in 2.0..8.0&&seat==seat.toInt().toDouble())
        return DsHleGuestAdmission(nativeCode,seat.toInt()).also {current();admission=it}
    }
    override fun post(roomCode:String,operation:String,nativeToken:String?,body:ByteArray):ByteArray {
        current();require(admission?.code==roomCode&&CODE.matches(roomCode)&&operation in OPERATIONS)
        require(if(operation=="join")nativeToken==null else nativeToken!=null&&TICKET.matches(nativeToken))
        require(body.size in 2..65536)
        val request=json(body.copyOf())
        val envelope=identity().put("roomCode",roomCode).put("request",request)
        if(nativeToken!=null)envelope.put("nativeToken",nativeToken)
        return call("$prefix/guest/$operation",envelope,65536)
    }
    override fun close(){stopped.set(true);admission=null}
    override fun toString()="DsHleGuestAccess(redacted)"
    companion object {
        private val CODE=Regex("[A-Z0-9]{4}")
        private val HASH=Regex("[0-9a-f]{64}")
        private val TICKET=Regex("[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]")
        private val OPERATIONS=setOf("join","poll","leave","failed","gateway-ticket","rom-prefetch-read")
        private fun json(bytes:ByteArray):JSONObject=try {
            val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            JSONObject(text)
        }finally{bytes.fill(0)}
    }
}

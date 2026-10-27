package eu.emufii.dshle

import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import com.mpds.online.HleGameActivity
import com.mpds.online.RoomEngine
import com.mpds.online.BuildConfig
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Embedded GOLD entry point. The authenticated protocol owns all routes. */
object DsHleRuntime {
    enum class LaunchResult { Started, AlreadyRunning, InvalidRequest, UnsupportedDevice, Failed }

    private val running = MutableStateFlow(false)
    private var closer:(()->Unit)?=null
    private var closeRequested=false
    val active: StateFlow<Boolean> = running.asStateFlow()
    val supported: Boolean get() = GuestRequest.supports(Build.SUPPORTED_ABIS.toList())
    fun acceptsEndpoints(directoryUrl:String,gatewayUrl:String):Boolean =
        directoryUrl==BuildConfig.DIRECTORY_ENDPOINT&&gatewayUrl==BuildConfig.GATEWAY_ENDPOINT

    internal const val EXTRA_ROOM = "eu.emufii.dshle.room"
    internal const val EXTRA_NAME = "eu.emufii.dshle.name"
    internal const val EXTRA_HOST = "eu.emufii.dshle.host"
    internal const val EXTRA_GUEST = "eu.emufii.dshle.guest"

    /** Application-owned directory requests. Endpoint selection and private
     * member credentials remain in the application; the native token is scoped
     * to the admitted seat. Calls run on the room worker, never the UI thread. */
    fun interface GuestAccess : java.io.Closeable {
        fun post(roomCode:String,operation:String,nativeToken:String?,body:ByteArray):ByteArray
        override fun close() {}
    }
    private val guests=mutableMapOf<String,GuestAccess>()
    @Synchronized internal fun takeGuest(id:String?)=id?.let(guests::remove)

    data class HostRequest(val roomCode:String,val playerName:String,val romUri:Uri,
        val game:String,val players:Int)
    /** Two independently signed epochs: persistent lobby and one inner game.
     * Invitations are in seat order, including P1. No URLs select an endpoint. */
    data class HostPlan(val generation:Long,val lobbyInvitations:List<String>,val gameInvitations:List<String>)
    interface HostAccess {
        /** Blocking worker calls, bounded/cancellable by the application's adapter. */
        fun prepare(romSha256:String,romBytes:Long):HostPlan
        fun gatewayTicket(generation:Long,room:String,seat:Int,role:String,expiresAtUnixMs:Long):String
        fun openPreparation(seat:Int,kind:String,bytes:Long,sha256:String):InputStream
        /** Publish only after the native host has installed real server carriers. */
        fun publish(generation:Long)
        fun close()
    }
    internal data class HostRegistration(val request:HostRequest,val access:HostAccess)
    private val hosts=mutableMapOf<String,HostRegistration>()
    @Synchronized internal fun takeHost(id:String?)=id?.let(hosts::remove)

    @Synchronized
    fun openHost(context:Context,request:HostRequest,access:HostAccess):LaunchResult {
        val parsed=GuestRequest.parse("0000",request.playerName) ?: return LaunchResult.InvalidRequest
        val hostCode=request.roomCode.trim().uppercase(java.util.Locale.ROOT)
        if(request.roomCode.any {it.code>127}||!Regex("[A-Z0-9-]{3,16}").matches(hostCode))return LaunchResult.InvalidRequest
        if(request.game !in (setOf("mpds","mkds","dkr","nsmb") + if(BuildConfig.SDK_BLOCKS)setOf("experimental")else emptySet()) || request.players !in 2..when(request.game){"mpds"->4;"dkr","nsmb"->2;else->8} ||
            request.romUri.scheme !in setOf("content","file") ||
            request.game=="mpds"&&parsed.playerName.length>8)return LaunchResult.InvalidRequest
        if(!supported)return LaunchResult.UnsupportedDevice
        if(running.value)return LaunchResult.AlreadyRunning
        val id=UUID.randomUUID().toString()
        hosts[id]=HostRegistration(request.copy(roomCode=hostCode,playerName=parsed.playerName),access)
        closer=null;closeRequested=false;running.value=true
        return try {
            context.startActivity(Intent(context,HleGameActivity::class.java).apply {
                putExtra(EXTRA_ROOM,hostCode);putExtra(EXTRA_NAME,parsed.playerName);putExtra(EXTRA_HOST,id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            });LaunchResult.Started
        } catch(_:Exception){hosts.remove(id);running.value=false;LaunchResult.Failed}
    }

    @Synchronized
    fun openGuest(context: Context, roomCode: String, playerName: String, access:GuestAccess?=null): LaunchResult {
        val request = GuestRequest.parse(roomCode, playerName) ?: return LaunchResult.InvalidRequest
        if (!supported) return LaunchResult.UnsupportedDevice
        if (running.value) return LaunchResult.AlreadyRunning
        val id=access?.let {UUID.randomUUID().toString().also {id->guests[id]=it}}
        closer=null;closeRequested=false;running.value = true
        return try {
            context.startActivity(Intent(context, HleGameActivity::class.java).apply {
                putExtra(EXTRA_ROOM, request.roomCode)
                putExtra(EXTRA_NAME, request.playerName)
                if(id!=null)putExtra(EXTRA_GUEST,id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            LaunchResult.Started
        } catch (_: Exception) {
            if(id!=null)guests.remove(id)
            running.value = false
            LaunchResult.Failed
        }
    }

    @Synchronized
    internal fun opened() { running.value = true }

    /** Application/session shutdown: the activity retires the real native lease.
     * active remains true until the process death/cleanup has been confirmed. */
    @Synchronized
    fun requestClose():Boolean {
        if(!running.value)return false
        closeRequested=true;closer?.invoke();return true
    }
    @Synchronized
    internal fun attachCloser(action:()->Unit) {
        closer=action;if(closeRequested)action()
    }

    /** Called only after the asynchronous round lease has been retired. */
    @Synchronized
    internal fun retired() {
        if(RoomEngine.hasReservedLease())return
        running.value = false;closer=null;closeRequested=false
    }
}

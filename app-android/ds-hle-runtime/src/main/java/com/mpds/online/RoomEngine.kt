package com.mpds.online

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synchronous room-only JNI facade. There is deliberately no local JNI fallback.
 * Open and close on a worker. A failed/dead lease must still be closed. A close
 * timeout retains the reservation until confirmed Binder death; never reuse that process.
 * Stage preparation with NativeBridge in the caller process before configuring.
 */
object RoomEngine {
    private val ownership = Any()
    private var active: Lease? = null
    internal fun hasReservedLease() = synchronized(ownership) { active != null }
    // Serialize emulator calls BEFORE entering Binder, keeping its pool available
    // for carrier traffic and stop while a frame/boot waits for WM network replies.
    private val emulator = Any()
    private val incoming = Any()
    private val outgoing = Any()
    // Only used under emulator; normal DsView arrays are filled directly.
    private val screenScratchTop = IntArray(RoomWire.PIXELS)
    private val screenScratchBottom = IntArray(RoomWire.PIXELS)

    fun open(context: Context): Closeable {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Open room on a worker" }
        val lease = synchronized(ownership) {
            check(active == null) { "A room lease is already reserved" }
            Lease(context.applicationContext).also { active = it }
        }
        try {
            lease.bind()
            check(lease.ready.await(15, TimeUnit.SECONDS)) { "Room binding timed out" }
            lease.failure?.let { throw IllegalStateException(it) }
            check(lease.binder != null && lease.dead.count != 0L) { "Room process unavailable" }
            return lease
        } catch (error: Throwable) {
            try { lease.close() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            throw error
        }
    }

    private class Lease(val context: Context) : Closeable, ServiceConnection {
        val ready = CountDownLatch(1)
        val dead = CountDownLatch(1)
        val callbacks = HandlerThread("room-binding").apply { start() }
        val handler = Handler(callbacks.looper)
        val client = Binder()
        @Volatile var binder: IBinder? = null
        @Volatile var failure: String? = null
        @Volatile var closing = false
        private var bound = false
        private val death = IBinder.DeathRecipient { died() }

        fun bind() {
            // This method is called by the start worker, never the main thread.
            synchronized(this) {
                bound = context.bindService(Intent(context, RoomEngineService::class.java),
                    this, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
                if (!bound) {
                    failure = "Room service binding rejected"
                    dead.countDown()
                    ready.countDown()
                }
            }
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            // Public bindService overload works on minSdk 26. Framework callbacks
            // only post work; the handshake and lifecycle handling stay off UI.
            handler.post { connected(service) }
        }

        private fun connected(service: IBinder) {
            synchronized(this) {
                // Automatic reconnection must never substitute a fresh native process.
                if (binder != null || dead.count == 0L) return
                binder = service
                try {
                    service.linkToDeath(death, 0)
                    transact(service, RoomWire.HELLO, { writeStrongBinder(client) }) { }
                } catch (_: Exception) {
                    failure = "Room service handshake failed"
                    closing = true
                    if (!service.isBinderAlive) died()
                } finally {
                    ready.countDown()
                }
                if (closing && dead.count != 0L) requestShutdown(service)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            handler.post { disconnected() }
        }

        private fun disconnected() {
            failure = "Room process disconnected"
            ready.countDown()
            // Only the death recipient / a positively dead binder releases ownership.
            binder?.let { if (!it.isBinderAlive) died() }
        }

        override fun onBindingDied(name: ComponentName) {
            handler.post { bindingDied() }
        }

        private fun bindingDied() {
            failure = "Room service binding died"
            ready.countDown()
            binder?.let { if (!it.isBinderAlive) died() else requestShutdown(it) }
        }

        override fun onNullBinding(name: ComponentName) {
            handler.post { nullBinding() }
        }

        private fun nullBinding() {
            failure = "Room service returned no binder"
            ready.countDown()
            // No native session was acquired; this service never returns a null binder.
            dead.countDown()
            if (closing) release()
        }

        private fun died() {
            failure = "Room process died"
            dead.countDown()
            ready.countDown()
            if (closing) release()
        }

        private fun requestShutdown(service: IBinder) {
            try {
                // Synchronous admission preserves getCallingPid() for ownership
                // validation. The service starts teardown on a separate thread.
                transact(service, RoomWire.SHUTDOWN, {}) { }
            } catch (_: Exception) {
                if (!service.isBinderAlive) died()
                // If alive, retain ownership; do not pretend teardown completed.
            }
        }

        override fun close() {
            check(Looper.myLooper() != Looper.getMainLooper()) { "Close room on a worker" }
            synchronized(this) {
                closing = true
                binder?.let { if (dead.count != 0L) requestShutdown(it) }
                if (!bound && binder == null) dead.countDown()
            }
            check(dead.await(15, TimeUnit.SECONDS)) {
                "Room death not confirmed; lease remains reserved"
            }
            release()
        }

        private fun release() {
            synchronized(this) {
                if (bound) {
                    context.unbindService(this)
                    bound = false
                }
                callbacks.quitSafely()
            }
            synchronized(ownership) { if (active === this) active = null }
        }
    }

    private fun <T> call(code: Int, write: Parcel.() -> Unit = {}, read: Parcel.() -> T): T {
        val lease = synchronized(ownership) { active }
            ?: error("RoomEngine requires an open lease")
        check(!lease.closing && lease.dead.count != 0L && lease.failure == null) {
            lease.failure ?: "Room lease is closed"
        }
        return transact(checkNotNull(lease.binder) { "Room lease is not ready" }, code, write, read)
    }

    private fun <T> transact(service: IBinder, code: Int, write: Parcel.() -> Unit,
                            read: Parcel.() -> T): T {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(RoomWire.DESCRIPTOR)
            data.write()
            require(data.dataSize() <= RoomWire.MAX_REQUEST) { "Room request too large" }
            check(service.transact(code, data, reply, 0)) { "Room transaction rejected" }
            check(reply.dataSize() <= RoomWire.MAX_REPLY) { "Room reply too large" }
            reply.readException()
            return reply.read()
        } finally { reply.recycle(); data.recycle() }
    }

    fun nativeConfigureRoom(game: Int, boot: Int, players: Int, seat: Int,
        bufferTicks: Int, language: Int, epoch: String, nickname: String,
        romSha: String, romBytes: Long, personalSave: Boolean, roomNames: Boolean,
        generatedSha: String, preparationDirectory: String): Long = synchronized(emulator) {
        call(RoomWire.CONFIGURE, {
            writeInt(game); writeInt(boot); writeInt(players); writeInt(seat)
            writeInt(bufferTicks); writeInt(language)
            RoomWire.writeText(this, epoch); RoomWire.writeText(this, nickname)
            RoomWire.writeText(this, romSha); writeLong(romBytes)
            writeInt(if (personalSave) 1 else 0); writeInt(if (roomNames) 1 else 0)
            RoomWire.writeText(this, generatedSha); RoomWire.writeText(this, preparationDirectory)
        }) { readLong() }
    }

    fun nativeBootRoom(ticket: Long, directory: String): Int = synchronized(emulator) {
        call(RoomWire.BOOT, { writeLong(ticket); RoomWire.writeText(this, directory) }) { readInt() }
    }

    fun nativeDeliverRoomPacket(ticket: Long, lane: Int, source: Int, bytes: ByteArray): Int = synchronized(incoming) {
        require(bytes.size <= RoomWire.MAX_PACKET) { "Room packet too large" }
        var offset = 0
        var result: Int
        do {
            val end = minOf(bytes.size, offset + RoomWire.PACKET_CHUNK)
            result = call(RoomWire.DELIVER, {
                writeLong(ticket); writeInt(lane); writeInt(source)
                writeInt(bytes.size); writeInt(offset); writeByteArray(bytes, offset, end - offset)
            }) { readInt() }
            offset = end
            if (offset < bytes.size) check(result == RoomWire.MORE) { "Room packet transfer interrupted" }
        } while (offset < bytes.size)
        check(result in -1..1) { "Invalid room packet admission" }
        result
    }

    fun nativeTakeRoomPacket(ticket: Long): ByteArray? = synchronized(outgoing) {
        var packet: ByteArray? = null
        var offset = 0
        do {
            call(RoomWire.TAKE, { writeLong(ticket); writeInt(offset) }) {
                val total = readInt()
                if (total == -1) {
                    check(offset == 0) { "Room packet transfer interrupted" }
                } else {
                    check(total in 2..RoomWire.MAX_PACKET + 2) { "Invalid room packet size" }
                    if (offset == 0) packet = ByteArray(total)
                    val target = checkNotNull(packet)
                    check(target.size == total)
                    val chunk = checkNotNull(createByteArray())
                    check(chunk.size == minOf(RoomWire.PACKET_CHUNK, total - offset))
                    chunk.copyInto(target, offset)
                    offset += chunk.size
                }
            }
        } while (packet != null && offset < packet!!.size)
        packet
    }

    fun nativeCloseRoomCarrier(ticket: Long) {
        call(RoomWire.CLOSE_CARRIER, { writeLong(ticket) }) { }
    }

    fun nativeCheckpointStatus(ticket:Long,action:Int,operation:String,incarnation:String,
        releasedMask:Int=0,inFlightAccounted:Boolean=false):Int=synchronized(emulator){
        require(action in 0..10 && releasedMask in 0..255)
        call(RoomWire.CHECKPOINT_STATUS,{
            writeLong(ticket);writeInt(action);RoomWire.writeText(this,operation);RoomWire.writeText(this,incarnation)
            writeInt(releasedMask);writeInt(if(inFlightAccounted)1 else 0)
        }){readInt().also { check(it in 0..7) }}
    }
    fun nativeCheckpointPart(ticket:Long,operation:String,incarnation:String,component:Int,offset:Int):ByteArray?=
        synchronized(emulator){
            require(component in 0..6 && offset>=0)
            call(RoomWire.CHECKPOINT_PART,{
                writeLong(ticket);RoomWire.writeText(this,operation);RoomWire.writeText(this,incarnation)
                writeInt(component);writeInt(offset)
            }){createByteArray()?.also { check(it.size in 4..RoomWire.PACKET_CHUNK+4) }}
        }
    fun nativeTakeHeldRoomState(ticket:Long):ByteArray?=synchronized(incoming){
        call(RoomWire.TAKE_HELD_STATE,{writeLong(ticket)}){
            createByteArray()?.also { check(it.size in 34..545) }
        }
    }
    fun nativeCheckpointUpload(ticket:Long,operation:String,freshIncarnation:String,capturedIncarnation:String,
        component:Int,total:Int,offset:Int,bytes:ByteArray):Int=synchronized(emulator){
        require(component in 0..5&&total>=0&&offset in 0..total&&bytes.size<=RoomWire.PACKET_CHUNK)
        call(RoomWire.CHECKPOINT_UPLOAD,{
            writeLong(ticket);RoomWire.writeText(this,operation);RoomWire.writeText(this,freshIncarnation)
            RoomWire.writeText(this,capturedIncarnation);writeInt(component);writeInt(total);writeInt(offset);writeByteArray(bytes)
        }){readInt().also { check(it in 0..7) }}
    }
    fun nativeCheckpointMaterialize(ticket:Long,operation:String,freshIncarnation:String,capturedIncarnation:String,endpoint:String,frame:Long):Int=synchronized(emulator){
        require(frame in 0..4294967295L)
        call(RoomWire.CHECKPOINT_MATERIALIZE,{
            writeLong(ticket);RoomWire.writeText(this,operation);RoomWire.writeText(this,freshIncarnation);RoomWire.writeText(this,capturedIncarnation)
            RoomWire.writeText(this,endpoint);writeLong(frame)
        }){readInt().also { check(it in 0..7) }}
    }
    fun nativeCheckpointNewCarrier(ticket:Long,operation:String,freshIncarnation:String):Long=synchronized(emulator){
        call(RoomWire.CHECKPOINT_CARRIER,{
            writeLong(ticket);RoomWire.writeText(this,operation);RoomWire.writeText(this,freshIncarnation)
        }){readLong().also { check(it>0) {"checkpoint-carrier-rejected"} }}
    }

    // Must bypass the emulator monitor: native stop first wakes any blocked frame.
    fun nativeStopSingleOwner() { call(RoomWire.STOP) { } }

    fun nativeSingleOwnerError(): String = synchronized(emulator) {
        call(RoomWire.ERROR) { RoomWire.readText(this) }
    }

    fun nativeReadSingleOwnerAudio(out: ShortArray, maxPairs: Int): Int {
        // Match JNI's invalid-buffer behavior; verify a lease even for an empty read.
        val requested = if (maxPairs in 1..RoomWire.MAX_AUDIO_PAIRS && out.size >= maxPairs * 2)
            maxPairs else 0
        return call(RoomWire.AUDIO, { writeInt(requested) }) {
            val pairs = readInt()
            check(pairs in 0..requested) { "Invalid room audio reply" }
            repeat(pairs * 2) { out[it] = readInt().toShort() }
            pairs
        }
    }

    fun nativeRunFrame() { synchronized(emulator) { call(RoomWire.FRAME) { } } }

    fun nativeGetFramebuffer(top: IntArray, bottom: IntArray) {
        require(top.size >= RoomWire.PIXELS && bottom.size >= RoomWire.PIXELS)
        synchronized(emulator) {
            call(RoomWire.FRAMEBUFFER) {
                // Always transmit exactly two screens, regardless of caller capacity.
                val first = if(top.size == RoomWire.PIXELS)top else screenScratchTop
                val second = if(bottom.size == RoomWire.PIXELS)bottom else screenScratchBottom
                // Parcel enforces the encoded length equals the destination.
                readIntArray(first); readIntArray(second)
                if(first !== top)first.copyInto(top)
                if(second !== bottom)second.copyInto(bottom)
            }
        }
    }

    fun nativeSetInput(buttons: Int, touchX: Int, touchY: Int, touchDown: Boolean) {
        synchronized(emulator) {
            call(RoomWire.INPUT, {
                writeInt(buttons); writeInt(touchX); writeInt(touchY); writeInt(if (touchDown) 1 else 0)
            }) { }
        }
    }

    fun nativeIsBooted(): Int = synchronized(emulator) { call(RoomWire.IS_BOOTED) { readInt() } }
    fun exportRoomSave():String = synchronized(emulator) {
        call(RoomWire.EXPORT_SAVE) { RoomWire.readText(this) }
    }
}

/** Private, versioned protocol; all limits apply on both sides of Binder. */
internal object RoomWire {
    const val DESCRIPTOR = "com.mpds.online.RoomEngine.v1"
    const val HELLO = IBinder.FIRST_CALL_TRANSACTION
    const val CONFIGURE = HELLO + 1
    const val BOOT = HELLO + 2
    const val DELIVER = HELLO + 3
    const val TAKE = HELLO + 4
    const val CLOSE_CARRIER = HELLO + 5
    const val STOP = HELLO + 6
    const val ERROR = HELLO + 7
    const val AUDIO = HELLO + 8
    const val FRAME = HELLO + 9
    const val FRAMEBUFFER = HELLO + 10
    const val INPUT = HELLO + 11
    const val IS_BOOTED = HELLO + 12
    const val SHUTDOWN = HELLO + 13
    const val EXPORT_SAVE = HELLO + 14
    const val CHECKPOINT_STATUS = HELLO + 15
    const val CHECKPOINT_PART = HELLO + 16
    const val TAKE_HELD_STATE = HELLO + 17
    const val CHECKPOINT_UPLOAD = HELLO + 18
    const val CHECKPOINT_MATERIALIZE = HELLO + 19
    const val CHECKPOINT_CARRIER = HELLO + 20
    const val PIXELS = 256 * 192
    const val MAX_PACKET = 1024 * 1024 // Native RoomCarrier::MaxPacket
    const val PACKET_CHUNK = 64 * 1024
    const val MORE = 2
    const val MAX_AUDIO_PAIRS = 4096
    const val MAX_TEXT = 4096
    const val MAX_REQUEST = 96 * 1024
    const val MAX_REPLY = 400 * 1024 // two 196608-byte screens plus Parcel framing

    fun writeText(parcel: Parcel, value: String) {
        require(value.length <= MAX_TEXT) { "Room text too large" }
        parcel.writeString(value)
    }

    fun readText(parcel: Parcel): String = requireNotNull(parcel.readString()).also {
        require(it.length <= MAX_TEXT) { "Room text too large" }
    }
}

package com.mpds.online

import android.app.ActivityManager
import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/** One native round per process. Never load this service in the activity process. */
class RoomEngineService : Service() {
    private val stopping = AtomicBoolean(false)
    private val configuring = Any()
    private val admission = Semaphore(1)
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "room-native") }
    @Volatile private var ticket = 0L
    private var client: IBinder? = null
    @Volatile private var clientPid = -1
    private var roundProcess = false
    private val clientDeath = IBinder.DeathRecipient { shutdown() }
    private val incoming = Any()
    private val outgoing = Any()
    private var inPacket: ByteArray? = null
    private var inOffset = 0
    private var inTicket = 0L
    private var inLane = 0
    private var inSource = 0
    private var outPacket: ByteArray? = null
    private var outOffset = 0
    private var outTicket = 0L
    private val frameOutput = Any()
    private val frameTop = IntArray(RoomWire.PIXELS)
    private val frameBottom = IntArray(RoomWire.PIXELS)
    private val audioOutput = Any()
    private val audioSamples = ShortArray(RoomWire.MAX_AUDIO_PAIRS * 2)

    override fun onCreate() {
        super.onCreate()
        val processName = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName()
        else (getSystemService(ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses
            ?.firstOrNull { it.pid == Process.myPid() }?.processName
        roundProcess = processName == "$packageName:round"
        check(roundProcess) { "Room service requires its dedicated process" }
    }

    override fun onBind(intent: Intent): IBinder = endpoint
    override fun onUnbind(intent: Intent): Boolean { shutdown(); return false }
    override fun onDestroy() { shutdown(); super.onDestroy() }

    private fun shutdown() {
        if (!roundProcess || !stopping.compareAndSet(false, true)) return
        // Separate from both the Binder pool and emulator executor. Closing the
        // carrier must run while a blocked native boot/frame still owns its lock.
        Thread({
            try {
                synchronized(configuring) {
                    try {
                        if (ticket != 0L) NativeBridge.nativeCloseRoomCarrier(ticket)
                    } finally {
                        NativeBridge.nativeStopSingleOwner()
                    }
                }
            } finally {
                // Never use a PID supplied by the caller. Binder death is the
                // acknowledgement; onDestroy alone is not round isolation.
                Process.killProcess(Process.myPid())
            }
        }, "room-shutdown").start()
    }

    private fun <T> emulation(block: () -> T): T {
        // Reject extra simultaneous emulator requests instead of filling Binder
        // threads with queued frames. Carrier/audio/stop calls never take this permit.
        check(admission.tryAcquire()) { "Concurrent room emulator request" }
        try {
            return worker.submit(Callable {
                check(!stopping.get()) { "Room is stopping" }
                block()
            }).get()
        } finally { admission.release() }
    }

    private val endpoint = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return try {
                dispatch(code, data, reply, flags)
            } catch (_: Exception) {
                // Binder's default uncaught-exception reporting can log native
                // messages containing paths. Return a bounded transport error.
                if (reply != null && flags and IBinder.FLAG_ONEWAY == 0) {
                    reply.setDataSize(0)
                    reply.setDataPosition(0)
                    reply.writeException(IllegalStateException("Room service operation failed"))
                }
                true
            }
        }

        private fun dispatch(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) { reply?.writeString(RoomWire.DESCRIPTOR); return true }
            if (code !in RoomWire.HELLO..RoomWire.CHECKPOINT_CARRIER) return super.onTransact(code, data, reply, flags)
            require(data.dataSize() <= RoomWire.MAX_REQUEST) { "Room request too large" }
            data.enforceInterface(RoomWire.DESCRIPTOR)
            check(Binder.getCallingUid() == Process.myUid()) { "Room caller rejected" }
            if (code == RoomWire.HELLO) {
                synchronized(configuring) {
                    check(!stopping.get() && client == null) { "Room already claimed" }
                    check(Binder.getCallingPid() != Process.myPid()) { "Room must be remote" }
                    clientPid = Binder.getCallingPid()
                    client = requireNotNull(data.readStrongBinder())
                    client!!.linkToDeath(clientDeath, 0)
                }
                reply!!.writeNoException()
                return true
            }
            check(Binder.getCallingPid() == clientPid) { "Room lease owner mismatch" }
            if (code == RoomWire.SHUTDOWN) {
                requireNotNull(reply).writeNoException()
                shutdown()
                return true
            }
            check(!stopping.get()) { "Room is stopping" }
            val output = requireNotNull(reply)
            output.writeNoException()
            when (code) {
                RoomWire.CONFIGURE -> {
                    val game = data.readInt(); val boot = data.readInt()
                    val players = data.readInt(); val seat = data.readInt()
                    val ticks = data.readInt(); val language = data.readInt()
                    val epoch = RoomWire.readText(data); val nickname = RoomWire.readText(data)
                    val sha = RoomWire.readText(data); val bytes = data.readLong()
                    val save = data.readInt() != 0; val names = data.readInt() != 0
                    val generated = RoomWire.readText(data); val preparation = RoomWire.readText(data)
                    output.writeLong(emulation {
                        synchronized(configuring) {
                            check(!stopping.get()) { "Room is stopping" }
                            NativeBridge.nativeConfigureRoom(game, boot, players, seat, ticks, language,
                                epoch, nickname, sha, bytes, save, names, generated, preparation).also {
                                if (it != 0L) ticket = it
                            }
                        }
                    })
                }
                RoomWire.BOOT -> {
                    val id = data.readLong(); val directory = RoomWire.readText(data)
                    output.writeInt(emulation { NativeBridge.nativeBootRoom(id, directory) })
                }
                RoomWire.DELIVER -> {
                    val id = data.readLong(); val lane = data.readInt(); val source = data.readInt()
                    val total = data.readInt(); val offset = data.readInt()
                    require(total in 0..RoomWire.MAX_PACKET && offset in 0..total)
                    val bytes = requireNotNull(data.createByteArray())
                    require(bytes.size == minOf(RoomWire.PACKET_CHUNK, total - offset))
                    synchronized(incoming) {
                        if (offset == 0) {
                            inPacket = ByteArray(total); inOffset = 0
                            inTicket = id; inLane = lane; inSource = source
                        }
                        val packet = checkNotNull(inPacket)
                        check(id == inTicket && lane == inLane && source == inSource &&
                            offset == inOffset && total == packet.size)
                        bytes.copyInto(packet, offset)
                        inOffset += bytes.size
                        if (inOffset == total) {
                            inPacket = null
                            output.writeInt(NativeBridge.nativeDeliverRoomPacket(id, lane, source, packet))
                        } else output.writeInt(RoomWire.MORE)
                    }
                }
                RoomWire.TAKE -> {
                    val id = data.readLong(); val offset = data.readInt()
                    synchronized(outgoing) {
                        if (offset == 0) {
                            outPacket = NativeBridge.nativeTakeRoomPacket(id)
                            outTicket = id; outOffset = 0
                        }
                        val packet = outPacket
                        if (packet == null) {
                            check(offset == 0)
                            output.writeInt(-1)
                        } else {
                            check(packet.size in 2..RoomWire.MAX_PACKET + 2 &&
                                id == outTicket && offset == outOffset && offset < packet.size)
                            val size = minOf(RoomWire.PACKET_CHUNK, packet.size - offset)
                            output.writeInt(packet.size)
                            output.writeByteArray(packet, offset, size)
                            outOffset += size
                            if (outOffset == packet.size) outPacket = null
                        }
                    }
                }
                RoomWire.CLOSE_CARRIER -> NativeBridge.nativeCloseRoomCarrier(data.readLong())
                RoomWire.CHECKPOINT_STATUS -> {
                    val id=data.readLong();val action=data.readInt()
                    val operation=RoomWire.readText(data);val incarnation=RoomWire.readText(data)
                    val mask=data.readInt();val accounted=data.readInt()
                    require(action in 0..10 && mask in 0..255 && accounted in 0..1)
                    output.writeInt(emulation { NativeBridge.nativeCheckpointStatus(id,action,operation,incarnation,mask,accounted==1) })
                }
                RoomWire.CHECKPOINT_PART -> {
                    val id=data.readLong();val operation=RoomWire.readText(data);val incarnation=RoomWire.readText(data)
                    val component=data.readInt();val offset=data.readInt()
                    require(component in 0..6 && offset>=0)
                    output.writeByteArray(emulation { NativeBridge.nativeCheckpointPart(id,operation,incarnation,component,offset) })
                }
                RoomWire.TAKE_HELD_STATE -> {
                    val id=data.readLong()
                    synchronized(incoming) { output.writeByteArray(NativeBridge.nativeTakeHeldRoomState(id)) }
                }
                RoomWire.CHECKPOINT_UPLOAD -> {
                    val id=data.readLong();val operation=RoomWire.readText(data);val fresh=RoomWire.readText(data)
                    val captured=RoomWire.readText(data);val component=data.readInt();val total=data.readInt();val offset=data.readInt()
                    val bytes=requireNotNull(data.createByteArray())
                    require(component in 0..5&&total>=0&&offset in 0..total&&bytes.size<=RoomWire.PACKET_CHUNK)
                    try{output.writeInt(emulation { NativeBridge.nativeCheckpointUpload(id,operation,fresh,captured,component,total,offset,bytes) })}
                    finally{bytes.fill(0)}
                }
                RoomWire.CHECKPOINT_MATERIALIZE -> {
                    val id=data.readLong();val operation=RoomWire.readText(data);val fresh=RoomWire.readText(data);val captured=RoomWire.readText(data)
                    val endpoint=RoomWire.readText(data);val frame=data.readLong();require(frame in 0..4294967295L)
                    output.writeInt(emulation { NativeBridge.nativeCheckpointMaterialize(id,operation,fresh,captured,endpoint,frame) })
                }
                RoomWire.CHECKPOINT_CARRIER -> {
                    val id=data.readLong();val operation=RoomWire.readText(data);val fresh=RoomWire.readText(data)
                    output.writeLong(emulation { NativeBridge.nativeCheckpointNewCarrier(id,operation,fresh) })
                }
                RoomWire.STOP -> synchronized(configuring) {
                    try {
                        if (ticket != 0L) NativeBridge.nativeCloseRoomCarrier(ticket)
                    } finally { NativeBridge.nativeStopSingleOwner() }
                }
                RoomWire.ERROR -> RoomWire.writeText(output, emulation { NativeBridge.nativeSingleOwnerError() })
                RoomWire.AUDIO -> {
                    val maximum = data.readInt()
                    require(maximum in 0..RoomWire.MAX_AUDIO_PAIRS)
                    synchronized(audioOutput) {
                        val pairs = NativeBridge.nativeReadSingleOwnerAudio(audioSamples, maximum)
                        check(pairs in 0..maximum)
                        output.writeInt(pairs)
                        repeat(pairs * 2) { output.writeInt(audioSamples[it].toInt()) }
                    }
                }
                RoomWire.FRAME -> emulation { NativeBridge.nativeRunFrame() }
                RoomWire.FRAMEBUFFER -> {
                    synchronized(frameOutput) {
                        emulation { NativeBridge.nativeGetFramebuffer(frameTop, frameBottom) }
                        output.writeIntArray(frameTop); output.writeIntArray(frameBottom)
                    }
                }
                RoomWire.INPUT -> {
                    val buttons = data.readInt(); val x = data.readInt(); val y = data.readInt()
                    val down = data.readInt() != 0
                    emulation { NativeBridge.nativeSetInput(buttons, x, y, down) }
                }
                RoomWire.IS_BOOTED -> output.writeInt(emulation { NativeBridge.nativeIsBooted() })
                RoomWire.EXPORT_SAVE -> {
                    // Destination is chosen here, never supplied by a peer or Binder caller.
                    val saves=java.io.File(filesDir,"gold-hle/saves").apply { check(isDirectory || mkdirs()) }
                    val destination=java.io.File(saves,"cartridge-${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}.pending")
                    val result=emulation { NativeBridge.nativeExportRoomSave(destination.absolutePath) }
                    check(result>=0) { "Cartridge save export failed" }
                    val complete=java.io.File(saves,destination.nameWithoutExtension+".sav")
                    if(result>0)java.nio.file.Files.move(destination.toPath(),complete.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                    RoomWire.writeText(output,if(result>0)complete.absolutePath else "")
                }
            }
            check(output.dataSize() <= RoomWire.MAX_REPLY) { "Room reply too large" }
            return true
        }
    }
}

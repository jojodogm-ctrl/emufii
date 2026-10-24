package eu.emufii.dsnemul

import android.app.Service
import android.app.Application
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.Process
import android.os.SharedMemory
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** A disposable process owns all N emulated consoles and their globals. */
class NemulEngineService : Service() {
    @Volatile private var state = 0
    @Volatile private var error = ""
    @Volatile private var seat = 1
    @Volatile private var nativeStarted = false
    private val admitted = AtomicBoolean(false)
    private val closing = AtomicBoolean(false)
    private val startupFinished = CountDownLatch(1)
    private val frames = Executors.newSingleThreadScheduledExecutor()
    private var shared: SharedMemory? = null
    private var memory: ByteBuffer? = null
    private var proxy: SilverUdpProxy? = null
    private var roundDir: File? = null

    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) { reply?.writeString(NemulRpc.DESCRIPTOR); return true }
            if (code !in NemulRpc.START..NemulRpc.STOP) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(NemulRpc.DESCRIPTOR)
            // exported=false + same UID enforce that external apps cannot submit plans or input.
            check(Binder.getCallingUid() == Process.myUid())
            when (code) {
                NemulRpc.START -> {
                    val request = NemulRpc.readRequest(data)
                    data.enforceNoDataAvail()
                    val allowed = !closing.get() && request.valid() && admitted.compareAndSet(false, true)
                    reply?.writeNoException(); reply?.writeInt(if (allowed) 1 else 0)
                    if (allowed) launch(request) else request.destroySecrets()
                }
                NemulRpc.STATUS -> {
                    if (state == 1 && !closing.get() && NemulNative.running() == 0)
                        fail("La partie SILVER a été interrompue.")
                    reply?.writeNoException(); reply?.writeInt(state)
                    reply?.writeLong(if (nativeStarted && !closing.get())
                        runCatching { NemulNative.frames(seat - 1) }.getOrDefault(0) else 0)
                    reply?.writeString(error)
                }
                NemulRpc.MEMORY -> { reply?.writeNoException(); reply?.writeTypedObject(shared, 0) }
                NemulRpc.INPUT -> {
                    val keys = data.readInt(); val x = data.readInt(); val y = data.readInt()
                    val down = data.readInt() != 0; val mic = data.readInt()
                    data.enforceNoDataAvail()
                    if (nativeStarted && !closing.get()) NemulNative.input(keys and 4095,
                        x.coerceIn(0, 255), y.coerceIn(0, 191), down, mic.coerceIn(0, 255))
                    reply?.writeNoException()
                }
                NemulRpc.AUDIO -> {
                    val buffer = ShortArray(2048)
                    val pairs = if (state == 1 && !closing.get()) NemulNative.audio(buffer, 1024) else 0
                    val bytes = ByteArray(pairs.coerceIn(0, 1024) * 4) { index ->
                        (buffer[index / 2].toInt() shr (8 * (index % 2))).toByte()
                    }
                    reply?.writeNoException(); reply?.writeByteArray(bytes)
                }
                NemulRpc.STOP -> { reply?.writeNoException(); retire() }
            }
            return true
        }
    }
    override fun onCreate() {
        super.onCreate()
        check(Application.getProcessName() == "$packageName:ds_silver_round") {
            "The native round requires its disposable service process"
        }
        // A killed initialization may leave its private ROM copy behind. Capture
        // the old directory list before a new START can create this round's UUID.
        val staleDirectories = File(cacheDir, "silver-nemul-rounds").listFiles().orEmpty().toList()
        Thread({ staleDirectories.forEach { runCatching { it.deleteRecursively() } } },
            "silver-cache-cleanup").start()
    }
    override fun onBind(intent: Intent): IBinder = binder
    override fun onUnbind(intent: Intent): Boolean { retire(); return false }

    private fun launch(request: NemulRoundRequest) {
        Thread({
            try {
                seat = request.nativeSeat
                val directory = File(cacheDir, "silver-nemul-rounds/${java.util.UUID.randomUUID()}")
                check(directory.mkdirs())
                roundDir = directory
                val rom = File(directory, "cartridge.nds")
                copyRom(request, rom)
                if (closing.get()) return@Thread
                val states = File(directory, "state").apply { check(mkdir()) }
                val shm = SharedMemory.create("emufii-silver-frame", NemulRpc.MEMORY_BYTES)
                shared = shm
                memory = shm.mapReadWrite()
                val relay = SilverUdpProxy(request) { fail("Le relais a été interrompu.") }
                proxy = relay
                relay.start()
                request.destroySecrets() // HKDF keys are already held only by the proxy.
                frames.scheduleAtFixedRate({
                    if (nativeStarted && !closing.get()) runCatching {
                        NemulNative.copyFrame(seat - 1, memory!!)
                    }.onFailure { fail("Le moteur SILVER a été interrompu.") }
                }, 0, 16, TimeUnit.MILLISECONDS)
                nativeStarted = true
                val rc = NemulNative.start(rom.absolutePath, states.absolutePath, request.nativeSeat,
                    request.players, relay.port, request.inputDelay, request.roomCode, request.playerName)
                if (!closing.get() && state != 2) {
                    if (rc == 0) state = 1 else fail("Le moteur SILVER n’a pas pu démarrer (code $rc).")
                }
            } catch (_: Throwable) { fail("La préparation de la partie SILVER a échoué.") }
            finally { request.destroySecrets(); startupFinished.countDown() }
        }, "silver-native-start").start()
    }

    private fun copyRom(request: NemulRoundRequest, output: File) {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        contentResolver.openInputStream(request.romUri).use { input ->
            checkNotNull(input)
            output.outputStream().use { destination ->
                val buffer = ByteArray(256 * 1024)
                while (!closing.get()) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= 512L * 1024 * 1024)
                    digest.update(buffer, 0, count); destination.write(buffer, 0, count)
                }
            }
        }
        check(!closing.get() && total >= 512)
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual == request.expectedRomSha256)
    }

    private fun fail(message: String) { if (!closing.get()) { error = message; state = 2 } }

    private fun retire() {
        if (!closing.compareAndSet(false, true)) return
        state = 3
        frames.shutdownNow()
        proxy?.close()
        // Native initialization and shutdown may be waiting on a remote seat. The
        // fixed deadline always retires this process and does not touch Emufii.
        Handler(Looper.getMainLooper()).postDelayed({
            stopSelf()
            Process.killProcess(Process.myPid())
        }, 500)
        Thread({
            // The unchanged core mutates its thread/console vectors during init.
            // Calling its shutdown concurrently would race those vectors. If init
            // is still waiting on a remote seat, the process deadline owns cleanup.
            if (startupFinished.await(350, TimeUnit.MILLISECONDS)) {
                if (nativeStarted) runCatching { NemulNative.stop() }
                // File I/O must never postpone the process-death deadline.
                runCatching { roundDir?.deleteRecursively() }
            }
        }, "silver-native-stop").start()
    }
    override fun onDestroy() {
        retire()
        memory?.let { SharedMemory.unmap(it) }
        shared?.close()
        super.onDestroy()
    }
}

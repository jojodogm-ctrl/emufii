package eu.emufii.app.ds

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.os.Parcel
import android.os.SharedMemory
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mpds.online.RoomEngine
import com.mpds.online.RoomEngineService
import eu.emufii.dsnemul.DsNemulRuntime
import eu.emufii.dsnemul.NemulEngineService
import eu.emufii.dsnemul.NemulRoundRequest
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Physical ARM64 ABI and disposable-process checks, separate from gameplay verification.
 *
 * GOLD configures the native MKDS carrier without booting a cartridge. SILVER uses a
 * caller-provided NSMB dump in the app's private files directory; no ROM is packaged.
 * By default a silent loopback UDP receiver deliberately leaves initialization waiting
 * for seat 2. This proves preparation, Binder/shared-memory/audio ABI and retirement,
 * not frame progress or multiplayer gameplay. An external admission can additionally
 * require real native frame progress with dsMinFrames.
 */
@RunWith(AndroidJUnit4::class)
class DsRuntimeDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val arguments get() = InstrumentationRegistry.getArguments()

    @Test(timeout = 120_000)
    fun goldConfiguresJniAndRetiresTwoFreshProcesses_withoutBootingGame() {
        var previous: IBinder? = null
        repeat(2) { round ->
            val observer = BoundRound(context, RoomEngineService::class.java)
            var lease: java.io.Closeable? = null
            try {
                val remote = observer.bind()
                previous?.let { assertNotSame("A new round must have a fresh Binder", it, remote) }
                lease = bounded(20) { RoomEngine.open(context) }
                val ticket = bounded(10) {
                    // Native SingleOwnerLaunch accepts custom ROM SHA/size only for
                    // Experimental. MKDS uses its pinned identity at cartridge boot.
                    RoomEngine.nativeConfigureRoom(
                        game = 1, boot = 0, players = 2, seat = 1,
                        bufferTicks = 4, language = 1,
                        epoch = (1_000_001L + round).toString(), nickname = "P1",
                        romSha = "", romBytes = 0L,
                        personalSave = false, roomNames = true,
                        generatedSha = "", preparationDirectory = "",
                    )
                }
                assertTrue("Native MKDS configuration must return a carrier ticket", ticket > 0)
                assertEquals(0, bounded(5) { RoomEngine.nativeIsBooted() })
                assertTrue(bounded(5) { RoomEngine.nativeSingleOwnerError() }.length <= 4096)
                val samples = ShortArray(2048)
                val pairs = bounded(5) { RoomEngine.nativeReadSingleOwnerAudio(samples, 1024) }
                assertTrue("Audio reply must fit its stereo destination", pairs in 0..1024)
                assertEquals(0, bounded(5) {
                    RoomEngine.nativeReadSingleOwnerAudio(ShortArray(1), 1024)
                })
                bounded(20) { checkNotNull(lease).close() }
                lease = null
                observer.assertDead(20)
                previous = remote
            } finally {
                try { lease?.let { bounded(20) { it.close() } } }
                finally { observer.close() }
            }
        }
    }

    @Test(timeout = 120_000)
    fun goldSdk500ConfiguresJniAndRetiresTwoFreshProcesses_withoutBootingGame() {
        var previous: IBinder? = null
        repeat(2) { round ->
            val observer = BoundRound(context, RoomEngineService::class.java)
            var lease: java.io.Closeable? = null
            try {
                val remote = observer.bind()
                previous?.let { assertNotSame("A new round must have a fresh Binder", it, remote) }
                lease = bounded(20) { RoomEngine.open(context) }
                val ticket = bounded(10) {
                    // Native SingleOwnerLaunch accepts custom ROM SHA/size only for
                    // Experimental. MKDS uses its pinned identity at cartridge boot.
                    RoomEngine.nativeConfigureRoom(
                        game = 4, boot = 0, players = 2, seat = 1,
                        bufferTicks = 4, language = 1,
                        epoch = (1_000_001L + round).toString(), nickname = "P1",
                        romSha = "ca4242d7f88a4b8c4c84e28adcebb3ff558e11863c31d3e83c848a4a230715dc", romBytes = 67108864L,
                        personalSave = false, roomNames = false,
                        generatedSha = "", preparationDirectory = "",
                    )
                }
                assertTrue("Native generic SDK500 configuration must return a carrier ticket", ticket > 0)
                assertEquals(0, bounded(5) { RoomEngine.nativeIsBooted() })
                assertTrue(bounded(5) { RoomEngine.nativeSingleOwnerError() }.length <= 4096)
                val samples = ShortArray(2048)
                val pairs = bounded(5) { RoomEngine.nativeReadSingleOwnerAudio(samples, 1024) }
                assertTrue("Audio reply must fit its stereo destination", pairs in 0..1024)
                assertEquals(0, bounded(5) {
                    RoomEngine.nativeReadSingleOwnerAudio(ShortArray(1), 1024)
                })
                bounded(20) { checkNotNull(lease).close() }
                lease = null
                observer.assertDead(20)
                previous = remote
            } finally {
                try { lease?.let { bounded(20) { it.close() } } }
                finally { observer.close() }
            }
        }
    }

    @Test(timeout = 300_000)
    fun silverWaitingInitializationRetiresFreshRounds_withoutClaimingGameplay() {
        val path = arguments.getString("dsRomPath")
        assumeTrue("Provide dsRomPath for a privately supplied NSMB dump", !path.isNullOrBlank())
        assertTrue("The embedded SILVER engine requires ARM64", DsNemulRuntime.supported)
        assertFalse("A test must not replace an already active round", DsNemulRuntime.active.value)
        val rom = privateFile(checkNotNull(path))
        assertTrue("The supplied ROM must exist", rom.isFile)
        assertEquals(NSMB_SHA256, bounded(10) { sha256(rom) })
        val rounds = argumentInt("dsRounds", 2, 1..2)
        val minimumFrames = argumentInt("dsMinFrames", 0, 0..100_000)
        val readySeconds = argumentInt("dsReadyTimeoutSeconds", 45, 1..90)
        val admissionPath = arguments.getString("dsAdmissionPath")
        val suppliedAdmissions = admissionPath?.takeIf { it.isNotBlank() }?.let {
            val file = privateFile(it)
            assertTrue("Admission JSON must exist and be bounded", file.isFile && file.length() in 1..32_768)
            val root = JSONObject(file.readText())
            val array = root.optJSONArray("admissions")
            if (array == null) {
                assertEquals("A single real admission must run once", 1, rounds)
                listOf(root)
            } else {
                assertTrue("Each real round needs a fresh admission", array.length() >= rounds)
                (0 until rounds).map(array::getJSONObject)
            }
        }
        assertTrue("Frame progress requires a real peer admission", minimumFrames == 0 || suppliedAdmissions != null)
        val sink = if (suppliedAdmissions == null) SilentRelay() else null
        var previous: IBinder? = null
        try {
            repeat(rounds) { round ->
                val observer = BoundRound(context, NemulEngineService::class.java)
                try {
                    val remote = observer.bind()
                    previous?.let { assertNotSame("A new round must have a fresh Binder", it, remote) }
                    val request = suppliedAdmissions?.get(round)?.let { admission(it, rom) }
                        ?: loopbackRequest(rom, round)
                    val opened = onMain { DsNemulRuntime.open(context, request) }
                    assertEquals(DsNemulRuntime.LaunchResult.Started, opened)
                    assertTrue(DsNemulRuntime.active.value)
                    await(20, "SILVER must prepare its shared framebuffer") {
                        val status = silverStatus(remote)
                        assertHealthy(status)
                        silverMemory(remote)?.use { memory ->
                            assertEquals(FRAME_MEMORY_BYTES, memory.size)
                            val mapped = memory.mapReadOnly()
                            try { assertEquals(FRAME_MEMORY_BYTES, mapped.capacity()) }
                            finally { SharedMemory.unmap(mapped) }
                            true
                        } ?: false
                    }
                    // Memory is created before dlopen/init. Observe a later status
                    // as well so asynchronous ABI failures cannot count as success.
                    SystemClock.sleep(2000)
                    assertHealthy(silverStatus(remote))
                    val audio = silverAudio(remote)
                    assertTrue("PCM reply must fit 1024 stereo pairs", audio.size <= 4096 && audio.size % 4 == 0)
                    if (minimumFrames > 0) {
                        await(readySeconds, "SILVER must advance at least $minimumFrames native frames") {
                            val status = silverStatus(remote)
                            assertHealthy(status)
                            status.state == 1 && status.frames >= minimumFrames
                        }
                    }
                    assertTrue("Public close must own the active launch", onMain { DsNemulRuntime.requestClose() })
                    observer.assertDead(20)
                    await(5, "active must clear only after native process retirement") {
                        !DsNemulRuntime.active.value
                    }
                    previous = remote
                } finally {
                    try {
                        if (DsNemulRuntime.active.value) {
                            onMain { DsNemulRuntime.requestClose() }
                            observer.assertDead(20)
                            await(5, "SILVER cleanup must release its runtime lease") {
                                !DsNemulRuntime.active.value
                            }
                        }
                    } finally { observer.close() }
                }
            }
        } finally { sink?.close() }
    }

    private fun argumentInt(name: String, default: Int, allowed: IntRange): Int {
        val value = arguments.getString(name)?.toIntOrNull() ?: default
        require(value in allowed) { "$name is outside its allowed range" }
        return value
    }

    private fun privateFile(path: String): File {
        val file = File(path).canonicalFile
        val directory = context.filesDir.canonicalFile
        require(file.toPath().startsWith(directory.toPath())) { "Test inputs must stay inside app-private files" }
        return file
    }

    private fun loopbackRequest(rom: File, round: Int): NemulRoundRequest {
        val random = SecureRandom()
        return NemulRoundRequest(
            Uri.fromFile(rom), NSMB_SHA256, "A1B2C3D4E5F60$round", 1, 2, 4,
            "127.0.0.1", SILENT_RELAY_PORT,
            ByteArray(16).also(random::nextBytes), ByteArray(32).also(random::nextBytes), "P1",
        )
    }

    private fun admission(json: JSONObject, rom: File) = NemulRoundRequest(
        Uri.fromFile(rom), NSMB_SHA256, json.getString("roomCode"),
        json.getInt("nativeSeat"), json.getInt("players"), json.getInt("inputDelay"),
        json.getString("relayAddress"), json.getInt("relayPort"),
        unhex(json.getString("sessionId"), 16), unhex(json.getString("capability"), 32),
        json.optString("playerName", "P1"),
    )

    private fun unhex(value: String, size: Int): ByteArray {
        require(value.length == size * 2 && value.matches(Regex("[0-9a-fA-F]+")))
        return ByteArray(size) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class SilverStatus(val state: Int, val frames: Long, val error: String)
    private fun assertHealthy(status: SilverStatus) {
        assertTrue("SILVER startup failed: ${status.error.take(512)}", status.state in 0..1)
        assertTrue("SILVER returned an unexpected startup error", status.error.isEmpty())
        assertTrue("Native frame count must be nonnegative", status.frames >= 0)
    }

    // Read-only observer of the existing internal Binder ABI. START belongs to
    // NemulGameActivity, launched through the actual public runtime facade.
    private fun silverStatus(remote: IBinder) = silverCall(remote, 2) {
        SilverStatus(it.readInt(), it.readLong(), it.readString().orEmpty())
    }
    private fun silverMemory(remote: IBinder): SharedMemory? = silverCall(remote, 3) {
        it.readTypedObject(SharedMemory.CREATOR)
    }
    private fun silverAudio(remote: IBinder): ByteArray = silverCall(remote, 5) {
        it.createByteArray() ?: byteArrayOf()
    }

    private fun <T> silverCall(remote: IBinder, code: Int, read: (Parcel) -> T): T = bounded(5) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken("eu.emufii.dsnemul.Round")
            check(remote.transact(code, data, reply, 0))
            check(reply.dataSize() <= 8192)
            reply.readException()
            read(reply).also { reply.enforceNoDataAvail() }
        } finally { reply.recycle(); data.recycle() }
    }

    private fun <T> onMain(action: () -> T): T = bounded(5) {
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { result.set(runCatching(action)) }
        result.get().getOrThrow()
    }

    private fun <T> bounded(seconds: Long, action: () -> T): T {
        val worker = Executors.newSingleThreadExecutor { task ->
            Thread(task, "ds-device-check").apply { isDaemon = true }
        }
        val pending = worker.submit(Callable(action))
        return try { pending.get(seconds, TimeUnit.SECONDS) }
        finally { pending.cancel(true); worker.shutdownNow() }
    }

    private fun await(seconds: Int, message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + seconds * 1000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            SystemClock.sleep(100)
        }
        throw AssertionError(message)
    }

    private inner class BoundRound(private val target: Context, service: Class<*>) : AutoCloseable {
        private val intent = Intent(target, service)
        private val ready = CountDownLatch(1)
        private val dead = CountDownLatch(1)
        private val remote = AtomicReference<IBinder>()
        private val failure = AtomicReference<Throwable>()
        private var bound = false
        private val death = IBinder.DeathRecipient { dead.countDown() }
        private val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (!remote.compareAndSet(null, binder)) { close(); return }
                runCatching { binder.linkToDeath(death, 0) }.onFailure {
                    failure.set(it)
                    if (!binder.isBinderAlive) dead.countDown()
                }
                ready.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {
                if (remote.get()?.isBinderAlive == false) dead.countDown()
                // Prevent BIND_AUTO_CREATE from replacing the observed process.
                close()
            }
            override fun onBindingDied(name: ComponentName) {
                if (remote.get()?.isBinderAlive == false) dead.countDown()
                close()
            }
            override fun onNullBinding(name: ComponentName) {
                failure.set(IllegalStateException("Round service returned a null Binder"))
                ready.countDown()
                close()
            }
        }
        fun bind(): IBinder {
            onMain { bound = target.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
            check(bound) { "Round service binding was rejected" }
            check(ready.await(10, TimeUnit.SECONDS)) { "Round service binding timed out" }
            failure.get()?.let { throw it }
            return checkNotNull(remote.get())
        }
        fun assertDead(seconds: Long) {
            assertTrue("Round process must acknowledge retirement with Binder death", dead.await(seconds, TimeUnit.SECONDS))
            assertFalse("Retired Binder must no longer be alive", remote.get().isBinderAlive)
        }
        override fun close() {
            // Framework invokes connection callbacks on main; do not recursively
            // runOnMainSync when removing the observer from those callbacks.
            val release = {
                if (bound) { runCatching { target.unbindService(connection) }; bound = false }
                remote.get()?.let { runCatching { it.unlinkToDeath(death, 0) } }
            }
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) release()
            else onMain(release)
        }
    }

    /** Consume ciphertext without manufacturing native replies or a fake peer. */
    private class SilentRelay : AutoCloseable {
        private val live = AtomicBoolean(true)
        private val socket = DatagramSocket(InetSocketAddress(
            InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), SILENT_RELAY_PORT,
        )).apply { soTimeout = 250 }
        private val receiver = Thread({
            while (live.get()) {
                try { socket.receive(DatagramPacket(ByteArray(4096), 4096)) }
                catch (_: SocketTimeoutException) { }
                catch (failure: Exception) { if (live.get()) throw failure }
            }
        }, "ds-device-silent-relay").apply { isDaemon = true; start() }
        override fun close() { live.set(false); socket.close(); receiver.join(1000) }
    }

    companion object {
        private const val NSMB_SHA256 = "dd2e519a9e46134e213e6e63dd2b478d9a258627769b777baddb2316dc21b81d"
        private const val FRAME_MEMORY_BYTES = 8 + 256 * 192 * 4 * 2
        private const val SILENT_RELAY_PORT = 49077
    }
}

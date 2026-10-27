package eu.emufii.app.ds

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.net.Uri
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.mpds.online.DsView
import com.mpds.online.HleGameActivity
import com.mpds.online.RoomEngine
import com.mpds.online.RoomEngineService
import eu.emufii.app.library.Console
import eu.emufii.app.library.Rom
import eu.emufii.app.network.CoordinatorClient
import eu.emufii.app.profile.ProfileStore
import eu.emufii.dshle.DsHleRuntime
import eu.emufii.dsnemul.DsNemulRuntime
import java.io.File
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Optional physical GOLD checks with a privately supplied cartridge.
 * The full pipeline requires a real admitted PC guest and uses Emufii's manager,
 * signed adapter and actual start button. The preparation-failure check exercises
 * local automatic cleanup without a coordinator or native process reservation.
 * No ROM, preparation state, coordinator URL or TLS exception is embedded here.
 */
@RunWith(AndroidJUnit4::class)
class DsGoldDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test(timeout = 90_000)
    fun goldHostPreparationFailure_automaticallyReleasesRuntimeAndActivity() {
        val supplied = InstrumentationRegistry.getArguments().getString("dsGoldRomPath")
        assumeTrue("Provide dsGoldRomPath for the local GOLD cleanup check", !supplied.isNullOrBlank())
        assertTrue(DsHleRuntime.supported)
        assertFalse("The test must not replace an active GOLD round", DsHleRuntime.active.value)
        assertFalse("The test must not replace an active SILVER round", DsNemulRuntime.active.value)
        val file = privateFile(checkNotNull(supplied))
        assertTrue("A privately supplied MPDS dump must exist", file.isFile)
        val identity = runBlocking { withTimeout(15_000) { DsCatalog.assess(context, Uri.fromFile(file)) } }
        assertEquals(PATCHED_MPDS_SHA256, identity.sha256)
        val enteredPreparation = CountDownLatch(1)
        val prepareCalls = AtomicInteger()
        val closeCalls = AtomicInteger()
        val unexpectedCalls = AtomicInteger()
        val activity = AtomicReference<HleGameActivity>()
        val preparedIdentity = AtomicReference<Pair<String, Long>>()
        val access = object : DsHleRuntime.HostAccess {
            override fun prepare(romSha256: String, romBytes: Long): DsHleRuntime.HostPlan {
                prepareCalls.incrementAndGet()
                preparedIdentity.set(romSha256 to romBytes)
                activity.set(onMain { checkNotNull(hostActivity()) })
                enteredPreparation.countDown()
                throw IllegalStateException("device-test-host-preparation-rejected")
            }
            override fun gatewayTicket(generation: Long, room: String, seat: Int, role: String,
                expiresAtUnixMs: Long): String = unexpected()
            override fun openPreparation(seat: Int, kind: String, bytes: Long,
                sha256: String): InputStream = unexpected()
            override fun publish(generation: Long) { unexpected<Unit>() }
            override fun close() { closeCalls.incrementAndGet() }
            private fun <T> unexpected(): T {
                unexpectedCalls.incrementAndGet()
                throw AssertionError("Preparation failure must precede every transport/asset operation")
            }
        }
        var started = false
        try {
            val opened = onMain {
                DsHleRuntime.openHost(context, DsHleRuntime.HostRequest(
                    "FAULT-TEST", "HostP1", Uri.fromFile(file), "mpds", 2,
                ), access)
            }
            assertEquals(DsHleRuntime.LaunchResult.Started, opened)
            started = true
            assertTrue("The real host worker must attempt preparation", enteredPreparation.await(20, TimeUnit.SECONDS))
            assertEquals(PATCHED_MPDS_SHA256 to file.length(), preparedIdentity.get())
            await(30, { "Preparation failure must automatically retire the public runtime" }) {
                !DsHleRuntime.active.value
            }
            await(5, { "Preparation failure must finish its owning Activity" }) {
                onMain { activity.get().let { it.isFinishing || it.isDestroyed } && hostActivity() == null }
            }
            assertEquals("The failed plan must be attempted once", 1, prepareCalls.get())
            assertEquals("HostAccess must be closed exactly once", 1, closeCalls.get())
            assertEquals("No gateway, publish or preparation download may follow failure", 0, unexpectedCalls.get())
            Log.i(TAG, "GOLD_PREPARATION_FAILURE_RETIRED closeCalls=${closeCalls.get()}")
        } finally {
            // Assertions above observe automatic cleanup before this fallback.
            // A failing old implementation must still leave the device usable.
            if (started && DsHleRuntime.active.value) {
                onMain { DsHleRuntime.requestClose() }
                await(20, { "Cleanup fallback could not release the failed GOLD launch" }) {
                    !DsHleRuntime.active.value
                }
            }
        }
    }

    @Test(timeout = 360_000)
    fun goldFullPipelineWithPc_bootsAndRendersThenRetires() {
        val supplied = InstrumentationRegistry.getArguments().getString("dsGoldRomPath")
        assumeTrue("Provide dsGoldRomPath and a real PC guest for this optional test", !supplied.isNullOrBlank())
        assertTrue("The native GOLD engine requires ARM64", DsHleRuntime.supported)
        assertFalse("The test must not replace an active GOLD round", DsHleRuntime.active.value)
        assertFalse("The test must not replace an active SILVER round", DsNemulRuntime.active.value)
        val file = privateFile(checkNotNull(supplied))
        assertTrue("A privately supplied MPDS dump must exist", file.isFile)
        val rom = Rom(Uri.fromFile(file), "patched.nds", TEST_TITLE, Console.DS, productCode = "A8TE")
        val identity = runBlocking { withTimeout(15_000) { DsCatalog.assess(context, rom.uri) } }
        assertEquals(PATCHED_MPDS_SHA256, identity.sha256)
        assertEquals(DsTier.GOLD, identity.tier)
        assertEquals("mpds", identity.profile?.gameId)
        val spec = DsCatalog.choose(identity, 2)
        assertEquals(1, spec.instanceCount)
        assertTrue("Application identity must already be initialized", CoordinatorClient.identityKey?.matches(Regex("[0-9a-f]{64}")) == true)
        // Keep the saved user profile untouched. Its existing identity/key owns
        // the test session; only this in-memory display name is changed.
        val profile = ProfileStore(context).profile.value.copy(name = "HostP1")
        val manager = DsSessionManager.get(context)
        assertEquals("A test must not replace an existing DS session", null, manager.session.value)
        val observer = NativeDeathObserver()
        var created = false
        try {
            managed(60_000) { manager.create(rom, identity, 2, private = false, profile = profile).getOrThrow() }
            created = true
            val session = checkNotNull(manager.session.value)
            assertEquals(spec, session.spec)
            assertTrue(session.host)
            // Only the ordinary public session code is printed for the PC test
            // participant. Bearers, invitation capabilities and tickets are not.
            Log.i(TAG, "GOLD_SESSION code=${session.code} title=$TEST_TITLE")
            observer.bind()
            managed(30_000) { manager.launchGame().getOrThrow() }
            assertTrue(DsHleRuntime.active.value)

            var gameActivity: HleGameActivity? = null
            var lastLobby = ""
            await(90, { "Real GOLD guest did not enable start: ${lastLobby.take(512)}" }) {
                requireLive(manager)
                onMain {
                    val activity = hostActivity()
                    gameActivity = activity
                    if (activity == null) false else {
                        lastLobby = activity.findViewById<TextView>(com.mpds.online.R.id.lobbyStatus).text.toString()
                        activity.findViewById<Button>(com.mpds.online.R.id.joinButton).isEnabled
                    }
                }
            }
            onMain {
                val activity = checkNotNull(gameActivity)
                val button = activity.findViewById<Button>(com.mpds.online.R.id.joinButton)
                check(button.isEnabled && !activity.isFinishing && !activity.isDestroyed)
                check(button.performClick())
            }
            Log.i(TAG, "GOLD_START_CLICKED code=${session.code}")
            var lastNative = "Native lease not opened yet"
            await(90, { "Native GOLD cartridge did not boot: ${lastNative.take(512)}; lobby=${lastLobby.take(512)}" }) {
                requireLive(manager)
                lastLobby = onMain {
                    hostActivity()?.findViewById<TextView>(com.mpds.online.R.id.lobbyStatus)?.text?.toString().orEmpty()
                }
                try {
                    val booted = bounded(5) { RoomEngine.nativeIsBooted() }
                    lastNative = "booted=$booted"
                    booted == 1
                } catch (failure: IllegalStateException) {
                    // Asset commitment precedes creation/binding of the native
                    // lease; these two pending states are expected until then.
                    lastNative = failure.message.orEmpty()
                    if (lastNative !in setOf("RoomEngine requires an open lease", "Room lease is not ready")) throw failure
                    false
                }
            }
            await(15, { "The existing DsView did not report frame progress" }) {
                requireLive(manager)
                onMain {
                    val activity = hostActivity()
                    activity != null &&
                        activity.findViewById<View>(com.mpds.online.R.id.lobbyRoot).visibility == View.GONE &&
                        activity.findViewById<DsView>(com.mpds.online.R.id.dsView).fps > 0f
                }
            }
            observer.assertAlive()
            // Observe the real renderer for five seconds before capturing its
            // native framebuffer. No extra nativeRunFrame call is made.
            repeat(10) {
                SystemClock.sleep(500)
                requireLive(manager)
                assertEquals(1, bounded(5) { RoomEngine.nativeIsBooted() })
            }
            val top = IntArray(256 * 192)
            val bottom = IntArray(top.size)
            bounded(5) { RoomEngine.nativeGetFramebuffer(top, bottom) }
            val colours = top.asSequence().map { it and 0x00ffffff }.distinct().take(4).count()
            assertTrue("The booted game must produce a nonuniform top framebuffer", colours >= 4)
            val nativeError = bounded(5) { RoomEngine.nativeSingleOwnerError() }
            assertTrue("Native GOLD reported an error: ${nativeError.take(512)}", nativeError.isEmpty())
            val fps = onMain {
                checkNotNull(hostActivity()).findViewById<DsView>(com.mpds.online.R.id.dsView).fps
            }
            assertTrue("The existing renderer must continue advancing", fps > 0f)
            val screenshot = saveFrame(top, bottom)
            val report = JSONObject().put("sessionCode", session.code).put("mode", "gold")
                .put("gameId", spec.gameId).put("romSha256", identity.sha256)
                .put("nativeBooted", true).put("rendererFps", fps.toDouble())
                .put("topDistinctColoursAtLeast", colours).put("framebufferPng", screenshot.name)
            File(context.filesDir, "ds-gold-device-report.json").writeText(report.toString(2))
            Log.i(TAG, "GOLD_NATIVE_BOOTED code=${session.code} fps=$fps framebuffer=${screenshot.name}")
            repeat(10) {
                SystemClock.sleep(500)
                requireLive(manager)
                assertEquals(1, bounded(5) { RoomEngine.nativeIsBooted() })
            }
            managed(60_000) { manager.leave().getOrThrow() }
            observer.assertDead()
            assertFalse(DsHleRuntime.active.value)
            assertEquals(null, manager.session.value)
            report.put("binderDeathConfirmed", true).put("managerLeaseReleased", true)
            File(context.filesDir, "ds-gold-device-report.json").writeText(report.toString(2))
            Log.i(TAG, "GOLD_RETIRED code=${session.code}")
        } finally {
            try {
                if (created && manager.session.value != null) managed(60_000) { manager.leave().getOrThrow() }
            } finally { observer.close() }
        }
    }

    /** Android-vs-Android GOLD host for emulated Thors: the same manager and real
     * start button as above, but the guest is a second Emufii device joining from
     * its Open sessions list, and the round is held so both screens can be driven
     * and captured from outside (adb). Optional; needs dsGoldRomPath. */
    @Test(timeout = 3_600_000)
    fun goldHostHoldsRoundForAndroidGuest() {
        val arguments = InstrumentationRegistry.getArguments()
        val supplied = arguments.getString("dsGoldRomPath")
        assumeTrue("Provide dsGoldRomPath and a second Emufii device as guest", !supplied.isNullOrBlank())
        assertTrue("The native GOLD engine requires ARM64", DsHleRuntime.supported)
        assertFalse(DsHleRuntime.active.value)
        val product = arguments.getString("dsGoldProductCode") ?: "A8TE"
        val holdSeconds = (arguments.getString("dsGoldHoldSeconds") ?: "600").toInt().coerceIn(10, 3000)
        val file = privateFile(checkNotNull(supplied))
        val rom = Rom(Uri.fromFile(file), file.name, TEST_TITLE, Console.DS, productCode = product)
        val identity = runBlocking { withTimeout(60_000) { DsCatalog.assess(context, rom.uri) } }
        assertEquals(DsTier.GOLD, identity.tier)
        val profile = ProfileStore(context).profile.value.copy(name = "ThorA")
        val manager = DsSessionManager.get(context)
        assertEquals("A test must not replace an existing DS session", null, manager.session.value)
        var created = false
        try {
            managed(120_000) { manager.create(rom, identity, 2, private = false, profile = profile).getOrThrow() }
            created = true
            val session = checkNotNull(manager.session.value)
            Log.i(TAG, "GOLD_HOLD_SESSION code=${session.code} game=${identity.profile?.gameId}")
            managed(120_000) { manager.launchGame().getOrThrow() }
            var lobby = ""
            await(900, { "Android guest did not enable start: ${lobby.take(512)}" }) {
                requireLive(manager)
                onMain {
                    val activity = hostActivity()
                    if (activity == null) false else {
                        lobby = activity.findViewById<TextView>(com.mpds.online.R.id.lobbyStatus).text.toString()
                        activity.findViewById<Button>(com.mpds.online.R.id.joinButton).isEnabled
                    }
                }
            }
            Log.i(TAG, "GOLD_HOLD_GUEST_READY lobby=${lobby.take(200)}")
            onMain { check(checkNotNull(hostActivity()).findViewById<Button>(com.mpds.online.R.id.joinButton).performClick()) }
            Log.i(TAG, "GOLD_HOLD_START_CLICKED code=${session.code}")
            await(900, { "Native GOLD cartridge did not boot; lobby=${lobby.take(512)}" }) {
                requireLive(manager)
                lobby = onMain { hostActivity()?.findViewById<TextView>(com.mpds.online.R.id.lobbyStatus)?.text?.toString().orEmpty() }
                // ARM-translated emulators answer slowly while the core is busy.
                try { bounded(60) { RoomEngine.nativeIsBooted() } == 1 }
                catch (_: java.util.concurrent.TimeoutException) { false }
                catch (failure: IllegalStateException) {
                    if (failure.message !in setOf("RoomEngine requires an open lease", "Room lease is not ready")) throw failure
                    false
                }
            }
            Log.i(TAG, "GOLD_HOLD_BOOTED code=${session.code}")
            val until = SystemClock.elapsedRealtime() + holdSeconds * 1000L
            while (SystemClock.elapsedRealtime() < until) {
                requireLive(manager)
                val fps = onMain { hostActivity()?.findViewById<DsView>(com.mpds.online.R.id.dsView)?.fps ?: -1f }
                Log.i(TAG, "GOLD_HOLD_TICK fps=$fps error=${runCatching { bounded(30) { RoomEngine.nativeSingleOwnerError() } }.getOrDefault("slow").take(200)}")
                SystemClock.sleep(10_000)
            }
        } finally {
            if (created && manager.session.value != null) managed(60_000) { manager.leave().getOrThrow() }
        }
    }

    private fun requireLive(manager: DsSessionManager) {
        val session = checkNotNull(manager.session.value) { "Emufii unexpectedly released its DS session" }
        check(session.error == null) { "Emufii session error: ${session.error}" }
        check(DsHleRuntime.active.value) { "The real GOLD runtime retired before verification" }
    }

    private fun hostActivity(): HleGameActivity? {
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        // On Thor, the bottom-display Activity can be resumed while Android
        // reports the top HleGameActivity as STOPPED. Its host lobby and start
        // button remain the owners of this round, so inspect that stage too.
        return sequenceOf(Stage.RESUMED, Stage.PAUSED, Stage.STARTED, Stage.STOPPED)
            .flatMap { monitor.getActivitiesInStage(it).asSequence() }
            .filterIsInstance<HleGameActivity>().firstOrNull { !it.isFinishing && !it.isDestroyed }
    }

    private fun privateFile(path: String): File = File(path).canonicalFile.also {
        require(it.toPath().startsWith(context.filesDir.canonicalFile.toPath())) { "Test ROM must stay in app-private files" }
    }

    private fun saveFrame(top: IntArray, bottom: IntArray): File {
        val bitmap = Bitmap.createBitmap(256, 384, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.setPixels(top, 0, 256, 0, 0, 256, 192)
            bitmap.setPixels(bottom, 0, 256, 0, 192, 256, 192)
            File(context.filesDir, "ds-gold-frame.png").also { file ->
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            }
        } finally { bitmap.recycle() }
    }

    private fun <T> managed(timeoutMs: Long, action: suspend () -> T): T = runBlocking {
        withTimeout(timeoutMs) { withContext(Dispatchers.Main.immediate) { action() } }
    }

    private fun <T> onMain(action: () -> T): T = bounded(5) {
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { result.set(runCatching(action)) }
        result.get().getOrThrow()
    }

    private fun <T> bounded(seconds: Long, action: () -> T): T {
        val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "gold-device-check").apply { isDaemon = true } }
        val pending = worker.submit(Callable(action))
        return try { pending.get(seconds, TimeUnit.SECONDS) }
        catch (failure: ExecutionException) { throw (failure.cause ?: failure) }
        finally { pending.cancel(true); worker.shutdownNow() }
    }

    private fun await(seconds: Int, message: () -> String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + seconds * 1000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            SystemClock.sleep(150)
        }
        throw AssertionError(message())
    }

    private inner class NativeDeathObserver : AutoCloseable {
        private val ready = CountDownLatch(1)
        private val dead = CountDownLatch(1)
        private val binder = AtomicReference<IBinder>()
        private var bound = false
        private val death = IBinder.DeathRecipient { dead.countDown() }
        private val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (!binder.compareAndSet(null, service)) { close(); return }
                service.linkToDeath(death, 0)
                ready.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {
                if (binder.get()?.isBinderAlive == false) dead.countDown()
                close()
            }
            override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
        }
        fun bind() {
            onMain { bound = context.bindService(Intent(context, RoomEngineService::class.java), connection, Context.BIND_AUTO_CREATE) }
            check(bound && ready.await(10, TimeUnit.SECONDS)) { "Unable to observe native GOLD process" }
        }
        fun assertDead() {
            assertTrue("Native GOLD process must confirm retirement by Binder death", dead.await(20, TimeUnit.SECONDS))
            assertFalse(binder.get().isBinderAlive)
        }
        fun assertAlive() {
            assertEquals("The observed process must survive through actual boot", 1L, dead.count)
            assertTrue("The booted native process must still own the observed Binder", binder.get().isBinderAlive)
        }
        override fun close() {
            val release = {
                if (bound) { runCatching { context.unbindService(connection) }; bound = false }
                binder.get()?.let { runCatching { it.unlinkToDeath(death, 0) } }
            }
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) release() else onMain(release)
        }
    }

    companion object {
        private const val TAG = "EMUFII-DS-TEST"
        private const val TEST_TITLE = "Emufii DS physical GOLD"
        private const val PATCHED_MPDS_SHA256 = "07b4ac7550e5ed876dd83578d49441712bbbd8127cdd60155e4d1a87604a5af7"
    }
}

package eu.emufii.app.wg

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import eu.emufii.app.MainActivity
import eu.emufii.app.network.CoordinatorClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Manual two-device test, opt-in with `-e spike true`: creates a real session on the hosted coordinator. */
class WgTunnelSpikeTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val client = CoordinatorClient()

    @Before
    fun onlyOnDemand() {
        assumeTrue(
            "manual spike: rerun with -e spike true",
            args.getString("spike") == "true"
        )
    }

    private companion object {
        const val HOST_ID = "E7K29QM4XR8T"
        const val GUEST_ID = "0123456789AB"
    }

    private fun bringAppToForeground() {
        // Android 12+ refuses startForegroundService from the background.
        ctx.startActivity(
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        Thread.sleep(2_500)
    }

    private fun ping(target: String): Pair<Boolean, String> {
        val p = ProcessBuilder("/system/bin/ping", "-c", "3", "-W", "2", target)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        return (p.waitFor() == 0) to out
    }

    private fun startTunnelFor(code: String, profileId: String): WgTunnelInfo = runBlocking {
        val info = client
            .claimAddress(code, EmufiiWgManager.publicKey(ctx), "spike", profileId)
            .getOrThrow()
        println("SPIKE config:\n" + WgConfig.renderRedacted(info))
        bringAppToForeground()
        EmufiiWgManager.start(ctx, code, info)

        val online = withTimeoutOrNull(45_000) {
            EmufiiWgManager.state.first { it is WgState.Online || it is WgState.Error }
        }
        assertNotNull("the tunnel never reached a terminal state", online)
        assertTrue("unexpected state: $online", online is WgState.Online)
        assertEquals(info.address, (online as WgState.Online).ip)
        info
    }

    @Test
    fun hostBringsTunnelUp() {
        val code = args.getString("spikeCode") ?: "SPIKE-01"
        runBlocking { client.deleteSession(code, null) }
        val created = runBlocking {
            client.createSession(code, null, "Spike", "Host", HOST_ID).getOrThrow()
        }
        runBlocking { client.heartbeat(code, HOST_ID, "Host") }
        println("SPIKE session=${created.code} subnet=${created.subnet}")

        val info = startTunnelFor(code, HOST_ID)
        println("SPIKE host_address=${info.address}")

        val (ok, out) = ping("10.67.0.1")
        println("SPIKE ping relais:\n$out")
        assertTrue("the relay 10.67.0.1 is unreachable inside the tunnel\n$out", ok)

        // Hold past the peer TTL to prove the heartbeat keeps the route alive.
        val hold = args.getString("spikeHoldSeconds")?.toIntOrNull() ?: 0
        repeat(hold / 5) {
            Thread.sleep(5_000)
            runBlocking { client.heartbeat(code, HOST_ID, "Host") }
        }
        if (hold > 0) {
            val (still, o) = ping("10.67.0.1")
            assertTrue("the host tunnel went down while waiting\n$o", still)
        }
    }

    @Test
    fun guestReachesHost() {
        val code = args.getString("spikeCode") ?: "SPIKE-01"
        val hostIp = requireNotNull(args.getString("spikeHostIp")) {
            "pass -e spikeHostIp <address advertised by the host>"
        }

        val info = startTunnelFor(code, GUEST_ID)
        println("SPIKE guest_address=${info.address}")

        val (relayOk, relayOut) = ping("10.67.0.1")
        assertTrue("the relay is unreachable\n$relayOut", relayOk)

        val (ok, out) = ping(hostIp)
        println("SPIKE ping host:\n$out")
        assertTrue("host $hostIp is unreachable inside the tunnel\n$out", ok)

        val hold = args.getString("spikeHoldSeconds")?.toIntOrNull() ?: 0
        repeat(hold / 5) {
            Thread.sleep(5_000)
            runBlocking { client.heartbeat(code, GUEST_ID, "Guest") }
        }
        if (hold > 0) {
            val (still, o) = ping(hostIp)
            assertTrue("the host became unreachable while waiting\n$o", still)
        }
    }
}

package eu.emufii.app.azahar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A netplay plan has to survive Emufii being killed, launching an emulator is the
 * most memory-hungry thing a phone does, and Android took Emufii down every single
 * time on the test bench, but it must not survive the session that
 * justified it. This is that rule about time, tested by winding the clock rather
 * than by waiting.
 */
class PlanCodecTest {

    private val plan = NetplayPlan(
        role = NetplayPlan.Role.Guest,
        ip = "10.67.1.2",
        port = 24872,
        roomName = "Emufii ABC-123",
        preferredGame = "Balatro"
    )

    @Test
    fun `a plan comes back exactly as it went in`() {
        val restored = PlanCodec.decode(PlanCodec.encode(plan, now = 1_000), now = 1_000)
        assertEquals(plan, restored)
    }

    @Test
    fun `it survives a while, then stops meaning anything`() {
        // A real wall-clock instant: an armed_at of zero is how a *missing*
        // timestamp reads, and the decoder is right to refuse that.
        val armedAt = 1_785_000_000_000L
        val encoded = PlanCodec.encode(plan, now = armedAt)
        assertNotNull(PlanCodec.decode(encoded, now = armedAt))
        assertNotNull(PlanCodec.decode(encoded, now = armedAt + PlanCodec.TTL_MS - 1))
        // Past the window, a forgotten plan would type an address into whatever
        // room the player happens to be setting up next.
        assertNull(PlanCodec.decode(encoded, now = armedAt + PlanCodec.TTL_MS + 1))
    }

    @Test
    fun `a plan with no timestamp is treated as no plan`() {
        // What a truncated or hand-edited entry looks like.
        assertNull(PlanCodec.decode("""{"role":"Host","ip":"1.2.3.4","port":1,"armed_at":0}""", now = 5))
    }

    @Test
    fun `a clock that moved under us is not trusted`() {
        val encoded = PlanCodec.encode(plan, now = 10_000)
        assertNull(PlanCodec.decode(encoded, now = 9_000))
    }

    @Test
    fun `the optional halves stay optional`() {
        val bare = NetplayPlan(role = NetplayPlan.Role.Host, ip = "10.67.9.2", port = 1234)
        val restored = PlanCodec.decode(PlanCodec.encode(bare, now = 5), now = 5)!!
        assertEquals(bare, restored)
        assertNull(restored.roomName)
        assertNull(restored.preferredGame)
    }

    @Test
    fun `garbage in storage is absence, not a crash`() {
        for (raw in listOf(
            "",
            "{",
            "{}",
            """{"role":"Nobody","ip":"1.2.3.4","port":1,"armed_at":1}""",
            """{"role":"Host","ip":"","port":1,"armed_at":1}""",
            """{"role":"Host","ip":"1.2.3.4","port":1}"""
        )) {
            assertNull(raw, PlanCodec.decode(raw, now = 2))
        }
    }
}

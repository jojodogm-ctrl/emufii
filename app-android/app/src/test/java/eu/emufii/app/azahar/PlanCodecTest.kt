package eu.emufii.app.azahar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

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
        // A real instant: an `armed_at` of zero reads as a missing timestamp.
        val armedAt = 1_785_000_000_000L
        val encoded = PlanCodec.encode(plan, now = armedAt)
        assertNotNull(PlanCodec.decode(encoded, now = armedAt))
        assertNotNull(PlanCodec.decode(encoded, now = armedAt + PlanCodec.TTL_MS - 1))
        // A forgotten plan would type an address into the next room being set up.
        assertNull(PlanCodec.decode(encoded, now = armedAt + PlanCodec.TTL_MS + 1))
    }

    @Test
    fun `a plan with no timestamp is treated as no plan`() {
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

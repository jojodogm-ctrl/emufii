package eu.emufii.app.eden

import eu.emufii.app.netplay.NetplayTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Eden ships several packages, some (the "Optimized" one) not named eden at all. */
class EdenVariantTest {

    @Test
    fun `the Optimized variant is a known variant`() {
        assertTrue(
            "the Optimized nightly must be recognised as an Eden",
            NetplayTarget.EDEN.packages.contains("com.miHoYo.Yuanshen.nightly")
        )
        assertEquals(
            NetplayTarget.EDEN,
            NetplayTarget.forPackage("com.miHoYo.Yuanshen.nightly")
        )
    }

    @Test
    fun `the most recently installed one wins, whatever its rank in the list`() {
        val installed = listOf(
            "dev.eden.eden_emulator" to 1_754_600_000_000L,
            "com.miHoYo.Yuanshen.nightly" to 1_754_800_000_000L
        )
        assertEquals("com.miHoYo.Yuanshen.nightly", pickEden(installed))
    }

    @Test
    fun `on equal dates the list order decides, and the fork comes first`() {
        val same = 1_754_800_000_000L
        val installed = listOf(
            "dev.eden.eden_emulator.emufii" to same,
            "dev.eden.eden_emulator" to same
        )
        assertEquals("dev.eden.eden_emulator.emufii", pickEden(installed))
    }

    @Test
    fun `no variant installed stays a case of its own`() {
        assertNull(pickEden(emptyList()))
    }
}

package eu.emufii.app.netplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetplayUiSupportTest {

    @Test
    fun `the probe never requires an id exclusive to one emulator`() {
        assertFalse(
            "PREFERRED_GAME is Eden-only; probing it fails every Azahar build",
            NetplayUiSupport.PROBE_IDS.contains(NetplayUi.PREFERRED_GAME)
        )
        assertFalse(
            "MENU_MULTIPLAYER is Azahar-only; probing it fails every Eden build",
            NetplayUiSupport.PROBE_IDS.contains(NetplayUi.MENU_MULTIPLAYER)
        )
    }

    @Test
    fun `the probe actually checks something`() {
        // An empty list makes `all {}` vacuously true and turns the guard off in silence.
        assertTrue(NetplayUiSupport.PROBE_IDS.isNotEmpty())
    }
}

package eu.emufii.app.netplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The probe decides whether Emufii refuses to launch, so a wrong id list is a
 * refusal on a build that would have worked. These guard the two ways it breaks.
 */
class NetplayUiSupportTest {

    @Test
    fun `the probe never requires an id exclusive to one emulator`() {
        // Azahar has no dropdown_preferred_game_name and Eden has no
        // menu_multiplayer: requiring either would report "no multiplayer" on an
        // emulator whose dialog is perfectly drivable.
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
        // An empty list makes `all {}` vacuously true, which would silently turn
        // the guard off and bring back the symptom it exists to prevent.
        assertTrue(NetplayUiSupport.PROBE_IDS.isNotEmpty())
    }
}

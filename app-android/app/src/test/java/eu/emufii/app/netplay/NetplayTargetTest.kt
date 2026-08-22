package eu.emufii.app.netplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which emulator an accessibility event belongs to, and whether it has an
 * in-game way into its multiplayer dialog.
 *
 * The service is the one piece of Emufii that reads another app's screen, so the
 * question "is this package one of ours?" has to be exact. A wrong yes means
 * typing into a stranger's window; a wrong no means the automation silently
 * does nothing.
 */
class NetplayTargetTest {

    @Test
    fun `each emulator is recognised by its own packages`() {
        assertEquals(NetplayTarget.AZAHAR, NetplayTarget.forPackage("org.azahar_emu.azahar"))
        assertEquals(NetplayTarget.AZAHAR, NetplayTarget.forPackage("org.azahar_emu.azahar.debug"))
        assertEquals(NetplayTarget.EDEN, NetplayTarget.forPackage("dev.eden.eden_emulator"))
        // The nightly is what most players actually have installed.
        assertEquals(NetplayTarget.EDEN, NetplayTarget.forPackage("dev.eden.eden_emulator.nightly"))
    }

    @Test
    fun `anything else is nobody's`() {
        for (pkg in listOf(
            "me.magnum.melonds",
            "org.dolphinemu.dolphinemu",
            "eu.emufii.app",
            "com.android.settings",
            "",
            // Close enough to be worth pinning: a prefix is not a package.
            "dev.eden",
            "dev.eden.eden_emulator_evil"
        )) {
            assertNull(pkg, NetplayTarget.forPackage(pkg))
        }
    }

    @Test
    fun `both emulators are reachable from the settings hub, and from a running game`() {
        // The two paths to the multiplayer sheet. Every target must declare the
        // settings one, because that is the path Emufii's own button takes: without
        // it the button opens the emulator and leaves the player to go hunting.
        for (target in NetplayTarget.all) {
            assertEquals(target.packages.toString(), NetplayUi.NAV_HOME_SETTINGS, target.homeNavId)
            assertEquals(target.packages.toString(), NetplayUi.HOME_SETTINGS_LIST, target.homeListId)
        }
        // The in-game entry is Azahar's original path; Eden's stable build turns
        // out to carry the same id, which the nightly scout had not seen.
        assertEquals(NetplayUi.MENU_MULTIPLAYER, NetplayTarget.AZAHAR.inGameMenuId)
        assertEquals(NetplayUi.MENU_MULTIPLAYER, NetplayTarget.EDEN.inGameMenuId)
    }

    @Test
    fun `every target records the build its ids were read from`() {
        for (target in NetplayTarget.all) {
            assertTrue(target.packages.toString(), target.packages.isNotEmpty())
            assertTrue(target.uiReadFrom, target.uiReadFrom.isNotBlank())
        }
    }

    @Test
    fun `the shared port is the one both emulators default to`() {
        assertEquals(24872, NetplayUi.DEFAULT_PORT)
    }

    @Test
    fun `ids are qualified with the package that owns them`() {
        assertEquals(
            "dev.eden.eden_emulator.nightly:id/ip_address",
            NetplayUi.id("dev.eden.eden_emulator.nightly", NetplayUi.IP_ADDRESS)
        )
        assertNotNull(NetplayTarget.forPackage("dev.eden.eden_emulator.nightly"))
    }
}

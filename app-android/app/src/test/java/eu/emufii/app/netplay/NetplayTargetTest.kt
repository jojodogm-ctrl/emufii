package eu.emufii.app.netplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetplayTargetTest {

    @Test
    fun `each emulator is recognised by its own packages`() {
        assertEquals(NetplayTarget.AZAHAR, NetplayTarget.forPackage("org.azahar_emu.azahar"))
        assertEquals(NetplayTarget.AZAHAR, NetplayTarget.forPackage("org.azahar_emu.azahar.debug"))
        // Legacy Lime3DS id some Azahar builds still use.
        assertEquals(NetplayTarget.AZAHAR, NetplayTarget.forPackage("io.github.lime3ds.android"))
        assertEquals(NetplayTarget.EDEN, NetplayTarget.forPackage("dev.eden.eden_emulator"))
        assertEquals(NetplayTarget.EDEN, NetplayTarget.forPackage("dev.eden.eden_emulator.nightly")) // what most players have
    }

    @Test
    fun `anything else is nobody's`() {
        for (pkg in listOf(
            "me.magnum.melonds",
            "org.dolphinemu.dolphinemu",
            "eu.emufii.app",
            "com.android.settings",
            "",
            "dev.eden",
            "dev.eden.eden_emulator_evil"
        )) {
            assertNull(pkg, NetplayTarget.forPackage(pkg))
        }
    }

    @Test
    fun `both emulators are reachable from the settings hub, and from a running game`() {
        for (target in NetplayTarget.all) {
            assertEquals(target.packages.toString(), NetplayUi.NAV_HOME_SETTINGS, target.homeNavId)
            assertEquals(target.packages.toString(), NetplayUi.HOME_SETTINGS_LIST, target.homeListId)
        }
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

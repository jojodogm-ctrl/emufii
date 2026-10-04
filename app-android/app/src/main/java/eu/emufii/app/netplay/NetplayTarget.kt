package eu.emufii.app.netplay

import eu.emufii.app.azahar.AzaharPackage

/** Azahar and Eden share the Citra/yuzu netplay dialog; matched by view id since both ship many locales. */
data class NetplayTarget(
    val packages: List<String>,
    /** Null means "start at the sheet", not "unsupported". */
    val inGameMenuId: String?,
    val homeNavId: String? = null,
    val homeListId: String? = null,
    /** The gear and the tab do not always land on the same screen. */
    val extraListIds: List<String> = emptyList(),
    /** Eden's gear is a plain view in one build, a toolbar menu item in another. */
    val homeSettingsButtonIds: List<String> = emptyList(),
    val uiReadFrom: String
) {
    fun owns(pkg: String): Boolean = pkg in packages

    companion object {
        val AZAHAR = NetplayTarget(
            packages = AzaharPackage.candidates,
            inGameMenuId = NetplayUi.MENU_MULTIPLAYER,
            homeNavId = NetplayUi.NAV_HOME_SETTINGS,
            homeListId = NetplayUi.HOME_SETTINGS_LIST,
            uiReadFrom = "azahar-android-vanilla-2126.0-rc5 (read live on the Thor, 2026-08-01)"
        )

        val EDEN = NetplayTarget(
            // Package names from Eden's build.gradle.kts (three flavours x .nightly); EdenLauncher keeps the first installed.
            packages = listOf(
                "dev.eden.eden_emulator.emufii",
                "dev.eden.eden_emulator",
                "dev.eden.eden_emulator.nightly",
                "com.miHoYo.Yuanshen",
                "com.miHoYo.Yuanshen.nightly",
                "dev.legacy.eden_emulator"
            ),
            inGameMenuId = NetplayUi.MENU_MULTIPLAYER,
            homeNavId = NetplayUi.NAV_HOME_SETTINGS,
            homeListId = NetplayUi.HOME_SETTINGS_LIST,
            // On Eden the tab id exists but is never shown: multiplayer is only behind the top-bar gear.
            homeSettingsButtonIds = listOf(NetplayUi.SETTINGS_BUTTON, NetplayUi.MENU_SETTINGS),
            extraListIds = listOf(NetplayUi.SETTINGS_LIST, NetplayUi.LIST_SETTINGS),
            uiReadFrom = "eden-android-1f6734c (stable, read on the Thor 2026-08-01)"
        )

        val all = listOf(AZAHAR, EDEN)

        fun forPackage(pkg: String): NetplayTarget? = all.firstOrNull { it.owns(pkg) }
    }
}

object NetplayUi {

    const val MENU_MULTIPLAYER = "menu_multiplayer"

    // The hub's option cards share ids; only their text tells them apart.
    const val NAV_HOME_SETTINGS = "homeSettingsFragment"
    const val HOME_SETTINGS_LIST = "home_settings_list"
    const val OPTION_TITLE = "option_title"

    val ROW_TITLE_IDS = listOf(OPTION_TITLE, "setting_title")

    const val SETTINGS_BUTTON = "settings_button"
    const val MENU_SETTINGS = "menu_settings"

    const val SETTINGS_LIST = "settings_list"
    const val LIST_SETTINGS = "list_settings"

    // In landscape the sheet clips the bottom: never look these up with a visibility filter.
    const val BTN_CREATE = "btn_create"
    const val BTN_JOIN = "btn_join"
    const val BTN_LOBBY_BROWSER = "btn_lobby_browser"

    const val IP_ADDRESS = "ip_address"
    const val IP_PORT = "ip_port"
    const val USERNAME = "username"
    const val ROOM_NAME = "room_name"
    const val PASSWORD = "password"

    /** Azahar's resources say `prefered_game_name` with one r, Eden's has two. */
    const val PREFERRED_GAME = "dropdown_preferred_game_name"
    const val PREFERRED_GAME_ALT = "dropdown_prefered_game_name"

    val PREFERRED_GAME_IDS = listOf(PREFERRED_GAME, PREFERRED_GAME_ALT)
    const val BTN_CONFIRM = "btn_confirm"

    /** The ENet port both emulators default to, and Citra's long-standing one. */
    const val DEFAULT_PORT = 24872

    fun id(pkg: String, name: String): String = "$pkg:id/$name"
}

package eu.emufii.app.netplay

/**
 * The netplay UI Emufii knows how to drive, and who ships it.
 *
 * Azahar and Eden present the same dialog: same view ids, same three buttons,
 * same default port. Azahar descends from Citra and Eden from yuzu, and both
 * inherited the same Android netplay screens. One automation, not two, and only
 * the package name differs.
 *
 * Ids rather than labels: both ship dozens of locales, so matching on "Join"
 * breaks for anyone not running in English. [uiReadFrom] records which build
 * each set was read from. See `docs/NOTES_NETPLAY.md`.
 */
data class NetplayTarget(
    val packages: List<String>,
    /**
     * The entry that opens the multiplayer sheet from inside a running game, or
     * null when the emulator has none. Null means "start at the sheet", not
     * "unsupported".
     */
    val inGameMenuId: String?,
    /**
     * The bottom-navigation item that opens the emulator's settings hub, and the
     * list it shows: the path to Multiplayer when no game is running.
     */
    val homeNavId: String? = null,
    val homeListId: String? = null,
    /**
     * Other lists the settings hub might be, tried alongside [homeListId]. The
     * gear and the tab do not always land on the same screen.
     */
    val extraListIds: List<String> = emptyList(),
    /**
     * Other ways into the settings hub, tried when [homeNavId] isn't on screen.
     * Eden's is a gear in the top bar: a plain view in one build, a toolbar menu
     * item in another.
     */
    val homeSettingsButtonIds: List<String> = emptyList(),
    /** Which build the ids below were read out of. */
    val uiReadFrom: String
) {
    fun owns(pkg: String): Boolean = pkg in packages

    companion object {
        val AZAHAR = NetplayTarget(
            packages = listOf("org.azahar_emu.azahar", "org.azahar_emu.azahar.debug"),
            inGameMenuId = NetplayUi.MENU_MULTIPLAYER,
            homeNavId = NetplayUi.NAV_HOME_SETTINGS,
            homeListId = NetplayUi.HOME_SETTINGS_LIST,
            uiReadFrom = "azahar-android-vanilla-2126.0-rc5 (read live on the Thor, 2026-08-01)"
        )

        val EDEN = NetplayTarget(
            // Eden ships a build matrix, not two channels: three flavours crossed
            // with a `.nightly` suffix, and two of the names do not contain
            // "eden". Order matters, EdenLauncher keeps the first installed
            // package, so our fork comes first. Names read out of
            // `build.gradle.kts`. See docs/NOTES_NETPLAY.md.
            packages = listOf(
                "dev.eden.eden_emulator.emufii",
                "dev.eden.eden_emulator",
                "dev.eden.eden_emulator.nightly",
                "com.miHoYo.Yuanshen",
                "com.miHoYo.Yuanshen.nightly",
                "dev.legacy.eden_emulator"
            ),
            // The stable build carries an in-game entry after all, where the
            // nightly scout had only found the settings path.
            inGameMenuId = NetplayUi.MENU_MULTIPLAYER,
            homeNavId = NetplayUi.NAV_HOME_SETTINGS,
            homeListId = NetplayUi.HOME_SETTINGS_LIST,
            // On Eden the multiplayer entry only appears behind the gear in the
            // top bar. The tab id exists in the resources but nothing shows it.
            homeSettingsButtonIds = listOf(NetplayUi.SETTINGS_BUTTON, NetplayUi.MENU_SETTINGS),
            extraListIds = listOf(NetplayUi.SETTINGS_LIST, NetplayUi.LIST_SETTINGS),
            uiReadFrom = "eden-android-1f6734c (stable, read on the Thor 2026-08-01)"
        )

        val all = listOf(AZAHAR, EDEN)

        /** Which target, if any, owns the package an accessibility event came from. */
        fun forPackage(pkg: String): NetplayTarget? = all.firstOrNull { it.owns(pkg) }
    }
}

/**
 * View ids of the netplay dialog, identical in Azahar and Eden.
 *
 *   menu_in_game               → [MENU_MULTIPLAYER]  (in-game drawer)
 *   dialog_multiplayer_connect → [BTN_CREATE] / [BTN_JOIN] / [BTN_LOBBY_BROWSER]
 *   dialog_multiplayer_room    → [IP_ADDRESS] / [IP_PORT] / … / [BTN_CONFIRM]
 */
object NetplayUi {

    // In-game drawer
    const val MENU_MULTIPLAYER = "menu_multiplayer"

    // The home hub: a bottom-nav tab, then a list of option cards. The cards
    // share their ids and only their text tells them apart, hence NetplayLabels.
    const val NAV_HOME_SETTINGS = "homeSettingsFragment"
    const val HOME_SETTINGS_LIST = "home_settings_list"
    const val OPTION_TITLE = "option_title"

    /**
     * Every id a settings row's title goes by, across the two hubs. The row is
     * still found by its text; this only says where to read that text from.
     */
    val ROW_TITLE_IDS = listOf(OPTION_TITLE, "setting_title")

    /** The gear in Eden's top bar: a view in one build, a menu item in another. */
    const val SETTINGS_BUTTON = "settings_button"
    const val MENU_SETTINGS = "menu_settings"

    /** The other names a settings list goes by. */
    const val SETTINGS_LIST = "settings_list"
    const val LIST_SETTINGS = "list_settings"

    // Multiplayer bottom sheet, stacked lobby browser / join / create. In
    // landscape the sheet cuts off at the bottom, so the host's button is
    // routinely off screen: never look these up with a visibility filter.
    const val BTN_CREATE = "btn_create"
    const val BTN_JOIN = "btn_join"
    const val BTN_LOBBY_BROWSER = "btn_lobby_browser"

    // Create/join room form (same layout serves both)
    const val IP_ADDRESS = "ip_address"
    const val IP_PORT = "ip_port"
    const val USERNAME = "username"
    const val ROOM_NAME = "room_name"
    const val PASSWORD = "password"

    /**
     * The room's game. Mandatory when hosting on Eden.
     *
     * Two spellings: Azahar's resources say `prefered_game_name` with one r,
     * Eden's has two. Both are tried.
     */
    const val PREFERRED_GAME = "dropdown_preferred_game_name"
    const val PREFERRED_GAME_ALT = "dropdown_prefered_game_name"

    /** Every spelling of the preferred-game dropdown, in lookup order. */
    val PREFERRED_GAME_IDS = listOf(PREFERRED_GAME, PREFERRED_GAME_ALT)
    const val BTN_CONFIRM = "btn_confirm"

    /** The ENet port both emulators default to, and Citra's long-standing one. */
    const val DEFAULT_PORT = 24872

    /** Qualifies a bare id for [android.view.accessibility.AccessibilityNodeInfo] lookup. */
    fun id(pkg: String, name: String): String = "$pkg:id/$name"
}

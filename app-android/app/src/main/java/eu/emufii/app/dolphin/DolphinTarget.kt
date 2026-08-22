package eu.emufii.app.dolphin

/**
 * Dolphin's netplay screen.
 *
 * Kept apart from [eu.emufii.app.netplay.NetplayTarget] because its screen is
 * Compose and exposes no resource ids: every field arrives as a bare
 * `android.widget.EditText`. Addressing here is text and geometry only.
 * See `docs/NOTES_DOLPHIN.md`.
 */
object DolphinTarget {

    /** Release, beta and dev builds all install under the same package. */
    val packages = listOf(
        "org.dolphinemu.dolphinemu",
        "org.dolphinemu.dolphinemu.debug"
    )

    fun owns(pkg: String): Boolean = pkg in packages

    /** The "Netplay" row of the game grid's overflow menu, found by text. */
    const val LABEL_MENU_NETPLAY = "grid_menu_netplay"

    /** The overflow button. Belongs to appcompat, resolves in Dolphin's locale. */
    const val OVERFLOW_DESCRIPTION = "abc_action_menu_overflow_description"

    /**
     * String *names* in Dolphin's own table, resolved through its resources.
     *
     * Names and not values, because Dolphin ships some forty translations and an
     * app's language is per-app since Android 13. Same approach as
     * [eu.emufii.app.netplay.NetplayLabels].
     */
    const val LABEL_NICKNAME = "netplay_nickname_label"
    const val LABEL_IP_ADDRESS = "netplay_ip_address_label"
    const val LABEL_PORT = "netplay_port_label"
    const val LABEL_CONNECTION_TYPE = "netplay_connection_type"
    const val LABEL_DIRECT_CONNECTION = "netplay_connection_type_direct_connection"
    const val LABEL_TRAVERSAL_SERVER = "netplay_connection_type_traversal_server"

    /**
     * The two tabs, which are also the two confirm buttons: Dolphin reuses one
     * string for both. The button is the one wrapped in `android.widget.Button`.
     */
    const val LABEL_ROLE_CONNECT = "netplay_connection_role_connect"
    const val LABEL_ROLE_HOST = "netplay_connection_role_host"

    /**
     * The room's game selector.
     *
     * The host picks the game, and Dolphin restores it from `[NetPlay] Game` in
     * `Dolphin.ini`, so the room opens on whatever this device last chose. Must
     * be set explicitly.
     */
    const val LABEL_GAME = "netplay_game_label"

    /**
     * `DEFAULT_LISTEN_PORT` in `Source/Core/Core/Config/NetplaySettings.cpp`.
     * Unlike Azahar's 24872, the plan must carry this one explicitly.
     */
    const val DEFAULT_PORT = 2626

    /** Which build the screen above was read out of. */
    const val UI_READ_FROM = "dolphin-master-2606-302 (read live on the Thor, 2026-08-15)"
}

package eu.emufii.app.dolphin

// Compose screen with no view ids: fields are matched by label text and geometry.
object DolphinTarget {

    val packages = listOf(
        "org.dolphinemu.dolphinemu",
        "org.dolphinemu.dolphinemu.debug"
    )

    fun owns(pkg: String): Boolean = pkg in packages

    const val LABEL_MENU_NETPLAY = "grid_menu_netplay"

    /** appcompat string, so it resolves in Dolphin's locale. */
    const val OVERFLOW_DESCRIPTION = "abc_action_menu_overflow_description"

    /** Resource names, not values: matched against every shipped translation. */
    const val LABEL_NICKNAME = "netplay_nickname_label"
    const val LABEL_IP_ADDRESS = "netplay_ip_address_label"
    const val LABEL_PORT = "netplay_port_label"
    const val LABEL_CONNECTION_TYPE = "netplay_connection_type"
    const val LABEL_DIRECT_CONNECTION = "netplay_connection_type_direct_connection"
    const val LABEL_TRAVERSAL_SERVER = "netplay_connection_type_traversal_server"

    /** Also the label of both confirm buttons. */
    const val LABEL_ROLE_CONNECT = "netplay_connection_role_connect"
    const val LABEL_ROLE_HOST = "netplay_connection_role_host"

    /** Dolphin restores the last game from Dolphin.ini, so always set it. */
    const val LABEL_GAME = "netplay_game_label"

    /** DEFAULT_LISTEN_PORT in Dolphin's NetplaySettings.cpp. */
    const val DEFAULT_PORT = 2626

    const val UI_READ_FROM = "dolphin-master-2606-302 (read live on the Thor, 2026-08-15)"
}

package eu.emufii.app.ps2

object Ps2Target {

    /** Not `xyz.aethersx2.android`: it has no network layer. */
    val packages = listOf("com.armsx2")

    fun owns(pkg: String): Boolean = pkg in packages

    /** Some labels live in assets/i18n; Local Link labels are hardcoded English in the dex. */
    object I18n {
        const val DIRECTORY = "i18n"

        const val KEY_ENABLE_DEV9 = "network.enableDev9Ethernet"
        const val KEY_PRIMARY_DNS = "network.primaryDns"
        const val KEY_NETWORK_TAB = "tab.network"
    }

    const val LABEL_ENABLE_DEV9 = "Enable DEV9 Ethernet"
    const val LABEL_NETWORK_MODE = "Network mode"
    const val LABEL_MODE_ONLINE = "Online (Sockets)"
    const val LABEL_MODE_HOST = "Host local game"
    const val LABEL_MODE_JOIN = "Join local game"

    const val LABEL_HOST_ADDRESS = "Host IPv4 address"
    const val LABEL_OWN_ADDRESS = "This device's address"
    const val LABEL_PORT = "Local Link port"
    const val LABEL_ROOM_CODE = "Room code"

    const val LABEL_PRIMARY_DNS = "Primary DNS"
    const val LABEL_DNS_MANUAL = "Manual"

    const val LABEL_SETTINGS = "Settings"
    const val LABEL_NETWORK = "Network"

    /** ARMSX2 negotiates nothing, so both ends must use this port. */
    const val DEFAULT_PORT = 19072

    val PORT_RANGE = 1024..65535

    val ROOM_CODE_LENGTH = 4..12

    fun isValidRoomCode(code: String): Boolean =
        code.length in ROOM_CODE_LENGTH && code.all { it.isLetterOrDigit() && it.code < 128 }

    fun isValidPort(port: Int): Boolean = port in PORT_RANGE
}

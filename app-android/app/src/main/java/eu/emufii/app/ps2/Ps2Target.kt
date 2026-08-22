package eu.emufii.app.ps2

/**
 * ARMSX2's network screen.
 *
 * A third backend with a third way of addressing a screen: Azahar and Eden share
 * a resource id per field, Dolphin is Compose and exposes none, and ARMSX2 has
 * real Android views but keeps its translations in JSON assets rather than
 * resources, and only for part of this screen. See [I18n] and
 * `docs/NOTES_PS2.md`.
 *
 * Kept apart so that an ARMSX2 change cannot reach the other consoles.
 *
 * Read on ARMSX2 2.6.6.7 (`com.armsx2`, versionCode 1512), Thor, 2026-08-17.
 * Scout: `docs/PHASE1_SCOUT_PS2_ARMSX2.md`.
 */
object Ps2Target {

    /**
     * `xyz.aethersx2.android` is deliberately absent: that is the original
     * AetherSX2, abandoned in 2023, with no network layer. Both coexist on the
     * Thor.
     */
    val packages = listOf("com.armsx2")

    fun owns(pkg: String): Boolean = pkg in packages

    /**
     * ARMSX2 is translated, but not everywhere, and the boundary is sharp.
     *
     * The old network settings live in `assets/i18n` (19 languages, 838 keys) and
     * are translated. The Local Link labels are in none of those files, they are
     * hardcoded in the dex and therefore English in every language.
     *
     * So [Ps2Labels] reads ARMSX2's own JSON when the key is there and falls back
     * to the English constant otherwise. If upstream ever translates Local Link,
     * the driver follows on its own.
     */
    object I18n {
        /** Where ARMSX2 keeps its translations, inside its assets. */
        const val DIRECTORY = "i18n"

        // Keys of the translated labels. English stays the fallback.
        const val KEY_ENABLE_DEV9 = "network.enableDev9Ethernet"
        const val KEY_PRIMARY_DNS = "network.primaryDns"
        const val KEY_NETWORK_TAB = "tab.network"
    }

    // -- The labels, as they are displayed --

    const val LABEL_ENABLE_DEV9 = "Enable DEV9 Ethernet"
    const val LABEL_NETWORK_MODE = "Network mode"
    const val LABEL_MODE_ONLINE = "Online (Sockets)"
    const val LABEL_MODE_HOST = "Host local game"
    const val LABEL_MODE_JOIN = "Join local game"

    /** Guest side only; the host shows [LABEL_OWN_ADDRESS], which is informational. */
    const val LABEL_HOST_ADDRESS = "Host IPv4 address"
    const val LABEL_OWN_ADDRESS = "This device's address"
    const val LABEL_PORT = "Local Link port"
    const val LABEL_ROOM_CODE = "Room code"

    /**
     * ARMSX2's own online mode, which Emufii does not drive.
     *
     * Kept because this object maps the Network screen, and a label missing from
     * the map is a label the next reader goes looking for in the emulator. The
     * revival-server path was set aside on 2026-08-19, see
     * `docs/PS2_ONLINE_MIS_DE_COTE.md`.
     */
    const val LABEL_PRIMARY_DNS = "Primary DNS"
    const val LABEL_DNS_MANUAL = "Manual"

    /** The path from the library: ☰ → Settings → Network. */
    const val LABEL_SETTINGS = "Settings"
    const val LABEL_NETWORK = "Network"

    /**
     * ARMSX2's default port, and ours.
     *
     * The emulator negotiates nothing ("Use the same port on every device"), so
     * the session imposes the same value at both ends. Keeping its default means
     * a player who configured the device by hand has nothing to change.
     */
    const val DEFAULT_PORT = 19072

    /** The bounds the emulator displays, and refuses beyond. */
    val PORT_RANGE = 1024..65535

    /**
     * The room code: 4 to 12 letters or digits, identical everywhere.
     *
     * "It keeps two nearby sessions apart; it is not security", says ARMSX2
     * itself. Never present it to the player as a protection: the real isolation
     * is the relay's.
     */
    val ROOM_CODE_LENGTH = 4..12

    fun isValidRoomCode(code: String): Boolean =
        code.length in ROOM_CODE_LENGTH && code.all { it.isLetterOrDigit() && it.code < 128 }

    fun isValidPort(port: Int): Boolean = port in PORT_RANGE
}

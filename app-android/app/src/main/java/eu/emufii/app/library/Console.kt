package eu.emufii.app.library

/**
 * The grid stays one grid: which emulator is launched, and what the network needs
 * first, is our problem.
 * pourquoi : docs/decisions/reglages-et-consoles.md § The grid stays a grid
 */
enum class Console(
    val label: String,
    val extensions: Set<String>,
    val backend: Backend
) {
    THREE_DS(
        label = "3DS",
        extensions = setOf("3ds", "cci", "cxi", "cia", "3dsx", "app"),
        backend = Backend.AZAHAR
    ),

    /**
     * No room to create and no dialog to fill in, just a fixed ad hoc server address
     * the relay translates towards the session's host.
     * pourquoi : docs/decisions/reglages-et-consoles.md § The four multiplayer families
     */
    PSP(
        label = "PSP",
        // No `.prx`: a module sitting next to a game, never a game.
        extensions = setOf("iso", "cso", "pbp", "chd"),
        backend = Backend.PPSSPP
    ),

    DS(
        label = "DS",
        extensions = setOf("nds", "dsi", "ids", "srl"),
        backend = Backend.MELONDS_WFC
    ),

    SWITCH(
        label = "Switch",
        extensions = setOf("nsp", "xci"),
        backend = Backend.EDEN
    ),

    /**
     * Without `.iso`: the table is a map, one owner per key, and claiming it would
     * take it from the PSP.
     * pourquoi : docs/decisions/reglages-et-consoles.md § The extension table is a map: one owner per key
     */
    GAMECUBE(
        label = "GameCube",
        extensions = setOf("gcm"),
        backend = Backend.DOLPHIN
    ),

    WII(
        label = "Wii",
        extensions = setOf("rvz", "wia", "wbfs"),
        backend = Backend.DOLPHIN
    ),

    /**
     * No extension of its own, and that is not an oversight: PS2 arrives by its
     * folder (`ps2/`) or by reading the bytes.
     * pourquoi : docs/decisions/reglages-et-consoles.md § The extension table is a map: one owner per key
     */
    PS2(
        label = "PS2",
        extensions = emptySet(),
        backend = Backend.ARMSX2
    );

    /**
     * The name the coordinator receives, and what decides on a VPS room: stable
     * lowercase, never derived from [label].
     * pourquoi : docs/decisions/reglages-et-consoles.md § The network name is a contract, never a label
     */
    val wireName: String
        get() = when (this) {
            THREE_DS -> "3ds"
            PSP -> "psp"
            DS -> "ds"
            SWITCH -> "switch"
            GAMECUBE -> "gamecube"
            WII -> "wii"
            PS2 -> "ps2"
        }

    /** Fits on a tile badge, where [label] would wrap. */
    val shortLabel: String
        get() = when (this) {
            THREE_DS -> "3DS"
            PSP -> "PSP"
            DS -> "DS"
            SWITCH -> "Switch"
            GAMECUBE -> "GC"
            WII -> "Wii"
            PS2 -> "PS2"
        }

    companion object {
        private val byExtension: Map<String, Console> =
            entries.flatMap { c -> c.extensions.map { it to c } }.toMap()

        fun forExtension(ext: String): Console? = byExtension[ext.lowercase()]

        val allExtensions: Set<String> = byExtension.keys

        /**
         * The cheapest and truest answer, the player having sorted the file
         * themselves. Normalised, and the direct folder only, never its ancestors.
         * pourquoi : docs/decisions/reglages-et-consoles.md § The folder name is the cheapest and truest answer
         */
        private val byFolder: Map<String, Console> = mapOf(
            "ps2" to PS2,
            "playstation2" to PS2,
            "sonyps2" to PS2,
            "sonyplaystation2" to PS2,
            "psp" to PSP,
            "playstationportable" to PSP,
            "nds" to DS,
            "ds" to DS,
            "nintendods" to DS,
            "3ds" to THREE_DS,
            "n3ds" to THREE_DS,
            "nintendo3ds" to THREE_DS,
            "switch" to SWITCH,
            "nintendoswitch" to SWITCH,
            "wii" to WII,
            "nintendowii" to WII,
            "gc" to GAMECUBE,
            "ngc" to GAMECUBE,
            "gamecube" to GAMECUBE,
            "nintendogamecube" to GAMECUBE,
        )

        fun forFolder(name: String): Console? =
            byFolder[name.lowercase().filter { it.isLetterOrDigit() }]
    }
}

enum class Backend {
    AZAHAR,

    /** The Switch's LDN over an ENet room, same port and dialog as Azahar. */
    EDEN,

    /** PSP ad hoc through PPSSPP's per-game INI on a user-granted memory stick. */
    PPSSPP,

    /**
     * Kaeru WFC, reached by moving DNS rather than building a network: no session
     * code, no tunnel, each console talks to the revival server.
     * pourquoi : docs/decisions/reglages-et-consoles.md § The four multiplayer families
     */
    MELONDS_WFC,

    /**
     * Dolphin's own netplay: Compose screen, no view ids, its own driver, and
     * ENet/UDP 2626 rather than 24872.
     * pourquoi : docs/decisions/reglages-et-consoles.md § The port is part of the plan
     */
    DOLPHIN,

    /**
     * ARMSX2's Local Link, the ~57 games with a LAN mode: real Android views but no
     * translatable strings, hence hardcoded labels. PS2 online play is not this.
     * pourquoi : docs/decisions/reglages-et-consoles.md § The four multiplayer families
     */
    ARMSX2,

    /**
     * No multiplayer path built yet; these ROMs still belong in the grid.
     * pourquoi : docs/decisions/reglages-et-consoles.md § The four multiplayer families
     */
    NONE;

    /**
     * True where a room must be joined before the game boots; WFC is out, having no
     * room at all, only a resolver.
     * pourquoi : docs/decisions/reglages-et-consoles.md § The four multiplayer families
     */
    val hasNetplay: Boolean get() =
        this == AZAHAR || this == EDEN || this == DOLPHIN || this == ARMSX2

    /**
     * The emulator's own name, not a translated string: product names are
     * the same everywhere. Hardcoding one made a Switch session announce Azahar.
     * pourquoi : docs/decisions/reglages-et-consoles.md § The network name is a contract, never a label
     */
    /**
     * The port this emulator's netplay listens on: 24872 for Azahar and Eden,
     * 2626 for Dolphin. The wrong one reads as a broken tunnel.
     * pourquoi : docs/decisions/reglages-et-consoles.md § The port is part of the plan
     */
    val defaultNetplayPort: Int
        get() = when (this) {
            DOLPHIN -> eu.emufii.app.dolphin.DolphinTarget.DEFAULT_PORT
            // ARMSX2's own screen says "there is no automatic negotiation": both
            // ends have to carry this port.
            ARMSX2 -> eu.emufii.app.ps2.Ps2Target.DEFAULT_PORT
            else -> eu.emufii.app.netplay.NetplayUi.DEFAULT_PORT
        }

    val emulatorName: String get() = when (this) {
        AZAHAR -> "Azahar"
        EDEN -> "Eden"
        PPSSPP -> "PPSSPP"
        MELONDS_WFC -> "melonDS"
        DOLPHIN -> "Dolphin"
        ARMSX2 -> "ARMSX2"
        NONE -> ""
    }
}

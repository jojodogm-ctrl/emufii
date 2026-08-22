package eu.emufii.app.library

/**
 * What a ROM is, and what plays it.
 *
 * The grid stays one grid, a user drops a folder in and everything they own
 * shows up together. Which emulator gets launched, and what has to happen on
 * the network first, is our problem, not theirs.
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
     * PSP. PPSSPP's ad hoc has no room to create and no dialog to fill in: the
     * console looks for "the ad hoc server" at an address set once and for all,
     * and the relay translates it towards the current session's host.
     */
    PSP(
        label = "PSP",
        // No `.prx`: that is a module, a plugin sitting next to a game, and never
        // a game. It has no business in a grid of tiles.
        extensions = setOf("iso", "cso", "pbp", "chd"),
        backend = Backend.PPSSPP
    ),

    DS(
        label = "DS",
        extensions = setOf("nds", "dsi", "srl"),
        backend = Backend.MELONDS_WFC
    ),

    SWITCH(
        label = "Switch",
        extensions = setOf("nsp", "xci"),
        backend = Backend.EDEN
    ),

    /**
     * GameCube and Wii, both played by Dolphin, and both listed here without
     * `.iso` even though that is the commonest thing a disc image is called.
     *
     * The extension table is a map, one console per key: adding `.iso` here
     * would not share it with the PSP, it would take it away, last entry wins,
     * and every UMD rip in the library would silently start pointing at
     * Dolphin. So these two claim only what nothing else uses, and the shared
     * extension is settled by reading the file, in [DiscImage].
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
     * PS2, through ARMSX2, and without a single extension of its own.
     *
     * That is not an oversight. On the Thor the six PS2 games and the six PSP
     * games are all `.iso`, in two neighbouring folders, the exact collision the
     * GameCube already ran into. The table is a map, one owner per key: claiming
     * `.iso` here would not share it with the PSP, it would take it away, and the
     * whole UMD library would silently point at ARMSX2.
     *
     * The PS2 therefore arrives only through [DiscImage], which reads the bytes
     * and promotes only what it has positively recognised.
     */
    PS2(
        label = "PS2",
        extensions = emptySet(),
        backend = Backend.ARMSX2
    );

    /**
     * The name the coordinator receives, and what decides on a room on the VPS.
     *
     * It cannot infer the console from what it stores, a title and a titleId,
     * which the 3DS and the Switch write the same way. Written here in stable
     * lowercase, never derived from [label]: a label gets retouched for the
     * screen, and this name is a network contract.
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
            // "GameCube" wraps on a tile; the abbreviation is what the console
            // was sold as anyway.
            GAMECUBE -> "GC"
            WII -> "Wii"
            PS2 -> "PS2"
        }

    companion object {
        private val byExtension: Map<String, Console> =
            entries.flatMap { c -> c.extensions.map { it to c } }.toMap()

        fun forExtension(ext: String): Console? = byExtension[ext.lowercase()]

        /** Every extension worth opening. Used to skip files fast during a scan. */
        val allExtensions: Set<String> = byExtension.keys
    }
}

/** How Emufii gets a game into multiplayer, once it's launched. */
enum class Backend {
    /** Rooms over the session network; Azahar's own netplay dialog. */
    AZAHAR,

    /**
     * Eden's rooms: the Switch's local wireless (LDN) tunnelled over an ENet
     * room, on the same port and with the same dialog as Azahar, see
     * docs/PHASE1_SCOUT_EDEN.md. The session network carries it unchanged.
     */
    EDEN,

    /**
     * PSP ad hoc, through PPSSPP. Nothing to drive in the emulator, which draws
     * its own interface, invisible to accessibility, and nothing to drive anyway:
     * the player set the address once, and it does not change.
     */
    PPSSPP,

    /**
     * Online play against Kaeru WFC, reached by moving DNS rather than by
     * building a network. No session code, no tunnel between players: each
     * console talks to the revival server. A second product in the same app,
     * as docs/ROADMAP_CONSOLES.md puts it.
     */
    MELONDS_WFC,

    /**
     * GameCube and Wii, by Dolphin's own netplay, Android-side since
     * 2026-06-28, and nothing like the other two: its screen is Compose and
     * carries no view ids, so it has a driver of its own. See
     * `eu.emufii.app.dolphin.DolphinTarget`.
     *
     * Rooms over the session network, like Azahar and Eden, but on ENet/UDP
     * 2626 instead of 24872, the plan has to carry that port explicitly.
     */
    DOLPHIN,

    /**
     * PS2, through ARMSX2's Local Link mode: the roughly 57 games shipped with a
     * LAN or System Link mode, wired together as if they were on the same switch.
     *
     * A third shape of screen again, hence a driver of its own
     * (`eu.emufii.app.ps2.Ps2Target`): real Android views, but no translatable
     * string in the APK, so hardcoded English labels.
     *
     * PS2 online play does not go through this: it is played on a revival server,
     * over DNS, with no session and no tunnel, see
     * `docs/PHASE1_SCOUT_PS2_ARMSX2.md`. The two must not be confused on screen.
     */
    ARMSX2,

    /**
     * Recognised, launchable one day, but with no multiplayer path built yet.
     * These ROMs still belong in the grid, leaving them out would make the
     * library look broken to someone who owns them.
     */
    NONE;

    /**
     * True where the emulator has a netplay dialog Emufii fills in, and where the
     * room therefore has to be joined before the game boots.
     *
     * WFC is out because there is no room at all, only a resolver.
     */
    val hasNetplay: Boolean get() =
        this == AZAHAR || this == EDEN || this == DOLPHIN || this == ARMSX2

    /**
     * The emulator's own name, for text the player reads.
     *
     * Not a translated string: these are product names, the same in every
     * language, and they are what is written on the icon the player is about to
     * see. Hardcoding "Azahar" in a label was how a Switch session came to
     * announce "automatic Azahar setup" while it was driving Eden.
     */
    /**
     * The port this emulator's netplay listens on by default.
     *
     * Azahar and Eden share 24872, inherited from Citra; Dolphin listens on 2626
     * (`DEFAULT_LISTEN_PORT`). The plan has to carry the right one, failing which
     * the guest dials a valid address on a port where nobody answers, a failure
     * that reads as a broken tunnel.
     */
    val defaultNetplayPort: Int
        get() = when (this) {
            DOLPHIN -> eu.emufii.app.dolphin.DolphinTarget.DEFAULT_PORT
            // ARMSX2 negotiates nothing: "there is no automatic negotiation",
            // says its own screen. Both ends have to carry this port.
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

package eu.emufii.app.library

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

    PSP(
        label = "PSP",
        extensions = setOf("iso", "cso", "pbp", "chd"),
        backend = Backend.PPSSPP
    ),

    DS(
        label = "DS",
        extensions = setOf("nds", "dsi", "ids", "srl"),
        backend = Backend.MELONDS
    ),

    SWITCH(
        label = "Switch",
        extensions = setOf("nsp", "xci"),
        backend = Backend.EDEN
    ),

    /** No `.iso`: one owner per extension, and it belongs to the PSP. */
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

    PS2(
        label = "PS2",
        extensions = emptySet(),
        backend = Backend.ARMSX2
    );

    /** Sent to the coordinator, decides on a VPS room: never derive from [label]. */
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

    EDEN,

    PPSSPP,

    MELONDS,

    /** ENet/UDP 2626, not 24872. */
    DOLPHIN,

    ARMSX2,

    NONE;

    val hasNetplay: Boolean get() =
        this == AZAHAR || this == EDEN || this == DOLPHIN || this == ARMSX2

    val defaultNetplayPort: Int
        get() = when (this) {
            DOLPHIN -> eu.emufii.app.dolphin.DolphinTarget.DEFAULT_PORT
            // ARMSX2 does no port negotiation: both ends must use this port.
            ARMSX2 -> eu.emufii.app.ps2.Ps2Target.DEFAULT_PORT
            MELONDS -> eu.emufii.app.wfc.MelonDsPackage.NETPLAY_PORT
            else -> eu.emufii.app.netplay.NetplayUi.DEFAULT_PORT
        }

    val emulatorName: String get() = when (this) {
        AZAHAR -> "Azahar"
        EDEN -> "Eden"
        PPSSPP -> "PPSSPP"
        MELONDS -> "melonDS"
        DOLPHIN -> "Dolphin"
        ARMSX2 -> "ARMSX2"
        NONE -> ""
    }
}

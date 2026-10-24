package eu.emufii.app.nativeds

import org.json.JSONObject

/** Physical DS/DSi companion discovery, separate from embedded GOLD/SILVER. */
data class NativeDsRoom internal constructor(
    val engineVersion: String,
    val contentSha256: String,
    val maxPlayers: Int,
    val roomCode: String,
    val phase: NativeDsPhase,
    val generation: Long,
    val players: Int,
) {
    val route: NativeDsRoute get() = NativeDsRoute.PHYSICAL_CONSOLE_COMPANION
    val launchesPhoneRuntime: Boolean get() = false
    val requiresWireGuard: Boolean get() = false

    fun sameDescriptor(other: NativeDsRoom): Boolean = engineVersion == other.engineVersion &&
        contentSha256 == other.contentSha256 && maxPlayers == other.maxPlayers && roomCode == other.roomCode

    companion object {
        const val TRANSPORT = "ds-homebrew"
        const val DISCOVERY_HEADER = "X-Emufii-Native-Rooms"
        private val descriptorFields = setOf("protocol", "gameId", "engineVersion", "contentSha256", "maxPlayers", "roomCode")
        private val stateFields = setOf("phase", "generation", "players")
        private val knownTransports = setOf("wireguard", "ds-hle", "ds-local", TRANSPORT)

        /** Null means a structurally non-native session. Malformed native or
         * unknown transport throws; callers must never reinterpret it as WG. */
        fun fromSession(json: JSONObject): NativeDsRoom? {
            val transport = json.opt("transport").let {
                if (it == null || it === JSONObject.NULL) null
                else (it as? String) ?: error("Invalid session transport type")
            }
            require(transport == null || transport in knownTransports) { "Unknown session transport" }
            if (transport != TRANSPORT) {
                require(!json.has("native") && !json.has("native_state")) { "Unexpected native descriptor" }
                return null
            }
            require(!json.has("ds") || json.isNull("ds")) { "Conflicting DS runtime descriptor" }
            val native = json.opt("native") as? JSONObject ?: error("Native descriptor missing")
            val state = json.opt("native_state") as? JSONObject ?: error("Native state missing")
            require(native.keys().asSequence().toSet() == descriptorFields && state.keys().asSequence().toSet() == stateFields)
            require(native.exactString("protocol") == "emufii-nsmb-native-v1")
            require(native.exactString("gameId") == "nsmb-mvlo-dsi")
            val engine = native.exactString("engineVersion").also { require(it.matches(Regex("[A-Za-z0-9_.-]{1,48}"))) }
            val hash = native.exactString("contentSha256").also { require(it.matches(Regex("[a-f0-9]{64}"))) }
            val capacity = native.exactInteger("maxPlayers", 2, 10).toInt()
            val code = native.exactString("roomCode").also { require(it.matches(Regex("[BCDFGHJKLMNPRQSTVWXYZ]{4}"))) }
            val phase = NativeDsPhase.entries.singleOrNull { it.wire == state.exactString("phase") }
                ?: error("Unknown native phase")
            val generation = state.exactInteger("generation", 0, 0xffff_ffffL)
            val players = state.exactInteger("players", 1, capacity.toLong()).toInt()
            for (key in listOf("ready", "host_ready")) if (json.has(key)) require(json.opt(key) == false)
            if (json.has("players")) require(json.exactInteger("players", 1, capacity.toLong()) == players.toLong())
            for (key in listOf("subnet", "host_ip", "port", "room")) if (json.has(key)) require(json.isNull(key))
            return NativeDsRoom(engine, hash, capacity, code, phase, generation, players)
        }

        private fun JSONObject.exactString(key: String): String = (opt(key) as? String) ?: error("Invalid $key type")
        private fun JSONObject.exactInteger(key: String, min: Long, max: Long): Long {
            val value = opt(key)
            require(value is Int || value is Long) { "Invalid $key integer" }
            return (value as Number).toLong().also { require(it in min..max) }
        }
    }
}

enum class NativeDsRoute { PHYSICAL_CONSOLE_COMPANION }
enum class NativeDsPhase(val wire: String, val label: String) {
    LOBBY("lobby", "En salon"), COUNTDOWN("countdown", "Départ imminent"),
    LOADING("loading", "Chargement"), STARTING("starting", "Introduction"),
    PLAYING("playing", "En partie"), FINISHED("finished", "Résultats"), STOPPED("stopped", "Partie interrompue")
}

data class NativeDsSelection(val emufiiCode: String, val hostName: String, val room: NativeDsRoom) {
    init { require(emufiiCode.matches(Regex("[A-HJ-NP-Z]{3}-[2-9]{3}"))) }
    fun shareText(): String = "Mario vs Luigi DS · salon DSi ${room.roomCode}\n" +
        "Code Emufii : $emufiiCode\nHôte : $hostName\n" +
        "Sur la DSi, ouvre Mario vs Luigi DS, puis En ligne → Rejoindre."
}

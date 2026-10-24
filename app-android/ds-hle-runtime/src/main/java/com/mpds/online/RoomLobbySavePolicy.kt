package com.mpds.online

/** Save metadata policy from guest-lobby.mjs. The caller authenticates [outer]
 * and owns lobby phase/generation checks and actual save-content validation.
 */
internal object RoomLobbySavePolicy {
    data class Binding(val generation: Long, val game: String, val romSha256: String)

    // NSMB_PREPARATION.romSha256 in tools/nifi2-web/nsmb-preparation.mjs;
    // also RoomInvitationIdentity.NSMB_ROM (private there).
    private const val NSMB_ROM = "dd2e519a9e46134e213e6e63dd2b478d9a258627769b777baddb2316dc21b81d"
    private val fields = setOf("generation", "game", "romSha256")
    private val hash = Regex("[0-9a-f]{64}")
    private val rawSaveSizes = setOf(
        512, 8192, 65536, 131072, 262144, 524288, 1048576, 8388608, 16777216, 67108864,
    )

    fun binding(value: Map<String, Any?>, outer: Map<String, Any?>): Binding {
        require(value.keys == fields) { "save-binding" }
        val game = value["game"] as? String
        val allowed = if (outer["lobbyProtocol"] == 2.0) {
            (outer["supportedGames"] as? List<*>)?.contains(game) == true
        } else game == (outer["game"] ?: "mpds")
        require(allowed) { "round-selection" }
        require(game != null) { "save-binding" }
        val generation = value["generation"]
        require(generation is Double && generation.isFinite() &&
            generation >= 1.0 && generation <= 1000000.0 &&
            generation == generation.toLong().toDouble()) { "save-binding" }
        val rom = value["romSha256"]
        require(rom is String && hash.matches(rom)) { "save-binding" }
        val matches = when (game) {
            "experimental" -> rom == outer["romSha256"]
            "nsmb" -> rom == NSMB_ROM
            "mpds", "mkds", "dkr" -> {
                val pin = RoomInvitationPins.games[game] as? Map<*, *>
                rom == pin?.get("rom") || game == "mpds" && rom == pin?.get("stock")
            }
            else -> false
        }
        require(matches) { "save-binding" }
        require(game != "experimental" || outer["guestBoot"] == "cartridge") {
            "save-loading-unsupported"
        }
        return Binding(generation.toLong(), game, rom)
    }

    /** Supplied file extents only; choosing no save is handled by the caller.
     * Size acceptance is not save-content/checksum or ROM database validation.
     */
    fun validateSize(bytes: Int, game: String) {
        require(when (game) {
            "mkds", "dkr" -> bytes == 262144
            "experimental" -> bytes in rawSaveSizes
            else -> false
        }) { "save-invalid" }
    }
}

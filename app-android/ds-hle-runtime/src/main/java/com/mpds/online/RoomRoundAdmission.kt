package com.mpds.online

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

/** Inner-game capabilities belong to a single generation, not to the persistent
 * directory lobby. Retirement invalidates them before native teardown/download
 * cancellation. The caller must await that teardown before admitting a new game.
 * Only use on the serialized lobby lifecycle worker.
 */
internal class RoomRoundAdmission(
    private val outer: RoomInvitation.Admission,
    private val snapshot: RoomDirectory.Snapshot,
    private val now: () -> Long = System::currentTimeMillis
) : Closeable {
    @Volatile private var closed = false
    private var retired = 0L
    private var started = 0L
    private var active: RoomInvitation? = null
    @Volatile private var current: RoomDirectory.Snapshot? = null
    private val epochs = HashSet<String>()

    @Synchronized fun admit(generation: Long, files: Map<String, Any?>): RoomInvitation.Admission {
        check(!closed && outer.isCurrent() && active == null) { "round-not-ready" }
        require(generation in 1..1000000 && generation > maxOf(started, retired)) { "round-generation" }
        require(files.keys == setOf("room.json", "client.json", "capability.bin")) { "round-files" }
        fun decode(name: String, limit: Int): ByteArray {
            val text = files[name] as? String ?: error("round-files")
            require(text.length <= ((limit + 2) / 3) * 4) { "round-files" }
            val bytes = Base64.getDecoder().decode(text)
            require(bytes.size <= limit && Base64.getEncoder().encodeToString(bytes) == text) { "round-files" }
            return bytes
        }
        // Read only the game selector before authentication. No untrusted field
        // here may choose endpoints, file paths or loosen the outer asset policy.
        val raw = decode("room.json", 16384)
        val unsigned = try {
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString()
            RoomDirectory.parseStrictJson(text) as? Map<*, *> ?: error("round-files")
        } finally { raw.fill(0) }
        decode("client.json", 16384).fill(0)
        decode("capability.bin", 32).also { require(it.size == 32) }.fill(0)
        val gameName = if (unsigned.containsKey("game")) unsigned["game"] as? String else "mpds"
        val game = RoomDirectory.Game.values().firstOrNull { it.name.lowercase(java.util.Locale.ROOT) == gameName }
            ?: error("round-game")
        val room = outer.room
        require(room["lobbyProtocol"] in listOf(1.0, 2.0)) { "round-lobby" }
        val allowed = if (room["lobbyProtocol"] == 2.0) room["supportedGames"] as? List<*>
            else listOf(room["game"] ?: "mpds")
        require(allowed?.contains(gameName) == true) { "round-game" }
        check(epochs.size < 4096) { "round-limit" }
        val selected = snapshot.copy(generation = generation, phase = RoomDirectory.Phase.RUNNING,
            settings = snapshot.settings.copy(game = game, sendROM = room["sendROM"] != false))
        current = selected
        val owner = RoomInvitation({ if (!closed && outer.isCurrent()) current else null }, now)
        active = owner
        started = generation
        try {
            val envelope = "NIFI2-INVITE-1\n" + RoomJson.encode(mapOf("version" to 1, "files" to files))
            val uri = "https://party.lepast.fr/nifi2/join/#" +
                Base64.getUrlEncoder().withoutPadding().encodeToString(envelope.toByteArray(Charsets.UTF_8))
            val admitted = owner.admit(uri, selected)
            val inner = admitted.room
            val ticks = when {
                room["lobbyProtocol"] == 2.0 && game == RoomDirectory.Game.NSMB -> 10.0
                room["lobbyProtocol"] == 2.0 && game == RoomDirectory.Game.MKDS && inner["mkdsMenu"] == true -> 8.0
                else -> room["bufferTicks"]
            }
            check(!inner.containsKey("lobbyProtocol") &&
                inner["relayAddress"] == room["relayAddress"] && inner["relayPort"] == room["relayPort"] &&
                inner["bufferTicks"] == ticks && admitted.config.expiresAtUnixMs <= outer.config.expiresAtUnixMs &&
                inner["room"] != room["room"] && inner["epoch"] != room["epoch"] &&
                (inner["shareAssets"] != true || room["shareAssets"] == true)) { "round-binding" }
            check(epochs.add(inner["epoch"] as String)) { "round-epoch-reused" }
            check(admitted.isCurrent()) { "round-retired" }
            return admitted
        } catch (e: Exception) {
            current = null; active = null; owner.close()
            throw e
        }
    }

    @Synchronized fun retire(generation: Long) {
        require(generation in maxOf(1L, started, retired)..1000000) { "round-generation" }
        retired = generation
        current = null
        active?.close(); active = null
    }

    @Synchronized override fun close() {
        closed = true; current = null
        active?.close(); active = null
        epochs.clear()
    }
}

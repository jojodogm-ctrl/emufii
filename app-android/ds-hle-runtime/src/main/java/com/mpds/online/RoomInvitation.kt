package com.mpds.online

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Base64
import java.util.Collections
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** In-memory admission only. Call on the runner worker; never give Admission to UI.
 * A self-contained capability authenticates consistency, not the host's identity.
 * The owner must cancel on Stop/leave/replacement and check Admission.isCurrent()
 * before each use and after blocking work. No timers, network, logging or storage.
 */
internal class RoomInvitation(
    private val currentSnapshot: () -> RoomDirectory.Snapshot?,
    private val now: () -> Long = System::currentTimeMillis
) : AutoCloseable {
    internal class InvitationError(val code: String) : Exception(code)

    internal class Config internal constructor(
        val relayAddress: String, val relayPort: Int, val room: String, val seat: Int,
        val invitationId: String, val ttlSeconds: Int, val expiresAtUnixMs: Long,
        val localPort: Int, val role: String = "client"
    ) {
        val version = 1
        val capabilityFile = "capability.bin"
        internal fun route(room: String, id: String = invitationId, port: Int = 0) =
            Config(relayAddress, relayPort, room, seat, id, ttlSeconds, expiresAtUnixMs, port, role)
        internal fun server() = Config(relayAddress, relayPort, room, seat, invitationId,
            ttlSeconds, expiresAtUnixMs, 0, "server")
        override fun toString() = "RoomInvitation.Config(redacted)"
    }
    internal class Channel internal constructor(val config: Config, val capability: ByteArray,
                                                val authenticationKey: ByteArray? = null) {
        override fun toString() = "RoomInvitation.Channel(redacted)"
    }
    /** Byte arrays are owner-owned, shared with channels, and erased on close/cancel.
     * Do not copy, persist, log, or retain them beyond this admission's lifetime.
     * room is immutable authenticated metadata; all JSON numbers are Double.
     */
    internal class Admission internal constructor(
        val generation: Long, val snapshotGeneration: Long, val room: Map<String, Any?>,
        val config: Config, val transportKey: ByteArray, val sdk: Channel,
        val lobby: Channel, val authority: Channel?, val assets: Channel,
        private val owner: RoomInvitation
    ) : AutoCloseable {
        fun isCurrent() = owner.isCurrent(generation)
        fun dispose() = close()
        override fun close() { owner.dispose(generation) }
        override fun toString() = "RoomInvitation.Admission(redacted)"
    }

    private val lock = Any()
    private var busy = false
    private var generation = 0L
    private var expiry = 0L
    private var selected: RoomDirectory.Snapshot? = null
    private val keys = mutableListOf<ByteArray>()

    fun cancel() = synchronized(lock) { invalidate() }
    override fun close() = cancel()
    private fun invalidate() {
        generation++
        keys.forEach { it.fill(0) }; keys.clear(); expiry = 0; selected = null
    }
    private fun dispose(id: Long) = synchronized(lock) { if (generation == id) invalidate() }
    fun isCurrent(id: Long): Boolean = synchronized(lock) {
        val valid = try {
            id > 0 && id == generation && expiry > now() &&
                selected?.let { sameLaunch(it, currentSnapshot()) } == true
        } catch (_: Exception) { false }
        if (!valid && id == generation) invalidate()
        valid
    }
    private fun sameLaunch(a: RoomDirectory.Snapshot, b: RoomDirectory.Snapshot?) =
        b != null && a.code == b.code && a.seat == b.seat && a.generation == b.generation &&
            a.settings == b.settings && a.phase == RoomDirectory.Phase.RUNNING && b.phase == a.phase

    fun admit(uri: String, snapshot: RoomDirectory.Snapshot): Admission {
        val id = synchronized(lock) {
            check(!busy, "invitation-busy"); busy = true; invalidate(); generation
        }
        var capability: ByteArray? = null
        var success = false
        try {
            selection(snapshot)
            check(sameLaunch(snapshot, currentSnapshot()), "invitation-selection")
            check(uri.length <= 30000 && uri.startsWith(PREFIX))
            val envelopeBytes = decode(uri.substring(PREFIX.length), 22500, true)
            val envelope = try {
                val text = utf8(envelopeBytes)
                check(text.startsWith("NIFI2-INVITE-1\n"))
                obj(RoomDirectory.parseStrictJson(text.substring(15)))
            } finally { envelopeBytes.fill(0) }
            fields(envelope, listOf("version", "files")); check(number(envelope["version"]) == 1L)
            val files = obj(envelope["files"])
            fields(files, listOf("room.json", "client.json", "capability.bin"))
            fun document(name: String): Map<String, Any?> {
                val bytes = decode(string(files[name]), 16384)
                return try { obj(RoomDirectory.parseStrictJson(utf8(bytes))) } finally { bytes.fill(0) }
            }
            val room = document("room.json"); val configMap = document("client.json")
            val cap = decode(string(files["capability.bin"]), 32); capability = cap
            check(cap.size == 32 && cap.any { it != 0.toByte() })
            val canonical = structural(room, configMap, snapshot, now())
            synchronized(lock) {
                check(id == generation, "invitation-cancelled")
                selected = snapshot; expiry = number(room["expiresAtUnixMs"])
            }
            fun fence() { check(!Thread.currentThread().isInterrupted && isCurrent(id), "invitation-cancelled") }
            fence()
            RoomInvitationIdentity.validate(room, snapshot)
            val message = "nifi2-room-invitation-v1\u0000".toByteArray() + canonical
            val expectedMac = hmac(cap, message)
            val signature = string(room["authentication"]).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            try { check(MessageDigest.isEqual(expectedMac, signature), "invitation-authentication") }
            finally { expectedMac.fill(0); signature.fill(0); message.fill(0) }
            fence()
            fun derive(material: ByteArray, salt: ByteArray, info: String, length: Int): ByteArray {
                fence()
                val bytes = hkdf(material, salt, info, length)
                synchronized(lock) {
                    if (id != generation) { bytes.fill(0); throw InvitationError("invitation-cancelled") }
                    keys.add(bytes)
                }
                fence(); return bytes
            }
            val config = Config(string(configMap["relayAddress"]), number(configMap["relayPort"]).toInt(),
                string(configMap["room"]), number(configMap["seat"]).toInt(), string(configMap["invitationId"]),
                number(configMap["ttlSeconds"]).toInt(), number(configMap["expiresAtUnixMs"]), number(configMap["localPort"]).toInt())
            val transport = derive(cap, canonical, "nifi2-room-transport-v1", 32)
            val sdkRoom = if (snapshot.settings.game == RoomDirectory.Game.MPDS) config.room else
                "S" + url64(derive(transport, canonical, "nifi2-sdk-pair-route-v1", 11)).take(14)
            fun salt(domain: String) = json(listOf("$domain/v1", config.room, config.seat,
                config.invitationId, config.ttlSeconds, config.expiresAtUnixMs)).toByteArray()
            fun channel(domain: String, prefix: String): Channel {
                val salt = salt(domain)
                val key = derive(transport, salt, "$domain/relay/v1", 32)
                val auth = derive(transport, salt, "$domain/local-proof/v1", 32)
                val route = prefix + url64(derive(transport, salt, "$domain/route/v1", 11)).take(14)
                val invitation = hex(derive(transport, salt, "$domain/invitation/v1", 16))
                check(route != config.room && invitation != config.invitationId, "invitation-channel-collision")
                return Channel(config.route(route, invitation), key, auth)
            }
            val lobby = channel("nifi2-lobby", "L")
            val authority = if (snapshot.settings.game in listOf(RoomDirectory.Game.MKDS, RoomDirectory.Game.NSMB))
                channel("nifi2-authority-datagrams-v2", "D") else null
            val assetKey = derive(transport, salt("nifi2-assets"), "nifi2-assets/relay/v1", 32)
            fun assetHash(label: String): ByteArray {
                val bytes = (label + '\u0000').toByteArray() + assetKey
                return try { digest(bytes) } finally { bytes.fill(0) }
            }
            val routeHash = assetHash("nifi2-assets/route/v1")
            val idHash = assetHash("nifi2-assets/invitation/v1")
            val assetRoom: String; val assetId: String
            try { assetRoom = "A" + url64(routeHash).take(14); assetId = hex(idHash).take(32) }
            finally { routeHash.fill(0); idHash.fill(0) }
            check(assetRoom != config.room && assetId != config.invitationId, "invitation-channel-collision")
            fence()
            val result = Admission(id, snapshot.generation, immutableRoom(room), config, transport,
                Channel(config.route(sdkRoom, port = config.localPort), transport), lobby, authority,
                Channel(config.route(assetRoom, assetId), assetKey), this)
            synchronized(lock) { fence(); success = true }
            return result
        } catch (e: InvitationError) { throw e }
        catch (_: Exception) { throw InvitationError("invalid-invitation") }
        finally {
            capability?.fill(0)
            synchronized(lock) { if (!success && generation == id) invalidate(); busy = false }
        }
    }

    companion object {
        const val PREFIX = "https://party.lepast.fr/nifi2/join/#"
        private const val SAFE = 9007199254740991L
        internal fun check(ok: Boolean, code: String = "invalid-invitation") { if (!ok) throw InvitationError(code) }
        internal fun string(v: Any?) = v as? String ?: throw InvitationError("invalid-invitation")
        internal fun number(v: Any?, min: Long = -SAFE, max: Long = SAFE): Long {
            val d = v as? Double ?: throw InvitationError("invalid-invitation")
            check(d.isFinite() && d >= min.toDouble() && d <= max.toDouble() && d == d.toLong().toDouble())
            return d.toLong()
        }
        internal fun hash(v: Any?) = v is String && Regex("[0-9a-f]{64}").matches(v)
        @Suppress("UNCHECKED_CAST")
        internal fun obj(v: Any?): Map<String, Any?> {
            check(v is Map<*, *> && v.keys.all { it is String })
            // Java boxed Double distinguishes -0.0; JavaScript equality does not.
            return (v as Map<String, Any?>).mapValues { (_, value) ->
                if (value is Double && value == 0.0) 0.0 else value
            }
        }
        private fun fields(v: Map<String, Any?>, names: List<String>) { check(v.keys == names.toSet()) }
        internal fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        internal fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        private fun url64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        private fun decode(text: String, limit: Int, url: Boolean = false): ByteArray {
            check(text.isNotEmpty() && text.length <= limit * 2 &&
                Regex(if (url) "[A-Za-z0-9_-]+" else "[A-Za-z0-9+/]+={0,2}").matches(text))
            val bytes = (if (url) Base64.getUrlDecoder() else Base64.getDecoder()).decode(text)
            try {
                check(bytes.isNotEmpty() && bytes.size <= limit &&
                    (if (url) url64(bytes) else Base64.getEncoder().encodeToString(bytes)) == text)
                return bytes
            } catch (e: Exception) { bytes.fill(0); throw e }
        }
        private fun utf8(bytes: ByteArray) = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        private fun hmac(key: ByteArray, message: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256")); return mac.doFinal(message)
        }
        // All protocol outputs are <= SHA256's 32 bytes: HKDF-Expand needs one block.
        private fun hkdf(key: ByteArray, salt: ByteArray, info: String, length: Int): ByteArray {
            check(length in 1..32)
            val prk = hmac(salt, key)
            var block: ByteArray? = null
            return try { block = hmac(prk, info.toByteArray() + byteArrayOf(1)); block.copyOf(length) }
            finally { prk.fill(0); block?.fill(0) }
        }
        // Only validated integer numbers and protocol ASCII strings reach signing.
        internal fun json(v: Any?): String = when (v) {
            null -> "null"
            is Boolean -> v.toString()
            is Double -> number(v).toString()
            is Int, is Long -> v.toString()
            is String -> buildString {
                append('"'); for (c in v) when (c) {
                    '"' -> append("\\\""); '\\' -> append("\\\\")
                    '\b' -> append("\\b"); '\u000c' -> append("\\f"); '\n' -> append("\\n")
                    '\r' -> append("\\r"); '\t' -> append("\\t")
                    else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
                }; append('"')
            }
            is List<*> -> v.joinToString(",", "[", "]") { json(it) }
            else -> throw InvitationError("invalid-invitation")
        }
        private fun immutableRoom(room: Map<String, Any?>): Map<String, Any?> =
            Collections.unmodifiableMap(room.mapValues { (_, v) -> if (v is List<*>) Collections.unmodifiableList(v.toList()) else v })
        internal fun game(snapshot: RoomDirectory.Snapshot) = snapshot.settings.game!!.name.lowercase(Locale.ROOT)
        private fun selection(s: RoomDirectory.Snapshot) {
            val settings = s.settings; val game = settings.game
            check(game != null && s.generation > 0 && s.settings.players in 2..when (game) {
                RoomDirectory.Game.MPDS -> 4; RoomDirectory.Game.NSMB, RoomDirectory.Game.DKR -> 2; else -> 8
            } && s.seat in 1..s.settings.players, "invitation-selection")
            if (game == RoomDirectory.Game.EXPERIMENTAL) check(hash(settings.romSha256) &&
                settings.romBytes != null && settings.romBytes in 512L..536870912L &&
                settings.guestBoot != null, "invitation-selection")
        }
        private val SIGNED = listOf("version", "profile", "sdkPipeline", "romSha256", "setSha256", "snapshotSha256",
            "savedAid", "players", "seat", "epoch", "bufferTicks", "room", "relayAddress", "relayPort", "invitationId",
            "ttlSeconds", "createdAtUnixMs", "expiresAtUnixMs", "experimental", "gameplaySupported", "shareAssets",
            "transferRomSha256", "transferRomBytes")
        private fun structural(r: Map<String, Any?>, c: Map<String, Any?>, s: RoomDirectory.Snapshot, now: Long): ByteArray {
            val game = game(s)
            val version = number(r["version"])
            val signed = SIGNED + (if (r.containsKey("lobbyProtocol")) listOf("lobbyProtocol") else emptyList()) +
                (if (r["lobbyProtocol"] == 2.0) listOf("supportedGames") else emptyList()) + when (game) {
                    "experimental" -> listOf("game", "romBytes", "guestBoot")
                    "dkr" -> listOf("game"); "mpds" -> emptyList(); else -> listOf("game", "authorityTransport")
                } + (if (r.containsKey("mkdsMenu")) listOf("mkdsMenu") else emptyList()) +
                (if (game == "nsmb" || r["mkdsMenu"] == true) listOf("bootSha256", "bootBytes") else emptyList()) +
                (if (version == 2L) listOf("sendROM") else emptyList())
            fields(r, signed + "authentication")
            fields(c, listOf("version", "role", "relayAddress", "relayPort", "room", "seat", "invitationId",
                "ttlSeconds", "expiresAtUnixMs", "capabilityFile", "localPort"))
            check(if (version == 1L) !r.containsKey("sendROM") else version == 2L && r["sendROM"] == false)
            check((r["sendROM"] != false) == (s.settings.sendROM != false) && r["shareAssets"] is Boolean, "invitation-asset-policy")
            check(number(r["seat"]) == s.seat.toLong() && number(r["players"]) == s.settings.players.toLong() &&
                r["experimental"] == true && r["gameplaySupported"] == false && (game == "mpds" || r["game"] == game))
            val epoch = string(r["epoch"])
            check(Regex("[1-9][0-9]{0,19}").matches(epoch) && BigInteger(epoch) <= BigInteger("18446744073709551615"))
            number(r["bufferTicks"], 2, 12); val ttl = number(r["ttlSeconds"], 30, 28800)
            check(hash(r["authentication"]))
            check(now in 1..SAFE, "invitation-expired")
            val created = number(r["createdAtUnixMs"], 1, minOf(SAFE, now + 120000))
            val expires = number(r["expiresAtUnixMs"])
            check(expires > now && expires - created in 1000..ttl * 1000 && expires - now <= ttl * 1000 + 120000, "invitation-expired")
            val address = string(c["relayAddress"])
            val octets = address.split('.')
            check(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}").matches(address) &&
                octets.all { text -> text.toIntOrNull()?.let { it in 0..255 && it.toString() == text } == true } &&
                octets[0].toInt() in 1..223, "invitation-transport")
            check(number(c["version"]) == 1L && c["role"] == "client" && number(c["seat"]) == s.seat - 1L &&
                c["capabilityFile"] == "capability.bin", "invitation-transport")
            number(c["localPort"], 0, 65535); number(c["relayPort"], 1, 65535)
            check(Regex("[A-Za-z0-9_-]{1,15}").matches(string(c["room"])) &&
                Regex("[0-9a-f]{32}").matches(string(c["invitationId"])) &&
                listOf("room", "relayAddress", "relayPort", "invitationId", "ttlSeconds", "expiresAtUnixMs").all { c[it] == r[it] }, "invitation-transport")
            check(!r.containsKey("lobbyProtocol") || r["lobbyProtocol"] in listOf(1.0, 2.0))
            if (r["lobbyProtocol"] == 2.0) {
                val games = r["supportedGames"]
                val base = if (s.settings.players <= 4) listOf("mpds", "mkds") else listOf("mkds")
                check(if (game == "experimental") games == listOf("experimental") else games == base ||
                    s.settings.players == 2 && games in listOf(listOf("mpds", "mkds", "nsmb"), listOf("mpds", "mkds", "nsmb", "dkr")))
                check(games is List<*> && game in games, "invitation-games")
            }
            check(!r.containsKey("mkdsMenu") || game == "mkds" && r["mkdsMenu"] == true && !r.containsKey("lobbyProtocol"))
            check(game !in listOf("mkds", "nsmb") || r["authorityTransport"] == "n2s1-loopback-datagram-v2", "invitation-pipeline")
            return json(signed.map { r[it] }).toByteArray()
        }
    }
}

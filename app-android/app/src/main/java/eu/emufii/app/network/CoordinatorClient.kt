package eu.emufii.app.network

import eu.emufii.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import eu.emufii.app.wg.WgTunnelInfo
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import eu.emufii.app.profile.Profile

const val COORDINATOR_BASE_URL: String = BuildConfig.COORDINATOR_BASE_URL

data class CreatedSession(
    val code: String,
    val subnet: String,
    val token: String,
    val room: RoomRef? = null
)

sealed class CoordinatorError(message: String) : Exception(message) {
    /** 404: no such session, or one purged after its TTL. */
    class NotFound : CoordinatorError("session not found")

    class Unreachable(cause: Throwable) : CoordinatorError(cause.message ?: "unreachable")

    class Http(val status: Int) : CoordinatorError("HTTP $status")
}

/** [id] is the per-session handle; [avatar] the picture hash, null without one. */
data class Member(val id: String, val name: String, val forSeconds: Int, val avatar: String? = null)

data class Heartbeat(
    val players: Int,
    val memberToken: String?,
    val memberHandle: String?
)

data class RelayRegion(val id: String, val name: String, val pingHost: String)

data class RoomRef(val host: String, val port: Int, val password: String)

data class RemoteSession(
    val code: String,
    val subnet: String,
    val hostIp: String?,
    val port: Int?,
    val romTitleId: String?,
    val romTitle: String?,
    val hostName: String?,
    val room: RoomRef?,
    /** Null from a host on an older app: no warning rather than a false one. */
    val emulatorVersion: String?,
    /** True when missing, so an old coordinator does not block every guest. */
    val hostReady: Boolean,
    val members: List<Member>
)

data class FriendsReply(
    val present: Map<String, FriendPresence>,
    val names: Map<String, String>,
    val avatars: Map<String, String> = emptyMap(),
    val lastGames: Map<String, LastGame> = emptyMap(),
)

data class LastGame(val title: String, val titleId: String?, val at: Long)

data class FriendPresence(
    val name: String?,
    val sessionCode: String?,
    val romTitle: String?,
    val romTitleId: String?,
    val players: Int,
    val ready: Boolean
)

data class OpenSession(
    val code: String,
    val romTitle: String?,
    val romTitleId: String?,
    val hostName: String?,
    val players: Int,
    val ready: Boolean,
    val ageSeconds: Int
)

class CoordinatorClient(private val baseUrl: String = COORDINATOR_BASE_URL) {

    companion object {
        @Volatile
        var identityKey: String? = null
    }

    private fun JSONObject.putIdentityKey(): JSONObject = apply { identityKey?.let { put("key", it) } }

    suspend fun createSession(
        code: String,
        romTitleId: String?,
        romTitle: String?,
        hostName: String? = null,
        hostId: String? = null,
        console: String? = null,
        private: Boolean = false,
        region: String? = null,
        /** Handed to guests, who warn when theirs differs. */
        emulatorVersion: String? = null
    ): Result<CreatedSession> = request(
        path = "/sessions",
        method = "POST",
        body = JSONObject().apply {
            put("code", code)
            if (romTitleId != null) put("rom_title_id", romTitleId)
            if (romTitle != null) put("rom_title", romTitle)
            if (hostName != null) put("host_name", hostName)
            if (hostId != null) {
                put("host_id", hostId)
                putIdentityKey()
            }
            if (console != null) put("console", console)
            if (private) put("private", true)
            if (region != null) put("region", region)
            if (emulatorVersion != null) put("emulator_version", emulatorVersion)
        },
        readTimeout = 15_000
    ).map { text ->
        val json = JSONObject(text)
        CreatedSession(
            json.getString("code"),
            json.getString("subnet"),
            json.optString("token"),
            json.roomOrNull()
        )
    }

    suspend fun patchSession(
        code: String,
        hostIp: String,
        port: Int,
        token: String?
    ): Result<Unit> = request(
        path = "/sessions/$code",
        method = "PATCH",
        body = JSONObject().apply {
            put("host_ip", hostIp)
            put("port", port)
        },
        bearer = token
    ).map { }

    suspend fun setHostReady(
        code: String,
        ready: Boolean,
        token: String?
    ): Result<Unit> = request(
        path = "/sessions/$code",
        method = "PATCH",
        body = JSONObject().apply { put("host_ready", ready) },
        bearer = token
    ).map { }

    suspend fun getSession(code: String): Result<RemoteSession> =
        request(path = "/sessions/$code", method = "GET").map { text ->
            val json = JSONObject(text)
            RemoteSession(
                code = json.getString("code"),
                subnet = json.getString("subnet"),
                hostIp = json.stringOrNull("host_ip"),
                port = json.intOrNull("port"),
                romTitleId = json.stringOrNull("rom_title_id"),
                romTitle = json.stringOrNull("rom_title"),
                hostName = json.stringOrNull("host_name"),
                room = json.roomOrNull(),
                emulatorVersion = json.stringOrNull("emulator_version"),
                hostReady = json.optBoolean("host_ready", true),
                members = json.optJSONArray("members").map { m ->
                    Member(
                        id = m.getString("id"),
                        name = m.optString("name", Profile.DEFAULT_NAME),
                        forSeconds = m.optInt("for_s", 0),
                        avatar = m.stringOrNull("avatar")
                    )
                }
            )
        }

    /** The regions a session can be placed in. A coordinator from before regions answers 404. */
    suspend fun listRelays(): Result<List<RelayRegion>> =
        request(path = "/relays", method = "GET").map { text ->
            JSONObject(text).optJSONArray("relays").map { r ->
                RelayRegion(r.getString("id"), r.optString("name"), r.getString("ping_host"))
            }
        }

    suspend fun listSessions(): Result<List<OpenSession>> =
        request(path = "/sessions", method = "GET").map { text ->
            JSONObject(text).optJSONArray("sessions").map { s ->
                OpenSession(
                    code = s.getString("code"),
                    romTitle = s.stringOrNull("rom_title"),
                    romTitleId = s.stringOrNull("rom_title_id"),
                    hostName = s.stringOrNull("host_name"),
                    players = s.optInt("players", 0),
                    ready = s.optBoolean("ready", false),
                    ageSeconds = s.optInt("age_s", 0)
                )
            }
        }

    /** The coordinator drops members that fall silent: this repeats for the whole session. */
    suspend fun heartbeat(code: String, id: String, name: String): Result<Heartbeat> = request(
        path = "/sessions/$code/members",
        method = "POST",
        body = JSONObject().apply {
            put("id", id)
            put("name", name)
            putIdentityKey()
        }
    ).map { text ->
        val json = JSONObject(text)
        Heartbeat(
            json.optInt("players", 0),
            json.optString("member_token").ifBlank { null },
            json.optString("member_handle").ifBlank { null }
        )
    }

    suspend fun leaveSession(code: String, id: String, token: String?): Result<Unit> =
        request(path = "/sessions/$code/members/$id", method = "DELETE", bearer = token).map { }

    suspend fun deleteSession(code: String, token: String?): Result<Unit> =
        request(path = "/sessions/$code", method = "DELETE", readTimeout = 8000, bearer = token)
            .map { }

    suspend fun announcePresence(
        id: String,
        name: String,
        inSession: Boolean = false
    ): Result<Unit> = request(
        path = "/me",
        method = "POST",
        body = JSONObject().apply {
            put("id", id)
            put("name", name)
            put("in_session", inSession)
            putIdentityKey()
        }
    ).map { }

    /** Null hides it: the coordinator forgets the last one at once. */
    suspend fun announceLastGame(id: String, title: String?, titleId: String?): Result<Unit> = request(
        path = "/me",
        method = "POST",
        body = JSONObject().apply {
            put("id", id)
            putIdentityKey()
            if (title == null) put("last_game", JSONObject.NULL)
            else put("last_game", JSONObject().apply {
                put("title", title)
                if (titleId != null) put("title_id", titleId)
            })
        }
    ).map { }

    /** [key] is the device's own, drawn once; see `coordinator/avatars.js`. Returns the hash. */
    suspend fun uploadAvatar(id: String, key: String, webp: ByteArray): Result<String> = request(
        path = "/avatar",
        method = "PUT",
        body = JSONObject().apply {
            put("id", id)
            put("key", key)
            put("image", android.util.Base64.encodeToString(webp, android.util.Base64.NO_WRAP))
        },
        readTimeout = 10_000
    ).map { JSONObject(it).getString("hash") }

    suspend fun deleteAvatar(id: String, key: String): Result<Unit> = request(
        path = "/avatar/delete",
        method = "POST",
        body = JSONObject().apply {
            put("id", id)
            put("key", key)
        }
    ).map { }

    /** Bounded: the coordinator refuses more than 48 KB, so anything larger is not its. */
    suspend fun fetchAvatar(id: String): Result<ByteArray> =
        requestBytes(path = "/avatars/$id", method = "GET", maxBytes = AVATAR_MAX_BYTES)

    suspend fun fetchMemberAvatar(code: String, handle: String): Result<ByteArray> =
        requestBytes(path = "/sessions/$code/members/$handle/avatar", method = "GET", maxBytes = AVATAR_MAX_BYTES)

    suspend fun friendStatuses(codes: List<String>): Result<FriendsReply> {
        if (codes.isEmpty()) return Result.success(FriendsReply(emptyMap(), emptyMap()))
        return request(
            path = "/friends",
            method = "POST",
            body = JSONObject().apply { put("ids", JSONArray(codes)) }
        ).map { text ->
            val json = JSONObject(text)
            val present = json.optJSONArray("friends").map { f ->
                val session = f.optJSONObject("session")
                f.getString("id") to FriendPresence(
                    name = f.stringOrNull("name"),
                    sessionCode = session?.stringOrNull("code"),
                    romTitle = session?.stringOrNull("rom_title"),
                    romTitleId = session?.stringOrNull("rom_title_id"),
                    players = session?.optInt("players", 0) ?: 0,
                    ready = session?.optBoolean("ready", false) ?: false
                )
            }.toMap()
            // Absent from older coordinators.
            val names = json.optJSONObject("names")?.let { n ->
                n.keys().asSequence().mapNotNull { k -> n.stringOrNull(k)?.let { k to it } }.toMap()
            } ?: emptyMap()
            val avatars = json.optJSONObject("avatars")?.let { a ->
                a.keys().asSequence().mapNotNull { k -> a.stringOrNull(k)?.let { k to it } }.toMap()
            } ?: emptyMap()
            val games = json.optJSONObject("last_games")?.let { g ->
                g.keys().asSequence().mapNotNull { k ->
                    val o = g.optJSONObject(k) ?: return@mapNotNull null
                    val title = o.stringOrNull("title") ?: return@mapNotNull null
                    k to LastGame(title, o.stringOrNull("title_id"), o.optLong("at", 0L))
                }.toMap()
            } ?: emptyMap()
            FriendsReply(present, names, avatars, games)
        }
    }

    suspend fun claimAddress(
        code: String,
        publicKey: String,
        name: String? = null,
        profileId: String? = null
    ): Result<WgTunnelInfo> = request(
        path = "/sessions/$code/peers",
        method = "POST",
        body = JSONObject().apply {
            put("public_key", publicKey)
            if (name != null) put("name", name)
            if (profileId != null) {
                put("id", profileId)
                putIdentityKey()
            }
        },
        readTimeout = 15_000
    ).map { text ->
        val json = JSONObject(text)
        val relay = json.optJSONObject("relay")
            ?: error("the coordinator has no relay configured")
        WgTunnelInfo(
            address = json.getString("ip"),
            // `isNull`, never `optString`: the latter returns "null" on a JSON null.
            hairpinAddress = if (json.isNull("hairpin_ip")) null
            else json.optString("hairpin_ip").takeIf { it.isNotBlank() },
            subnet = json.getString("subnet"),
            relayEndpoint = relay.getString("endpoint"),
            relayPublicKey = relay.getString("public_key"),
            relayAllowedIps = relay.getString("allowed_ips")
        )
    }

    private suspend fun request(
        path: String,
        method: String,
        body: JSONObject? = null,
        readTimeout: Int = 4000,
        bearer: String? = null
    ): Result<String> =
        requestBytes(path, method, body, readTimeout, bearer).map { String(it, Charsets.UTF_8) }

    private suspend fun requestBytes(
        path: String,
        method: String,
        body: JSONObject? = null,
        readTimeout: Int = 4000,
        bearer: String? = null,
        maxBytes: Int = Int.MAX_VALUE,
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = body?.toString()
            val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 4000
                this.readTimeout = readTimeout
                if (body != null) {
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    doOutput = true
                }
                if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
                ClientAuth.sign(method, path, payload)?.let { s ->
                    setRequestProperty(ClientAuth.HEADER_AUTH, s.value)
                    setRequestProperty(ClientAuth.HEADER_TIMESTAMP, s.timestamp)
                    setRequestProperty(ClientAuth.HEADER_CLIENT, ClientAuth.clientVersion)
                }
            }
            try {
                // Sign `payload`, not `body.toString()`: two serialisations of one JSONObject need not match.
                payload?.let { conn.outputStream.use { out -> out.write(it.toByteArray(Charsets.UTF_8)) } }
                val status = conn.responseCode
                when {
                    status == 404 -> throw CoordinatorError.NotFound()
                    status !in 200..299 -> throw CoordinatorError.Http(status)
                    // 204 has no body, and reading it would throw.
                    status == 204 || conn.contentLength == 0 -> ByteArray(0)
                    else -> conn.inputStream.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (true) {
                            val n = input.read(chunk)
                            if (n < 0) break
                            out.write(chunk, 0, n)
                            if (out.size() > maxBytes) throw java.io.IOException("response over $maxBytes bytes")
                        }
                        out.toByteArray()
                    }
                }
            } finally {
                conn.disconnect()
            }
        }.recoverCatching { err ->
            throw err as? CoordinatorError ?: CoordinatorError.Unreachable(err)
        }
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.roomOrNull(): RoomRef? {
        val r = optJSONObject("room") ?: return null
        val host = r.stringOrNull("host") ?: return null
        val port = r.intOrNull("port") ?: return null
        val password = r.stringOrNull("password") ?: return null
        return RoomRef(host, port, password)
    }

    private fun JSONObject.intOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null

    private fun <T> JSONArray?.map(transform: (JSONObject) -> T): List<T> =
        if (this == null) emptyList() else (0 until length()).map { transform(getJSONObject(it)) }
}

private const val AVATAR_MAX_BYTES = 48 * 1024

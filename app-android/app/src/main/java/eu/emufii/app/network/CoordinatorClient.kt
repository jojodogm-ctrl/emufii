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

/**
 * debug  → host Mac loopback seen from an AVD, cleartext, allowed by
 *          network_security_config for that host only
 * release → hosted coordinator over HTTPS, overridden at build time with
 *          -Pemufii.coordinatorUrl=https://...
 */
val COORDINATOR_BASE_URL: String = BuildConfig.COORDINATOR_BASE_URL

/**
 * The code is public, so [token] is what authorises. Returned at creation only,
 * and it never leaves the device.
 * pourquoi : docs/decisions/coordinator-et-mise-a-jour.md § A token, because the session code is public
 */
data class CreatedSession(
    val code: String,
    val subnet: String,
    val token: String,
    val room: RoomRef? = null
)

/**
 * The distinction is the player's, not the log's: a missing code is theirs to fix, an
 * unreachable server is ours.
 * pourquoi : docs/decisions/coordinator-et-mise-a-jour.md § Telling "it does not exist" from "I could not ask"
 */
sealed class CoordinatorError(message: String) : Exception(message) {
    /** 404: no such session, or one purged after its TTL. */
    object NotFound : CoordinatorError("session introuvable")

    /** Nothing answered: no network, DNS, TLS, timeout. */
    class Unreachable(cause: Throwable) : CoordinatorError(cause.message ?: "injoignable")

    /** Answered without success: full, rate-limited, broken. */
    class Http(val status: Int) : CoordinatorError("HTTP $status")
}

data class Member(val id: String, val name: String, val forSeconds: Int)

/**
 * [memberHandle] is how this session lists us: compare against it, never against
 * a friend code. [memberToken] arrives on the first heartbeat only.
 * pourquoi : docs/decisions/coordinator-et-mise-a-jour.md § A token, because the session code is public
 */
data class Heartbeat(
    val players: Int,
    val memberToken: String?,
    val memberHandle: String?
)

/**
 * Both players join it, so nobody hosts on a phone. Null when none is offered,
 * and the app falls back on hosting by a player.
 * pourquoi : docs/decisions/coordinator-et-mise-a-jour.md § The Eden room on the VPS changes the shape of a Switch game
 */
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
    /**
     * True when the field is missing: the opposite would block every guest until
     * the coordinator is deployed.
     * pourquoi : docs/decisions/coordinator-et-mise-a-jour.md § The defaults for an absent field are chosen in a precise direction
     */
    val hostReady: Boolean,
    val members: List<Member>
)

/** No "online" flag: being present at all is the signal. */
data class FriendPresence(
    val name: String?,
    val sessionCode: String?,
    val romTitle: String?,
    val romTitleId: String?,
    val players: Int,
    val ready: Boolean
)

/** No network id: that comes with joining. */
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

    suspend fun createSession(
        code: String,
        romTitleId: String?,
        romTitle: String?,
        hostName: String? = null,
        hostId: String? = null,
        /**
         * The console, sent explicitly: the coordinator sees only a title and a
         * titleId, which 3DS and Switch write the same way.
         * pourquoi : docs/decisions/coordinator-et-mise-a-jour.md § The defaults for an absent field are chosen in a precise direction
         */
        console: String? = null,
        /**
         * A private session does not appear in the finder. Sent only when true;
         * absence means public.
         * pourquoi : docs/decisions/coordinator-et-mise-a-jour.md § The defaults for an absent field are chosen in a precise direction
         */
        private: Boolean = false
    ): Result<CreatedSession> = request(
        path = "/sessions",
        method = "POST",
        body = JSONObject().apply {
            put("code", code)
            if (romTitleId != null) put("rom_title_id", romTitleId)
            if (romTitle != null) put("rom_title", romTitle)
            if (hostName != null) put("host_name", hostName)
            if (hostId != null) put("host_id", hostId)
            if (console != null) put("console", console)
            if (private) put("private", true)
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

    /**
     * States that the host's room exists, or no longer does. Only the host may
     * say so, and only the host has the answer.
     * pourquoi : docs/decisions/coordinator-et-mise-a-jour.md § A token, because the session code is public
     */
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
                hostReady = json.optBoolean("host_ready", true),
                members = json.optJSONArray("members").map { m ->
                    Member(
                        id = m.getString("id"),
                        name = m.optString("name", Profile.DEFAULT_NAME),
                        forSeconds = m.optInt("for_s", 0)
                    )
                }
            )
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
        }
    ).map { text ->
        val json = JSONObject(text)
        Heartbeat(
            json.optInt("players", 0),
            json.optString("member_token").ifBlank { null },
            json.optString("member_handle").ifBlank { null }
        )
    }

    /** [token]: the one received on joining, or the host's if it is clearing up. */
    suspend fun leaveSession(code: String, id: String, token: String?): Result<Unit> =
        request(path = "/sessions/$code/members/$id", method = "DELETE", bearer = token).map { }

    suspend fun deleteSession(code: String, token: String?): Result<Unit> =
        request(path = "/sessions/$code", method = "DELETE", readTimeout = 8000, bearer = token)
            .map { }

    /**
     * Only needed outside a session: inside one, [heartbeat] already says we are here.
     * pourquoi : docs/decisions/coordinator-et-mise-a-jour.md § Presence outside a session, and why it goes out inside one
     */
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
        }
    ).map { }

    /**
     * Only the codes we send can come back: no listing route, no directory behind this.
     * pourquoi : docs/decisions/coordinator-et-mise-a-jour.md § The defaults for an absent field are chosen in a precise direction
     */
    suspend fun friendStatuses(codes: List<String>): Result<Map<String, FriendPresence>> {
        if (codes.isEmpty()) return Result.success(emptyMap())
        return request(
            path = "/friends",
            method = "POST",
            body = JSONObject().apply { put("ids", JSONArray(codes)) }
        ).map { text ->
            JSONObject(text).optJSONArray("friends").map { f ->
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
        }
    }

    /**
     * Idempotent on the WireGuard public key, so a retry lands on the same address.
     * pourquoi : docs/decisions/coordinator-et-mise-a-jour.md § Claiming an address is idempotent on the key
     */
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
            if (profileId != null) put("id", profileId)
        },
        readTimeout = 15_000
    ).map { text ->
        val json = JSONObject(text)
        val relay = json.optJSONObject("relay")
            ?: error("the coordinator has no relay configured")
        WgTunnelInfo(
            address = json.getString("ip"),
            // `isNull`, never `optString`: the latter returns "null" on a JSON null.
            // pourquoi : docs/decisions/coordinator-et-mise-a-jour.md § Claiming an address is idempotent on the key
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
    ): Result<String> = withContext(Dispatchers.IO) {
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
                // A build with no key sends nothing and talks to a dev coordinator,
                // which demands nothing.
                ClientAuth.sign(method, path, payload)?.let { s ->
                    setRequestProperty(ClientAuth.HEADER_AUTH, s.value)
                    setRequestProperty(ClientAuth.HEADER_TIMESTAMP, s.timestamp)
                    setRequestProperty(ClientAuth.HEADER_CLIENT, ClientAuth.clientVersion)
                }
            }
            try {
                // `payload`, not `body.toString()`: the signature covers those bytes, and
                // two serialisations of the same JSONObject need not match.
                payload?.let { conn.outputStream.use { out -> out.write(it.toByteArray(Charsets.UTF_8)) } }
                val status = conn.responseCode
                when {
                    status == 404 -> throw CoordinatorError.NotFound
                    status !in 200..299 -> throw CoordinatorError.Http(status)
                    // 204 has no body, and reading it would throw.
                    status == 204 || conn.contentLength == 0 -> ""
                    else -> conn.inputStream.bufferedReader().use { it.readText() }
                }
            } finally {
                conn.disconnect()
            }
        }.recoverCatching { err ->
            // Anything that is not already a verdict on the answer is a failure to get
            // one: `openConnection`, `responseCode` and the body read all surface as
            // IOException when nothing answers.
            throw if (err is CoordinatorError) err else CoordinatorError.Unreachable(err)
        }
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    /**
     * An incomplete room counts as no room: all three fields are needed to dial.
     * pourquoi : docs/decisions/coordinator-et-mise-a-jour.md § The Eden room on the VPS changes the shape of a Switch game
     */
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

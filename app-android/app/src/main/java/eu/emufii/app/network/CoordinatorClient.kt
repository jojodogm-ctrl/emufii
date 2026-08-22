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
 * debug  → host Mac loopback seen from an AVD (cleartext, allowed by
 *          network_security_config for that host only)
 * release → hosted coordinator over HTTPS; override at build time with
 *          -Pemufii.coordinatorUrl=https://...
 */
val COORDINATOR_BASE_URL: String = BuildConfig.COORDINATOR_BASE_URL

/**
 * A freshly created session, and the secret proving we are its host.
 *
 * The subnet is the session's own /24; this device's address on it comes from
 * [CoordinatorClient.claimAddress], because it depends on the WireGuard key the
 * device presents.
 *
 * The code itself is public, the finder publishes it. Without the token the
 * routes that modify a session demanded nothing: anyone could rewrite an unknown
 * game's host address, close it, or kick a player out. It is returned here only,
 * at creation, and never leaves the device.
 */
data class CreatedSession(
    val code: String,
    val subnet: String,
    val token: String,
    /** The room brought up for this session, or null. See [RoomRef]. */
    val room: RoomRef? = null
)

/**
 * Why a call to the coordinator failed.
 *
 * The distinction matters to the player, not just to the log: a code that
 * doesn't exist is their mistake to fix, while a coordinator that can't be
 * reached is ours. Collapsing the two, which is what a bare `getOrNull()` used
 * to do, told someone whose network was down that their friend's session
 * didn't exist.
 */
sealed class CoordinatorError(message: String) : Exception(message) {
    /** 404: no such session, or one whose TTL has passed and been purged. */
    object NotFound : CoordinatorError("session introuvable")

    /** Nothing answered: no network, DNS failure, TLS failure, timeout. */
    class Unreachable(cause: Throwable) : CoordinatorError(cause.message ?: "injoignable")

    /** Answered, but not with a success: full, rate-limited, broken. */
    class Http(val status: Int) : CoordinatorError("HTTP $status")
}

data class Member(val id: String, val name: String, val forSeconds: Int)

/**
 * What a heartbeat returns: who is there, and the means to remove oneself.
 *
 * [memberHandle] is the name this session gives us in its own list.
 *
 * The coordinator no longer publishes friend codes there, since reading them was
 * enough to follow anyone, so the identifier found in `members` is no longer
 * ours. This is the one to compare against in order to recognise ourselves.
 *
 * [memberToken] arrives on the first heartbeat only, the one that registers us;
 * afterwards the field is absent, deliberately. Handing it back to whoever asks
 * again amounted to handing it to whoever knows an identifier.
 */
data class Heartbeat(
    val players: Int,
    val memberToken: String?,
    val memberHandle: String?
)

/**
 * The Eden room the coordinator holds for this session, on the VPS.
 *
 * It changes the shape of a Switch game: instead of one player hosting on their
 * phone and the other reaching them through the tunnel, both join the same public
 * room. The "one player must be reachable" link, the most fragile in the chain
 * and the only one depending on the host's device, disappears. Proven on
 * 2026-08-05 before being written: `docs/PHASE1_SCOUT_EDEN_ROOM_VPS.md`.
 *
 * Null for every other console, and null too when the coordinator has no room to
 * offer, where the app falls back on hosting by a player, which has not gone
 * away.
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
     * Has the host opened its room in the emulator yet?
     *
     * True by default when the field is missing, and the direction is what
     * matters: the absence comes from an older coordinator, which knows nothing
     * of this question. The opposite default would have blocked every guest until
     * deployment, and a sequencing setting that prevents play is worse than the
     * ordering it fixes.
     */
    val hostReady: Boolean,
    val members: List<Member>
)

/**
 * A friend the coordinator currently sees. Absence from a reply means offline,
 * so this type has no "online" flag: having one at all is the signal.
 */
data class FriendPresence(
    val name: String?,
    val sessionCode: String?,
    val romTitle: String?,
    val romTitleId: String?,
    val players: Int,
    val ready: Boolean
)

/** A session as the finder sees it, no network id, that comes with joining. */
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
         * The console, when it decides whether a room goes up on the VPS.
         *
         * The coordinator does not guess it: it sees a title and a titleId,
         * which the 3DS and the Switch write the same way. So it is the app's
         * job to say, and saying nothing means "no room".
         */
        console: String? = null,
        /**
         * A private session does not appear in the finder: its code is needed.
         *
         * Sent only when true. The coordinator treats absence as "public", so
         * keeping quiet about the default avoids making behaviour depend on a
         * field the app might one day forget.
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
     * States that the host's room exists, or no longer does.
     *
     * Only the host may say so, the session token is what authorises it, and only
     * the host has the answer: the coordinator cannot see inside the emulator.
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

    /** Everything joinable right now. Powers the session finder. */
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

    /**
     * The session heartbeat: announcing, and going on announcing, that we are
     * here. The coordinator drops members that fall silent, so the call repeats
     * for as long as the session lasts.
     *
     * It also reports what identifies this player, see [Heartbeat]: the token
     * allowing self-removal, on the first call only, and the handle the session
     * will list us under, every time.
     */
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
     * Say we're here, so friends holding our code can see it.
     *
     * Only needed outside a session: [heartbeat] already reports presence, and
     * says which game we're in while doing it. Passing `inSession = false` on
     * the way out clears that straight away rather than leaving friends looking
     * at a game that ended.
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
     * Ask which of these friends are online, and what they're playing.
     *
     * Only the codes we send can come back, there is no listing endpoint and
     * no directory behind this. Friends who are offline are simply absent from
     * the reply.
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
     * Claims this device's address on a session, presenting the WireGuard public key
     * the tunnel will use.
     *
     * Idempotent on the key server-side, so a retry after a dropped reply lands
     * on the same address rather than burning a second one and leaving the relay
     * routing to a peer nobody is behind.
     *
     * [profileId] lets the coordinator recognise the host claiming its own address
     * and publish `host_ip` itself, so the app never has to report it back.
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
            ?: error("le coordinator n'a pas de relais configuré")
        WgTunnelInfo(
            address = json.getString("ip"),
            // Absent on a guest, and absent too from a coordinator older than
            // 2026-08-03: in both cases the interface has one address, which is
            // exactly the old behaviour. `isNull` covers both at once and avoids
            // the `optString` trap, which returns the string "null" on a JSON
            // null.
            hairpinAddress = if (json.isNull("hairpin_ip")) null
            else json.optString("hairpin_ip").takeIf { it.isNotBlank() },
            subnet = json.getString("subnet"),
            relayEndpoint = relay.getString("endpoint"),
            relayPublicKey = relay.getString("public_key"),
            relayAllowedIps = relay.getString("allowed_ips")
        )
    }

    // -- plumbing --

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
                // What tells Emufii apart from any other client. A build with no
                // key sends nothing and talks to a dev coordinator, which demands
                // nothing. See `ClientAuth`.
                ClientAuth.sign(method, path, payload)?.let { s ->
                    setRequestProperty(ClientAuth.HEADER_AUTH, s.value)
                    setRequestProperty(ClientAuth.HEADER_TIMESTAMP, s.timestamp)
                    setRequestProperty(ClientAuth.HEADER_CLIENT, ClientAuth.clientVersion)
                }
            }
            try {
                // `payload` and not `body.toString()`: the signature covers
                // those bytes, and two successive serialisations of the same
                // JSONObject are under no obligation to match.
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
            // Everything that is not already a verdict on the answer is a
            // failure to get one at all: `openConnection`, `responseCode` and
            // the body read all surface as IOException when nothing answers.
            throw if (err is CoordinatorError) err else CoordinatorError.Unreachable(err)
        }
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    /**
     * The room, or null, including against a coordinator that knows of none.
     *
     * An incomplete room counts as no room: all three fields are needed to dial,
     * and falling back on hosting by a player beats aiming at a guessed port.
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

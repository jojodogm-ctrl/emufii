package eu.emufii.app.onetap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

enum class OneTapGame(val wireName: String) { MARIO_PARTY_DS("mpds"), MARIO_KART_DS("mkds") }
enum class OneTapPhase { AWAITING_ONE_TAP_HOST, LINKED }

data class OneTapLinkedRoom(val code: String, val browserUrl: String, val nativeUrl: String, val nativeUri: String)
data class OneTapSession(
    val code: String,
    val game: OneTapGame,
    val players: Int,
    val phase: OneTapPhase,
    val expiresAt: Long,
    val oneTap: OneTapLinkedRoom?,
) {
    // This adapter has no runtime readiness proof. A linked room is never a ready game.
    val hostReady: Boolean get() = false
}

class OneTapOwner internal constructor(internal val code: String, internal val token: String) {
    override fun toString(): String = "OneTapOwner(capability=redacted)"
}
data class OneTapCreatedSession(val session: OneTapSession, val owner: OneTapOwner)

sealed class OneTapClientError(message: String) : Exception(message) {
    class Http(val status: Int) : OneTapClientError("one-tap-http-$status")
    class InvalidResponse : OneTapClientError("one-tap-invalid-response")
    class Unreachable : OneTapClientError("one-tap-unreachable")
}

/** Injected by tests; credentials must never be logged through a request's string representation. */
class OneTapHttpRequest(val url: String, val method: String, val body: String?, val bearer: String?) {
    override fun toString(): String = "OneTapHttpRequest(method=$method, body=redacted, capability=redacted)"
}
class OneTapHttpResponse(val status: Int, val body: String) {
    override fun toString(): String = "OneTapHttpResponse(status=$status, body=redacted)"
}
fun interface OneTapTransport { fun execute(request: OneTapHttpRequest): OneTapHttpResponse }

/** Client of the Emufii server adapter, whose origin must be explicitly configured after deployment.
 * The dedicated coordinator HMAC key belongs to that server and is never a mobile input.
 * The actual One Tap host links its own room; this client cannot reserve or invent its host seat.
 */
class OneTapClient(
    baseUrl: String,
    private val transport: OneTapTransport = OneTapUrlConnectionTransport(),
) {
    private val baseUrl = validatedOrigin(baseUrl)

    suspend fun createSession(
        code: String,
        game: OneTapGame,
        hostName: String,
        romTitleId: String? = null,
        romTitle: String? = null,
    ): Result<OneTapCreatedSession> = operation {
        val normalized = sessionCode(code)
        require(hostName.isNotBlank() && hostName == hostName.trim() && hostName.length <= 10 &&
            !forbiddenName.containsMatchIn(hostName)) { "one-tap-player-name" }
        val body = JSONObject().put("code", normalized).put("game", game.wireName).put("hostName", hostName)
        romTitleId?.let { body.put("romTitleId", it) }
        romTitle?.let { body.put("romTitle", it) }
        val response = request("POST", ROOT, body.toString())
        parseResponse {
            val json = JSONObject(response)
            fields(json, "ownerToken", "session")
            val token = json.get("ownerToken") as? String ?: invalid()
            if (!ownerToken.matches(token)) invalid()
            val session = parseSession(json.getJSONObject("session"))
            if (session.code != normalized || session.game != game || session.phase != OneTapPhase.AWAITING_ONE_TAP_HOST) invalid()
            OneTapCreatedSession(session, OneTapOwner(normalized, token))
        }
    }

    suspend fun getSession(code: String): Result<OneTapSession> = operation {
        val normalized = sessionCode(code)
        val response = request("GET", "$ROOT/$normalized")
        parseResponse {
            parseSession(JSONObject(response)).also { if (it.code != normalized) invalid() }
        }
    }

    suspend fun deleteSession(owner: OneTapOwner): Result<Unit> = operation<Unit> {
        request("DELETE", "$ROOT/${sessionCode(owner.code)}", bearer = owner.token)
    }

    private fun request(method: String, path: String, body: String? = null, bearer: String? = null): String {
        val response = try { transport.execute(OneTapHttpRequest(baseUrl + path, method, body, bearer)) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { throw OneTapClientError.Unreachable() }
        if (response.status !in 200..299) throw OneTapClientError.Http(response.status)
        if (response.body.toByteArray(Charsets.UTF_8).size > MAX_RESPONSE_BYTES) invalid()
        return response.body
    }

    private suspend fun <T> operation(action: () -> T): Result<T> = withContext(Dispatchers.IO) {
        try { Result.success(action()) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    companion object {
        private const val ROOT = "/v1/one-tap/sessions"
        internal const val MAX_RESPONSE_BYTES = 64 * 1024
        private val emufiiCode = Regex("[A-HJ-NP-Z]{3}-[2-9]{3}")
        private val ownerToken = Regex("[a-f0-9]{64}")
        private val forbiddenName = Regex("[\\x00-\\x1f\\x7f-\\x9f\\ud800-\\udfff\\u061c\\u200e\\u200f\\u2028-\\u202e\\u2066-\\u2069]")

        fun sessionCode(value: String): String {
            val raw = value.trim()
            require(raw.all { it.code <= 127 }) { "one-tap-emufii-session-code" }
            return raw.uppercase(Locale.ROOT).also {
                require(emufiiCode.matches(it)) { "one-tap-emufii-session-code" }
            }
        }

        private fun validatedOrigin(value: String): String {
            val uri = runCatching { URI(value) }.getOrNull()
            require(uri != null && uri.scheme == "https" && !uri.host.isNullOrEmpty() &&
                uri.rawUserInfo == null && uri.port == -1 && uri.rawQuery == null && uri.rawFragment == null &&
                uri.rawPath.isNullOrEmpty() && uri.toASCIIString() == value) { "one-tap-adapter-origin" }
            return value
        }

        private fun invalid(): Nothing = throw OneTapClientError.InvalidResponse()
        private fun fields(json: JSONObject, vararg expected: String) {
            if (json.keys().asSequence().toSet() != expected.toSet()) invalid()
        }
        private fun integer(json: JSONObject, key: String): Long {
            val number = json.get(key)
            if (number !is Int && number !is Long) invalid()
            return (number as Number).toLong()
        }
        private fun parseSession(json: JSONObject): OneTapSession {
            fields(json, "code", "game", "players", "phase", "expiresAt", "hostReady", "oneTap")
            val code = json.get("code") as? String ?: invalid()
            if (!emufiiCode.matches(code)) invalid()
            val game = OneTapGame.entries.singleOrNull { it.wireName == json.get("game") } ?: invalid()
            if (integer(json, "players") != 4L || json.get("hostReady") != false) invalid()
            val expiresAt = integer(json, "expiresAt")
            if (expiresAt !in 1..9_007_199_254_740_991L) invalid()
            val phase = when (json.get("phase")) {
                "awaiting-one-tap-host" -> OneTapPhase.AWAITING_ONE_TAP_HOST
                "linked" -> OneTapPhase.LINKED
                else -> invalid()
            }
            val room = if (json.isNull("oneTap")) null else {
                val linked = json.getJSONObject("oneTap")
                fields(linked, "code", "browserUrl", "nativeUrl", "nativeUri")
                val roomCode = linked.get("code") as? String ?: invalid()
                if (!Regex("[A-Z0-9]{4}").matches(roomCode) ||
                    linked.get("browserUrl") != OneTapEntry.browserRoom(roomCode) ||
                    linked.get("nativeUrl") != OneTapEntry.publicInvite(roomCode) ||
                    linked.get("nativeUri") != OneTapEntry.nativeRoom(roomCode)) invalid()
                OneTapLinkedRoom(roomCode, OneTapEntry.browserRoom(roomCode),
                    OneTapEntry.publicInvite(roomCode), OneTapEntry.nativeRoom(roomCode))
            }
            if ((phase == OneTapPhase.LINKED) != (room != null)) invalid()
            return OneTapSession(code, game, 4, phase, expiresAt, room)
        }

        private fun <T> parseResponse(action: () -> T): T = try { action() }
        catch (_: Exception) { invalid() }
    }
}

private class OneTapUrlConnectionTransport : OneTapTransport {
    override fun execute(request: OneTapHttpRequest): OneTapHttpResponse {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.requestMethod = request.method
            connection.setRequestProperty("Accept", "application/json")
            request.bearer?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            request.body?.let {
                val bytes = it.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { stream -> stream.write(bytes) }
            }
            val status = connection.responseCode
            if (status !in 200..299) return OneTapHttpResponse(status, "")
            val bytes = connection.inputStream.use { it.readNBytes(OneTapClient.MAX_RESPONSE_BYTES + 1) }
            if (bytes.size > OneTapClient.MAX_RESPONSE_BYTES) throw OneTapClientError.InvalidResponse()
            return OneTapHttpResponse(status, bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
}

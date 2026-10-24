package com.mpds.online

import java.io.ByteArrayOutputStream
import java.net.CookieHandler
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Collections
import java.util.concurrent.FutureTask
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/** Native guest directory protocol; its application-owned transport may bind
 * requests to a private Emufii membership. Blocking calls run on a worker.
 * Calls are serialized; the caller owns polling and inner-runner teardown when phase or
 * generation changes. No persistence, logging, UI callbacks, or automatic retries.
 */
class RoomDirectory internal constructor(private val transport: Transport) {
    constructor() : this(PublicTransport)

    enum class Phase { WAITING, RUNNING }
    enum class Game { MPDS, MKDS, NSMB, DKR, EXPERIMENTAL }
    enum class GuestBoot { CARTRIDGE, DOWNLOAD_PLAY }
    data class Settings(val game: Game?, val players: Int, val askPlayerSaves: Boolean,
                        val sendROM: Boolean?, val romSha256: String?, val romBytes: Long?,
                        val guestBoot: GuestBoot?)
    data class Player(val seat: Int, val name: String, val connected: Boolean)
    data class Snapshot(val code: String, val seat: Int, val revision: Long,
                        val settings: Settings, val roster: List<Player>, val phase: Phase,
                        val generation: Long)

    /** Safe to show/log; neither remote text nor exception causes are retained. */
    class DirectoryError internal constructor(val status: Int, val code: String,
                                             val retryAfterMs: Long = 0) : Exception("directory-request")

    // Kotlin internal is module visibility (Kotlin has no package-private visibility).
    // These capabilities belong only to the inner runner, never UI/status/storage.
    internal class Launch internal constructor(val generation: Long, val invitation: String,
                                              val startBarrierProtocol: Int?, val stopIntentProtocol: Int?) {
        // Envelope compatibility only; the lobby roster must still negotiate
        // checkpoint support. Legacy native hosts reject extra hello fields.
        fun canOfferAuthorityCheckpoints(enabled: Boolean, game: Game?) =
            enabled && startBarrierProtocol == 1 && game in listOf(Game.MKDS,Game.NSMB)
        override fun toString() = "Launch(redacted)"
    }
    internal class GatewayTicket internal constructor(val ticket: String) {
        override fun toString() = "GatewayTicket(redacted)"
    }
    /** Preparation capability only; never a game admission or signed manifest. */
    internal class RomPrefetchOffer internal constructor(
        val attempt: String, val generation: Long, val bytes: Long, val sha256: String,
        val expiresAtUnixMs: Long, val seat: Int, val room: String,
        val invitationId: String, internal val capability: String
    ) {
        override fun toString() = "RomPrefetchOffer(redacted)"
    }
    internal fun interface Transport {
        fun post(code: String, operation: String, bearer: String?, body: ByteArray): Response
    }
    internal class Response(val status: Int, val contentType: String?, val retryAfter: String?, val body: ByteArray)
    private class Credential(val code: String, val token: String, val seat: Int)
    private class State(val snapshot: Snapshot, val launch: Launch?, val settingsWire: String, val launchWire: String)
    private var credential: Credential? = null
    private var state: State? = null
    private var verified = false
    private var prefetchUnsupported = false
    private var prefetchOffer: RomPrefetchOffer? = null

    @get:Synchronized
    val snapshot: Snapshot? get() = state?.snapshot

    @Synchronized
    fun join(code: String, name: String): Snapshot {
        require(credential == null) { "room-already-joined" }
        require(CODE.matches(code)) { "room-code" }
        name(name)
        val value = obj(request(code, "join", null, "{\"name\":${quote(name)}}"))
        fields(value, "code", "token", "seat", "snapshot")
        ensure(value["code"] == code)
        val token = string(value["token"]); ensure(TOKEN.matches(token))
        val next = Credential(code, token, integer(value["seat"], 2, 8).toInt())
        val accepted = validateSnapshot(value["snapshot"], next, null)
        credential = next
        state = accepted
        verified = true
        return accepted.snapshot
    }

    @Synchronized
    fun poll(name: String): Snapshot {
        name(name)
        return update("poll", "{\"name\":${quote(name)}}")
    }

    @Synchronized
    fun failed(generation: Long): Snapshot {
        current(generation)
        return update("failed", "{\"generation\":$generation}")
    }

    /** Clears local capabilities even if the remote leave fails; the remote lease then expires. */
    @Synchronized
    fun leave() {
        val c = credential ?: return
        try {
            val value = obj(request(c.code, "leave", c.token, "{}"))
            fields(value, "ok"); ensure(value["ok"] == true)
        } finally {
            credential = null; state = null; verified = false
            prefetchOffer = null; prefetchUnsupported = false
        }
    }

    @Synchronized
    internal fun readRomPrefetch(): RomPrefetchOffer? {
        val c = credential ?: throw IllegalStateException("room-not-joined")
        val s = state!!.snapshot
        check(verified && s.phase == Phase.WAITING) { "room-preparation" }
        prefetchOffer = null
        if (prefetchUnsupported || s.settings.sendROM == false || s.settings.game == null) return null
        val value = try { obj(request(c.code, "rom-prefetch-read", c.token, "{}")) }
        catch (e: DirectoryError) {
            if (e.status != 404) throw e
            prefetchUnsupported = true
            return null
        }
        fields(value, "offer")
        if (value["offer"] == null) return null
        val o = obj(value["offer"])
        fields(o, "version", "attempt", "generation", "rom", "expiresAtUnixMs", "seat", "route")
        ensure(integer(o["version"], 1, 1) == 1L)
        val attempt = string(o["attempt"]); ensure(Regex("[0-9a-f]{32}").matches(attempt))
        val generation = integer(o["generation"], 1, MAX_SAFE)
        ensure(s.generation < MAX_SAFE && generation == s.generation + 1)
        val rom = obj(o["rom"]); fields(rom, "bytes", "sha256")
        val bytes = integer(rom["bytes"], 512, 536870912)
        val hash = string(rom["sha256"]); ensure(Regex("[0-9a-f]{64}").matches(hash))
        if (s.settings.game == Game.EXPERIMENTAL)
            ensure(hash == s.settings.romSha256 && bytes == s.settings.romBytes)
        val expiry = integer(o["expiresAtUnixMs"], 1, MAX_SAFE)
        ensure(expiry - System.currentTimeMillis() in 1..600000)
        val seat = integer(o["seat"], 2, 8).toInt(); ensure(seat == c.seat)
        val route = obj(o["route"]); fields(route, "room", "invitationId", "capability")
        val room = string(route["room"]); ensure(ROUTE.matches(room))
        val id = string(route["invitationId"]); ensure(Regex("[0-9a-f]{32}").matches(id))
        val cap = string(route["capability"])
        ensure(Regex("[0-9a-f]{64}").matches(cap) && cap.any { it != '0' })
        return RomPrefetchOffer(attempt, generation, bytes, hash, expiry, seat, room, id, cap)
            .also { prefetchOffer = it }
    }

    /** Ticket restricted to the last validated preparation route, with no launch access. */
    @Synchronized
    internal fun preparationTicket(offer: RomPrefetchOffer): GatewayTicket {
        val c = credential ?: throw IllegalStateException("room-not-joined")
        fun checkOffer() {
            val s = state!!.snapshot
            check(verified && prefetchOffer === offer && s.phase == Phase.WAITING &&
                s.generation + 1 == offer.generation && c.seat == offer.seat &&
                offer.expiresAtUnixMs > System.currentTimeMillis()) { "room-preparation" }
        }
        checkOffer()
        val value = obj(request(c.code, "gateway-ticket", c.token,
            "{\"generation\":${offer.generation},\"room\":${quote(offer.room)},\"seat\":${offer.seat - 1},\"role\":\"client\"}"))
        fields(value, "ticket")
        val ticket = string(value["ticket"]); ensure(TOKEN.matches(ticket))
        checkOffer()
        return GatewayTicket(ticket)
    }

    /** Framing only: runner MUST authenticate invitation/game/seat/epoch before using it. */
    @Synchronized
    internal fun launchForRunner(generation: Long): Launch {
        current(generation)
        return state?.launch ?: throw IllegalStateException("room-launch")
    }

    @Synchronized
    internal fun gatewayTicket(generation: Long, room: String, seat: Int, role: String,
                               expiresAtUnixMs: Long): GatewayTicket {
        val c = current(generation)
        require(ROUTE.matches(room) && seat == c.seat - 1 &&
            seat in 0 until state!!.snapshot.settings.players && role == "client" &&
            expiresAtUnixMs in 1..MAX_SAFE && expiresAtUnixMs > System.currentTimeMillis()) { "gateway-route" }
        val value = obj(request(c.code, "gateway-ticket", c.token,
            "{\"generation\":$generation,\"room\":${quote(room)},\"seat\":$seat,\"role\":\"client\"}"))
        fields(value, "ticket")
        val ticket = string(value["ticket"]); ensure(TOKEN.matches(ticket))
        current(generation)
        require(expiresAtUnixMs > System.currentTimeMillis()) { "gateway-route" }
        return GatewayTicket(ticket)
    }

    private fun current(generation: Long): Credential {
        val c = credential ?: throw IllegalStateException("room-not-joined")
        require(verified && generation in 1..MAX_SAFE && state?.snapshot?.generation == generation &&
            state?.snapshot?.phase == Phase.RUNNING) { "room-generation" }
        return c
    }

    private fun update(operation: String, body: String): Snapshot {
        val c = credential ?: throw IllegalStateException("room-not-joined")
        verified = false
        prefetchOffer = null
        val accepted = validateSnapshot(request(c.code, operation, c.token, body), c, state)
        state = accepted
        verified = true
        return accepted.snapshot
    }

    private fun request(code: String, operation: String, token: String?, body: String): Any? {
        try {
            val r = transport.post(code, operation, token, body.toByteArray(Charsets.UTF_8))
            // Legacy routers may return HTML or an empty 404. Only this additive
            // operation treats that status as unsupported; all other errors retain policy.
            if (operation == "rom-prefetch-read" && r.status == 404)
                throw DirectoryError(404, "prefetch_unsupported")
            ensure(r.status !in 300..399 && r.contentType?.substringBefore(';')?.trim() == "application/json")
            ensure(r.body.size <= MAX_BODY)
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val value = parseStrictJson(decoder.decode(ByteBuffer.wrap(r.body)).toString())
            if (r.status !in 200..299) {
                val raw = ((value as? Map<*, *>)?.get("error") as? Map<*, *>)?.get("code")
                val error = if (raw is String && Regex("[a-z_]{1,64}").matches(raw)) raw else "request_failed"
                val retry = r.retryAfter?.takeIf { Regex("[0-9]{1,6}").matches(it) }
                    ?.toLong()?.times(1000)?.coerceAtMost(60000) ?: 0
                throw DirectoryError(r.status, error, retry)
            }
            return value
        } catch (e: DirectoryError) { throw e }
        catch (_: Exception) { throw DirectoryError(0, "directory_response") }
    }

    private fun validateSnapshot(raw: Any?, c: Credential, previous: State?): State {
        val v = obj(raw)
        fields(v, "code", "revision", "settings", "roster", "phase", "generation", "launch")
        ensure(v["code"] == c.code)
        val revision = integer(v["revision"], 1, MAX_SAFE)
        val generation = integer(v["generation"], 0, MAX_SAFE)
        val phase = when (v["phase"]) { "waiting" -> Phase.WAITING; "running" -> Phase.RUNNING; else -> invalid() }
        val s = obj(v["settings"])
        val keys = mutableListOf("game", "players", "askPlayerSaves")
        if (s.containsKey("sendROM")) keys.add("sendROM")
        if (s["game"] == "experimental") keys.addAll(listOf("romSha256", "romBytes", "guestBoot"))
        fields(s, *keys.toTypedArray())
        val game = when (s["game"]) {
            null -> null; "mpds" -> Game.MPDS; "mkds" -> Game.MKDS; "nsmb" -> Game.NSMB
            "dkr" -> Game.DKR; "experimental" -> Game.EXPERIMENTAL; else -> invalid()
        }
        val players = integer(s["players"], 2, 8).toInt()
        val saves = bool(s["askPlayerSaves"])
        val sendROM = if (s.containsKey("sendROM")) bool(s["sendROM"]) else null
        ensure((game != Game.MPDS || players <= 4) && (game !in listOf(Game.NSMB, Game.DKR) || players == 2))
        var hash: String? = null; var bytes: Long? = null; var boot: GuestBoot? = null
        if (game == Game.EXPERIMENTAL) {
            hash = string(s["romSha256"]); ensure(Regex("[0-9a-f]{64}").matches(hash))
            bytes = integer(s["romBytes"], 512, 536870912)
            boot = when (s["guestBoot"]) { "cartridge" -> GuestBoot.CARTRIDGE; "download-play" -> GuestBoot.DOWNLOAD_PLAY; else -> invalid() }
            ensure(!saves || boot == GuestBoot.CARTRIDGE)
        }
        val settings = Settings(game, players, saves, sendROM, hash, bytes, boot)
        val roster = v["roster"] as? List<*> ?: invalid()
        ensure(roster.size <= players)
        var last = 0
        val people = roster.map {
            val p = obj(it); fields(p, "seat", "name", "connected")
            val seat = integer(p["seat"], (last + 1).toLong(), players.toLong()).toInt()
            val n = string(p["name"]); name(n); ensure(p["connected"] == true)
            last = seat; Player(seat, n, true)
        }
        ensure(people.any { it.seat == c.seat })
        var launch: Launch? = null
        if (phase == Phase.WAITING) ensure(v["launch"] == null)
        else {
            ensure(generation > 0)
            val l = obj(v["launch"])
            val lk = mutableListOf("generation", "invitation")
            for (key in listOf("startBarrierProtocol", "stopIntentProtocol")) if (l.containsKey(key)) lk.add(key)
            fields(l, *lk.toTypedArray())
            ensure(integer(l["generation"], 1, MAX_SAFE) == generation)
            val invitation = string(l["invitation"])
            ensure(invitation.length <= 30000 && invitation.startsWith(INVITE) &&
                Regex("[A-Za-z0-9_-]+").matches(invitation.substring(INVITE.length)))
            fun protocol(key: String): Int? = if (l.containsKey(key)) integer(l[key], 1, 1).toInt() else null
            launch = Launch(generation, invitation, protocol("startBarrierProtocol"), protocol("stopIntentProtocol"))
        }
        // Preserve insertion order as JSON.stringify does for these non-numeric field names.
        val settingsWire = canonical(s); val launchWire = canonical(v["launch"])
        previous?.let {
            ensure(revision >= it.snapshot.revision && generation >= it.snapshot.generation)
            ensure(it.snapshot.settings.sendROM == null || sendROM != null)
            if (generation == it.snapshot.generation && it.snapshot.phase == Phase.RUNNING)
                ensure(phase == Phase.RUNNING && settingsWire == it.settingsWire && launchWire == it.launchWire)
        }
        return State(Snapshot(c.code, c.seat, revision, settings,
            Collections.unmodifiableList(ArrayList(people)), phase, generation), launch, settingsWire, launchWire)
    }

    companion object {
        const val DIRECTORY_ENDPOINT = BuildConfig.DIRECTORY_ENDPOINT
        /** Strict grammar/duplicate-key/depth decoder shared with the inner invitation validator.
         * Numbers have JavaScript Double semantics; caller validates schema and input byte bounds.
         */
        internal fun parseStrictJson(text: String): Any? = Json(text).parse()
        private const val INVITE = "https://party.lepast.fr/nifi2/join/#"
        private const val MAX_BODY = 65536
        private const val MAX_SAFE = 9007199254740991L
        private val CODE = Regex("[A-Z0-9]{4}")
        private val TOKEN = Regex("[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]")
        private val ROUTE = Regex("[A-Za-z0-9_-]{1,15}")
        private fun invalid(): Nothing = throw DirectoryError(0, "directory_response")
        private fun ensure(ok: Boolean) { if (!ok) invalid() }
        private fun string(v: Any?): String = v as? String ?: invalid()
        private fun bool(v: Any?): Boolean = v as? Boolean ?: invalid()
        @Suppress("UNCHECKED_CAST")
        private fun obj(v: Any?): Map<String, Any?> = v as? Map<String, Any?> ?: invalid()
        private fun fields(v: Map<String, Any?>, vararg keys: String) {
            ensure(v.size == keys.size && keys.all { v.containsKey(it) })
        }
        private fun integer(v: Any?, min: Long, max: Long): Long {
            val d = v as? Double ?: invalid()
            ensure(d.isFinite() && d >= min.toDouble() && d <= max.toDouble() && d == d.toLong().toDouble())
            return d.toLong()
        }
        // ECMAScript trim whitespace, not Java/Kotlin's differing whitespace predicates.
        private fun whitespace(c: Char) = c in "\u0009\u000a\u000b\u000c\u000d\u0020\u00a0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200a\u2028\u2029\u202f\u205f\u3000\ufeff"
        internal fun name(v: String) {
            require(v.length <= 10 && (v.isEmpty() || (!whitespace(v.first()) && !whitespace(v.last()))) &&
                v.none { it <= '\u001f' || it in '\u007f'..'\u009f' || it in '\ud800'..'\udfff' ||
                    it == '\u061c' || it == '\u200e' || it == '\u200f' || it in '\u2028'..'\u202e' || it in '\u2066'..'\u2069' }) { "invalid_name" }
        }
        private fun quote(s: String): String = buildString {
            append('"')
            for (c in s) when (c) {
                '"' -> append("\\\""); '\\' -> append("\\\\")
                else -> if (c < ' ' || c in '\ud800'..'\udfff') append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
            }
            append('"')
        }
        private fun canonical(v: Any?): String = when (v) {
            null -> "null"; is String -> quote(v); is Boolean -> v.toString()
            is Double -> v.toLong().toString() // Only validated safe integers reach this serializer.
            is Map<*, *> -> v.entries.joinToString(",", "{", "}") { quote(it.key as String) + ":" + canonical(it.value) }
            else -> invalid()
        }
    }

    /** Strict RFC JSON grammar with JS Number semantics, decoded duplicate keys and depth <=32. */
    private class Json(private val text: String) {
        private var at = 0
        fun parse(): Any? { val v = value(0); ws(); ensure(at == text.length); return v }
        private fun ws() { while (at < text.length && text[at] in " \t\r\n") at++ }
        private fun take(c: Char): Boolean = if (at < text.length && text[at] == c) { at++; true } else false
        private fun value(depth: Int): Any? {
            ws(); ensure(at < text.length)
            return when (text[at]) {
                '{' -> {
                    ensure(depth < 32); at++; ws()
                    val map = linkedMapOf<String, Any?>()
                    if (!take('}')) do {
                        ws(); val key = str(); ensure(!map.containsKey(key)); ws(); ensure(take(':'))
                        map[key] = value(depth + 1); ws()
                        if (take('}')) break
                        ensure(take(','))
                    } while (true)
                    map
                }
                '[' -> {
                    ensure(depth < 32); at++; ws(); val list = ArrayList<Any?>()
                    if (!take(']')) do {
                        list.add(value(depth + 1)); ws(); if (take(']')) break; ensure(take(','))
                    } while (true)
                    list
                }
                '"' -> str()
                't' -> literal("true", true); 'f' -> literal("false", false); 'n' -> literal("null", null)
                else -> {
                    val start = at
                    take('-')
                    if (!take('0')) { ensure(at < text.length && text[at] in '1'..'9'); digits() }
                    if (take('.')) { val p = at; digits(); ensure(at > p) }
                    if (take('e') || take('E')) { if (!take('+')) take('-'); val p = at; digits(); ensure(at > p) }
                    text.substring(start, at).toDoubleOrNull() ?: invalid()
                }
            }
        }
        private fun digits() { while (at < text.length && text[at] in '0'..'9') at++ }
        private fun literal(word: String, v: Any?): Any? { ensure(text.startsWith(word, at)); at += word.length; return v }
        private fun str(): String {
            ensure(take('"')); val out = StringBuilder()
            while (at < text.length) {
                val c = text[at++]; if (c == '"') return out.toString()
                ensure(c >= ' ')
                if (c != '\\') out.append(c) else {
                    ensure(at < text.length)
                    out.append(when (val e = text[at++]) {
                        '"', '\\', '/' -> e; 'b' -> '\b'; 'f' -> '\u000c'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
                        'u' -> {
                            ensure(at + 4 <= text.length)
                            val hex = text.substring(at, at + 4); ensure(hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
                            at += 4; hex.toInt(16).toChar()
                        }
                        else -> invalid()
                    })
                }
            }
            invalid()
        }
    }

    /** Platform certificate/hostname validation, with a fixed HTTPS endpoint.
     * The application must not install global credential handlers during a request.
     * Endpoint pinning is not certificate pinning. No custom trust or hostname verifier.
     */
    private object PublicTransport : Transport {
        override fun post(code: String, operation: String, bearer: String?, body: ByteArray): Response {
            require(CODE.matches(code) && operation in listOf("join", "poll", "leave", "failed", "gateway-ticket", "rom-prefetch-read"))
            require(if (operation == "join") bearer == null else bearer != null && TOKEN.matches(bearer))
            // Android has no Authenticator.getDefault(). This application never
            // installs a global authenticator; bearer credentials are explicit.
            ensure(CookieHandler.getDefault() == null)
            val connection = URL("$DIRECTORY_ENDPOINT/$code/$operation").openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.useCaches = false
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Cache-Control", "no-store")
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
            // Future deadline bounds DNS, TLS, writes and trickle responses as a whole,
            // in addition to the platform's connect and read inactivity timeouts.
            val task = FutureTask {
                try {
                    if (Thread.currentThread().isInterrupted) invalid()
                    ensure(CookieHandler.getDefault() == null)
                    connection.outputStream.use { it.write(body) }
                    val status = connection.responseCode
                    if (operation == "rom-prefetch-read" && status == 404)
                        return@FutureTask Response(status, null, null, byteArrayOf())
                    ensure(status !in 300..399)
                    val contentType = connection.getHeaderField("Content-Type")
                    ensure(contentType?.substringBefore(';')?.trim() == "application/json")
                    ensure(connection.contentLengthLong <= MAX_BODY)
                    val stream = if (status >= 400) connection.errorStream else connection.inputStream
                    ensure(stream != null)
                    val bytes = stream!!.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            if (Thread.currentThread().isInterrupted) invalid()
                            val n = input.read(buffer)
                            if (n == -1) break
                            ensure(output.size() + n <= MAX_BODY)
                            output.write(buffer, 0, n)
                        }
                        output.toByteArray()
                    }
                    Response(status, contentType, connection.getHeaderField("Retry-After"), bytes)
                } finally { connection.disconnect() }
            }
            Thread(task, "room-directory").apply { isDaemon = true; start() }
            try { return task.get(10, TimeUnit.SECONDS) }
            catch (_: InterruptedException) { Thread.currentThread().interrupt(); invalid() }
            catch (_: TimeoutException) { throw DirectoryError(0, "directory_timeout") }
            catch (e: ExecutionException) {
                val cause = e.cause
                // Retry only transport interruptions, never TLS validation or a
                // malformed/untrusted response. No exception text reaches UI.
                if (cause is java.io.IOException && cause !is javax.net.ssl.SSLException)
                    throw DirectoryError(0, "directory_connection")
                invalid()
            }
            catch (_: Exception) { invalid() }
            finally { task.cancel(true); connection.disconnect() }
        }
    }
}

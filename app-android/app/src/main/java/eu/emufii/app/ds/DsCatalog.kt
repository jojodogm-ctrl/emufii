package eu.emufii.app.ds

import android.content.Context
import android.net.Uri
import eu.emufii.dsnemul.NemulRoundRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Runtime architecture, independent of CompatRating and of per-game playability. */
enum class DsTier { GOLD, SILVER }

data class DsRuntimeProfile internal constructor(
    val gameId: String,
    val title: String,
    val productCode: String?,
    val profileId: String,
    val romSha256: String,
    val canonicalRomSha256: String,
    val profileFingerprint: String,
    val runtimeVersion: String,
    val runtimeSha256: String,
    val players: List<Int>,
) {
    val maxPlayers: Int get() = players.max()
}

data class DsGameIdentity internal constructor(
    val sha256: String,
    val profile: DsRuntimeProfile?,
    internal val silverAvailable: Boolean,
) {
    init {
        require(HASH.matches(sha256))
        require(profile == null || profile.romSha256 == sha256)
    }
    val tier: DsTier get() = if (profile != null) DsTier.GOLD else DsTier.SILVER
    val allowedPlayers: List<Int> get() = profile?.players ?: if (silverAvailable) (2..4).toList() else emptyList()
    val maxPlayers: Int get() = allowedPlayers.maxOrNull() ?: 0

    /** The same cartridge without its GOLD profile: the unmodified game, on SILVER. */
    fun original(): DsGameIdentity = if (profile == null) this else DsGameIdentity(sha256, null, silverAvailable)
}

/** Public coordinator descriptor. It contains no endpoint, key, ticket, or invitation URL. */
data class DsSessionSpec internal constructor(
    val mode: DsTier,
    val protocol: String,
    val gameId: String,
    val runtimeVersion: String,
    val romSha256: String,
    val players: Int,
    val instanceCount: Int,
    val profileId: String? = null,
    val profileFingerprint: String? = null,
) {
    val tier: DsTier get() = mode
    val transport: String get() = if (mode == DsTier.GOLD) "ds-hle" else "ds-local"

    /** The value of the coordinator request's `ds` field. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("mode", mode.name.lowercase())
        put("protocol", protocol); put("gameId", gameId); put("runtimeVersion", runtimeVersion)
        put("romSha256", romSha256); put("players", players); put("instanceCount", instanceCount)
        profileId?.let { put("profileId", it) }
        profileFingerprint?.let { put("profileFingerprint", it) }
    }
    fun toRequestJson(): JSONObject = JSONObject().put("transport", transport).put("ds", toJson())

    companion object {
        const val PROTOCOL = "emufii-ds-session-v1"
        private val required = setOf("mode", "protocol", "runtimeVersion", "players", "gameId", "romSha256", "instanceCount")

        /** Structural parser for network responses; local catalog validation is required before launch. */
        fun parse(transport: String, json: JSONObject): DsSessionSpec? = runCatching {
            val mode = when (transport) {
                "ds-hle" -> DsTier.GOLD
                "ds-local" -> DsTier.SILVER
                else -> return null
            }
            val profileFields = if (mode == DsTier.GOLD) setOf("profileId", "profileFingerprint") else emptySet()
            require(json.fields() == required + profileFields)
            require(json.string("mode") == mode.name.lowercase() && json.string("protocol") == PROTOCOL)
            val players = json.integer("players").also { require(it in 2..8) }
            val instances = json.integer("instanceCount")
            require(if (mode == DsTier.GOLD) instances == 1 else players <= 4 && instances == players)
            DsSessionSpec(mode, PROTOCOL, json.identifier("gameId"), json.identifier("runtimeVersion"),
                json.hash("romSha256"), players, instances,
                if (mode == DsTier.GOLD) json.identifier("profileId") else null,
                if (mode == DsTier.GOLD) json.hash("profileFingerprint") else null)
        }.getOrNull()

        fun parse(descriptor: JSONObject): DsSessionSpec? =
            (descriptor.opt("ds") as? JSONObject)?.let { ds ->
                (descriptor.opt("transport") as? String)?.let { parse(it, ds) }
            }
    }
}

object DsCatalog {
    private const val GOLD_RUNTIME = "android-hle-9bd1b92d70e3a7ca"
    private const val GOLD_SHA = "9bd1b92d70e3a7ca39d99d5074ac51847f8ff2852b70ab82d61c4642ba83b5c9"
    @Volatile private var loaded: Registry? = null
    private val hashes = ConcurrentHashMap<FileStamp, String>()
    private const val MAX_ROM_BYTES = 512L * 1024 * 1024

    internal data class Registry(val profiles: Map<String, DsRuntimeProfile>, val silverAvailable: Boolean)
    internal data class FileStamp(val path: String, val fileKey: String, val size: Long, val modifiedNanos: Long)

    /** SHA256 over the actual cartridge bytes. Cancellation propagates to the caller. */
    suspend fun assess(context: Context, uri: Uri): DsGameIdentity = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val catalog = get(context)
        // Arbitrary content providers do not promise reliable inode/change metadata.
        // They are always streamed again; URI text, filename and product code never select GOLD.
        val before = fileStamp(uri)
        val cached = before?.let { hashes[it] }
        val hash = if (cached != null) cached else {
            val result = context.contentResolver.openInputStream(uri)?.use { input ->
                hashStream(input) { currentCoroutineContext().ensureActive() }
            } ?: throw IOException("Impossible de lire la ROM DS.")
            val after = fileStamp(uri)
            if (before != null && before == after) {
                hashes.keys.removeIf { it.path == before.path && it != before }
                hashes[before] = result
            }
            result
        }
        currentCoroutineContext().ensureActive()
        identity(hash, catalog)
    }

    fun choose(identity: DsGameIdentity, players: Int): DsSessionSpec {
        require(players in identity.allowedPlayers) { "Nombre de joueurs incompatible avec ce profil DS." }
        val profile = identity.profile
        return if (profile != null) DsSessionSpec(DsTier.GOLD, DsSessionSpec.PROTOCOL, profile.gameId,
            profile.runtimeVersion, identity.sha256, players, 1, profile.profileId, profile.profileFingerprint)
        else {
            require(identity.silverAvailable)
            DsSessionSpec(DsTier.SILVER, DsSessionSpec.PROTOCOL, "nds-${identity.sha256.take(16)}",
                NemulRoundRequest.RUNTIME_VERSION, identity.sha256, players, players)
        }
    }

    fun validate(context: Context, spec: DsSessionSpec): Boolean = valid(spec, get(context))
    /** Useful after assess() has loaded the immutable packaged catalog. */
    fun validate(spec: DsSessionSpec): Boolean = loaded?.let { valid(spec, it) } ?: false
    fun parseSession(transport: String, json: JSONObject, context: Context): DsSessionSpec? =
        DsSessionSpec.parse(transport, json)?.takeIf { validate(context, it) }
    fun parseSession(descriptor: JSONObject, context: Context): DsSessionSpec? =
        DsSessionSpec.parse(descriptor)?.takeIf { validate(context, it) }

    private fun get(context: Context): Registry = loaded ?: synchronized(this) {
        loaded ?: context.assets.open("ds-runtime-profiles.json").bufferedReader(Charsets.UTF_8).use {
            parseRegistry(JSONObject(it.readText()))
        }.also { loaded = it }
    }

    internal fun identity(sha256: String, registry: Registry): DsGameIdentity {
        require(HASH.matches(sha256))
        return DsGameIdentity(sha256, registry.profiles[sha256], registry.silverAvailable)
    }

    internal fun valid(spec: DsSessionSpec, registry: Registry): Boolean {
        if (DsSessionSpec.parse(spec.transport, spec.toJson()) != spec) return false
        if (spec.mode == DsTier.SILVER) return registry.silverAvailable &&
            spec.runtimeVersion == NemulRoundRequest.RUNTIME_VERSION && spec.players in 2..4 &&
            spec.instanceCount == spec.players
        val p = registry.profiles[spec.romSha256] ?: return false
        return p.gameId == spec.gameId && p.runtimeVersion == spec.runtimeVersion &&
            p.profileId == spec.profileId && p.profileFingerprint == spec.profileFingerprint &&
            spec.players in p.players && spec.instanceCount == 1
    }

    internal fun parseRegistry(json: JSONObject): Registry {
        require(json.fields() == setOf("schema", "protocol", "profiles", "silver"))
        require(json.integer("schema") == 1 && json.string("protocol") == DsSessionSpec.PROTOCOL)
        val entries = json.getJSONArray("profiles")
        val profiles = mutableMapOf<String, DsRuntimeProfile>()
        for (index in 0 until entries.length()) {
            val p = entries.getJSONObject(index)
            val allowed = setOf("gameId", "title", "productCode", "profileId", "pipeline", "romSha256",
                "canonicalRomSha256", "profileFingerprint", "players", "runtimeVersion", "runtimeSha256")
            require(p.fields().all { it in allowed })
            val list = p.getJSONArray("players")
            val counts = (0 until list.length()).map { list.get(it).integerValue() }
            require(counts.isNotEmpty() && counts.all { it in 2..8 } && counts.distinct().size == counts.size)
            require(p.string("runtimeVersion") == GOLD_RUNTIME && p.hash("runtimeSha256") == GOLD_SHA)
            val profile = DsRuntimeProfile(p.identifier("gameId"), p.opt("title") as? String ?: p.identifier("gameId"),
                p.opt("productCode") as? String, p.identifier("profileId"), p.hash("romSha256"),
                p.hash("canonicalRomSha256"), p.hash("profileFingerprint"), p.string("runtimeVersion"),
                p.hash("runtimeSha256"), counts.sorted())
            require(profiles.put(profile.romSha256, profile) == null) { "SHA DS ambigu dans le catalogue." }
        }
        val silver = json.getJSONObject("silver")
        require(silver.fields() == setOf("available", "maxPlayers", "runtimeVersion", "runtimeSha256", "protocol"))
        val available = silver.get("available") as? Boolean ?: error("Invalid SILVER availability")
        require(silver.integer("maxPlayers") == 4 && silver.string("protocol") == DsSessionSpec.PROTOCOL &&
            silver.string("runtimeVersion") == NemulRoundRequest.RUNTIME_VERSION &&
            silver.hash("runtimeSha256") == NemulRoundRequest.ENGINE_SHA256)
        return Registry(profiles.toMap(), available)
    }

    /** Pure reader shared with tests. The check runs before every potentially blocking read. */
    internal suspend fun hashStream(input: InputStream, check: suspend () -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(128 * 1024)
        var total = 0L
        while (true) {
            check()
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_ROM_BYTES) throw IOException("ROM DS trop volumineuse.")
            if (count > 0) digest.update(buffer, 0, count)
        }
        check()
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fileStamp(uri: Uri): FileStamp? = runCatching {
        if (uri.scheme != "file" || !uri.authority.isNullOrEmpty()) return null
        val file = File(uri.path ?: return null)
        localFileStamp(file)
    }.getOrNull()

    internal fun localFileStamp(file: File): FileStamp? = runCatching {
        val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (!attrs.isRegularFile || attrs.isSymbolicLink || attrs.size() < 0 || attrs.size() > MAX_ROM_BYTES) return null
        val key = attrs.fileKey()?.toString() ?: return null
        val modified = attrs.lastModifiedTime().to(TimeUnit.NANOSECONDS)
        if (modified <= 0) return null
        FileStamp(file.canonicalPath, key, attrs.size(), modified)
    }.getOrNull()
}

private val HASH = Regex("[0-9a-f]{64}")
private val ID = Regex("[A-Za-z0-9_.:-]{1,128}")
private fun JSONObject.fields(): Set<String> = keys().asSequence().toSet()
private fun JSONObject.string(key: String): String = (get(key) as? String) ?: error("Invalid $key")
private fun JSONObject.hash(key: String): String = string(key).also { require(HASH.matches(it)) }
private fun JSONObject.identifier(key: String): String = string(key).also { require(ID.matches(it)) }
private fun JSONObject.integer(key: String): Int = get(key).integerValue()
private fun Any.integerValue(): Int {
    require(this is Number)
    val number = toDouble()
    require(number.isFinite() && number >= Int.MIN_VALUE && number <= Int.MAX_VALUE && number == toInt().toDouble())
    return toInt()
}

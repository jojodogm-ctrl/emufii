package eu.emufii.app.ds

import android.content.Context
import eu.emufii.app.library.Console
import eu.emufii.app.library.Rom
import eu.emufii.app.network.CoordinatorClient
import eu.emufii.app.network.CoordinatorError
import eu.emufii.app.network.Member
import eu.emufii.app.network.RemoteSession
import eu.emufii.app.profile.Profile
import eu.emufii.app.session.RomRef
import eu.emufii.app.session.SessionCodes
import eu.emufii.dshle.DsHleRuntime
import eu.emufii.dsnemul.DsNemulRuntime
import eu.emufii.dsnemul.NemulRoundRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class DsSessionView(
    val code: String,
    val title: String,
    val spec: DsSessionSpec,
    val host: Boolean,
    val members: List<Member> = emptyList(),
    val launching: Boolean = false,
    val runtimeActive: Boolean = false,
    val error: String? = null,
)

/** Owns the control-plane lease while either internal game Activity is visible.
 * Native capabilities remain memory-only and are fetched anew at each launch. */
class DsSessionManager private constructor(private val context: Context) {
    private val client = CoordinatorClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Mutex()
    private val state = MutableStateFlow<DsSessionView?>(null)
    val session: StateFlow<DsSessionView?> = state.asStateFlow()
    private var lease: Lease? = null
    private var heartbeat: Job? = null
    private class Lease(val profile: Profile, val identityKey: String, val rom: RomRef?,
        val ownerToken: String?, var memberToken: String)

    init {
        scope.launch { DsHleRuntime.active.collect { updateRuntime() } }
        scope.launch { DsNemulRuntime.active.collect { updateRuntime() } }
    }
    private fun updateRuntime() {
        state.value = state.value?.copy(runtimeActive = DsHleRuntime.active.value || DsNemulRuntime.active.value)
    }

    suspend fun create(rom: Rom, identity: DsGameIdentity, players: Int, private: Boolean, profile: Profile): Result<Unit> = operation {
        lock.withLock {
            checkIdle()
            val identityKey = DsSessionPolicy.identityKey(CoordinatorClient.identityKey)
            val actual = DsCatalog.assess(context, rom.uri)
            require(actual == identity) { "La ROM a changé depuis sa vérification." }
            val spec = DsCatalog.choose(actual, players)
            var last: Throwable? = null
            var created: eu.emufii.app.network.CreatedSession? = null
            var requestedCode: String? = null
            repeat(4) {
                if (created != null) return@repeat
                val candidate = SessionCodes.generate()
                requestedCode = candidate
                val result = client.createSession(candidate, rom.sessionId, rom.displayName,
                    profile.name, profile.id, console = "nds", private = private,
                    emulatorVersion = spec.runtimeVersion, ds = spec)
                created = result.getOrNull(); last = result.exceptionOrNull()
                if (created == null && (last as? CoordinatorError.Http)?.status != 409) throw requireNotNull(last)
            }
            val reply = created ?: throw (last ?: IllegalStateException("Impossible de créer la session DS."))
            try {
                DsSessionPolicy.requireDescriptor(requireNotNull(requestedCode), spec, reply.code, reply.ds)
                val ownerToken = DsSessionPolicy.bearer(reply.token)
                val member = client.heartbeat(reply.code, profile.id, profile.name).getOrThrow()
                val memberToken = DsSessionPolicy.bearer(member.memberToken ?: reply.memberToken)
                require(CoordinatorClient.identityKey == identityKey) { "Le profil a changé pendant la création." }
                lease = Lease(profile, identityKey, RomRef(rom.uri, rom.displayName, rom.console,
                    rom.titleIdHex, rom.filename, rom.productCode, rom.ps2ElfCrc), ownerToken, memberToken)
                state.value = DsSessionView(reply.code, rom.displayName, spec, host = true)
                keepAlive()
            } catch (e: Throwable) {
                withContext(NonCancellable) { client.deleteSession(reply.code, reply.token) }
                throw e
            }
        }
    }

    suspend fun join(remote: RemoteSession, rom: RomRef?, profile: Profile): Result<Unit> = operation {
        lock.withLock {
            checkIdle()
            val identityKey = DsSessionPolicy.identityKey(CoordinatorClient.identityKey)
            val spec = requireNotNull(remote.ds) { "Cette session ne possède pas de moteur DS intégré." }
            DsSessionPolicy.requireDescriptor(remote.code, spec, remote.code, remote.ds)
            require(DsCatalog.validate(context, spec)) { "Profil ou version DS incompatible avec cet APK." }
            if (spec.mode == DsTier.SILVER) {
                require(rom?.console == Console.DS) { "Ajoute la ROM hôte à ta bibliothèque pour rejoindre en argent." }
                require(DsCatalog.assess(context, rom!!.uri).sha256 == spec.romSha256) {
                    "La ROM diffère de celle de l’hôte (région, révision ou patch)."
                }
            }
            val member = client.heartbeat(remote.code, profile.id, profile.name).getOrThrow()
            val token = DsSessionPolicy.bearer(member.memberToken)
            require(CoordinatorClient.identityKey == identityKey) { "Le profil a changé pendant la connexion." }
            lease = Lease(profile, identityKey, rom, null, token)
            state.value = DsSessionView(remote.code, remote.romTitle ?: "Nintendo DS", spec,
                host = false, members = remote.members)
            keepAlive()
        }
    }

    private fun keepAlive() {
        heartbeat?.cancel()
        heartbeat = scope.launch {
            while (true) {
                val current = state.value ?: return@launch
                val credentials = lease ?: return@launch
                if (CoordinatorClient.identityKey != credentials.identityKey) {
                    failLease("Le profil a changé. Quitte cette session avant d’en ouvrir une autre.")
                    return@launch
                }
                val pulse = client.heartbeat(current.code, credentials.profile.id, credentials.profile.name)
                DsSessionPolicy.terminalError(pulse.exceptionOrNull())?.let {
                    failLease(it); return@launch
                }
                pulse.getOrNull()?.memberToken?.let {
                    try { credentials.memberToken = DsSessionPolicy.bearer(it) }
                    catch (_: IllegalArgumentException) {
                        failLease("L’admission de la session DS est invalide."); return@launch
                    }
                }
                client.announcePresence(credentials.profile.id, credentials.profile.name, inSession = true)
                val remote = client.getSession(current.code)
                DsSessionPolicy.terminalError(remote.exceptionOrNull())?.let { failLease(it); return@launch }
                remote.getOrNull()?.let {
                    try { DsSessionPolicy.requireDescriptor(current.code, current.spec, it.code, it.ds) }
                    catch (_: IllegalArgumentException) {
                        failLease("Le protocole de la session a changé.")
                        return@launch
                    }
                    state.value = state.value?.copy(members = it.members)
                }
                delay(10_000)
            }
        }
    }

    suspend fun launchGame(): Result<Unit> = operation {
        lock.withLock {
            val round = requireNotNull(state.value)
            val credentials = requireNotNull(lease)
            require(!round.runtimeActive && !round.launching &&
                !DsHleRuntime.active.value && !DsNemulRuntime.active.value) { "Une partie est déjà active." }
            require(round.error == null) { round.error.orEmpty() }
            require(if (round.spec.mode == DsTier.GOLD) DsHleRuntime.supported else DsNemulRuntime.supported) {
                "Le moteur DS nécessite un appareil ARM64."
            }
            require(CoordinatorClient.identityKey == credentials.identityKey) { "Le profil de cette session a changé." }
            val key = credentials.identityKey
            val memberBearer = credentials.memberToken
            state.value = round.copy(launching = true, error = null)
            try {
                // Re-check the immutable descriptor immediately before a new admission.
                // A missing/malformed DS response must never enter the legacy WG path.
                val remote = client.getSession(round.code).getOrThrow()
                DsSessionPolicy.requireDescriptor(round.code, round.spec, remote.code, remote.ds)
                if (round.spec.mode == DsTier.SILVER) {
                    val rom = requireNotNull(credentials.rom)
                    require(DsCatalog.assess(context, rom.uri).sha256 == round.spec.romSha256) { "La ROM a changé." }
                    val bytes = client.dsRequest("/sessions/${round.code}/ds/admission", "POST",
                        JSONObject().put("id", credentials.profile.id).put("key", key), memberBearer).getOrThrow()
                    val admission = try { DsSilverAdmission.parse(JSONObject(bytes.toString(Charsets.UTF_8)), round.spec.players) }
                        finally { bytes.fill(0) }
                    val request = try {
                        requireCurrentLease(credentials, round)
                        NemulRoundRequest(rom.uri, round.spec.romSha256, admission.room,
                            admission.seat, round.spec.players, admission.inputDelay, admission.address, admission.port,
                            admission.sessionId, admission.capability, nativeName(credentials.profile.name, round.spec))
                    }
                        finally { admission.erase() }
                    check(DsNemulRuntime.open(context, request) == DsNemulRuntime.LaunchResult.Started) {
                        "Impossible d’ouvrir le moteur argent."
                    }
                } else {
                    val service = requireNotNull(remote.dsRuntime?.optJSONObject("service")) { "Le relais HLE est indisponible." }
                    require(DsHleRuntime.acceptsEndpoints(service.getString("directoryUrl"), service.getString("gatewayUrl"))) {
                        "Le relais HLE configuré ne correspond pas à cet APK."
                    }
                    val transport: DsHleSignedTransport = { path, method, body, bearer, max ->
                        client.dsRequest(path, method, body, bearer, max)
                    }
                    if (round.host) {
                        val rom = requireNotNull(credentials.rom)
                        require(DsCatalog.assess(context, rom.uri).sha256 == round.spec.romSha256) { "La ROM a changé." }
                        requireCurrentLease(credentials, round)
                        val access = DsHleHostAccess(context, round.code, requireNotNull(credentials.ownerToken),
                            credentials.profile, key, transport)
                        val request = DsHleRuntime.HostRequest(round.code, nativeName(credentials.profile.name, round.spec),
                            rom.uri, round.spec.gameId, round.spec.players)
                        if (DsHleRuntime.openHost(context, request, access) != DsHleRuntime.LaunchResult.Started) {
                            withContext(NonCancellable + Dispatchers.IO) { runCatching { access.close() } }
                            error("Impossible d’ouvrir le moteur HLE.")
                        }
                    } else {
                        val access = DsHleGuestAccess(context, round.code, memberBearer, credentials.profile, key, transport)
                        try {
                            val admission = withContext(Dispatchers.IO) { access.admission() }
                            require(admission.seat in 2..round.spec.players) { "Siège HLE incompatible avec cette session." }
                            requireCurrentLease(credentials, round)
                            check(DsHleRuntime.openGuest(context, admission.code,
                                nativeName(credentials.profile.name, round.spec), access) == DsHleRuntime.LaunchResult.Started) {
                                "Impossible d’ouvrir le moteur HLE."
                            }
                        } catch (e: Throwable) {
                            access.close()
                            throw e
                        }
                    }
                }
                updateRuntime()
            } finally { state.value = state.value?.copy(launching = false) }
        }
    }

    suspend fun leave(): Result<Unit> = operation {
        lock.withLock {
            val round = state.value ?: return@withLock
            val credentials = requireNotNull(lease)
            stopRuntime()
            // Keep the lease until each native process confirms retirement.
            while (DsHleRuntime.active.value || DsNemulRuntime.active.value) delay(100)
            // Finish the last in-flight pulse before removal; it must not re-add
            // a guest or announce the old session after the final DELETE.
            heartbeat?.cancelAndJoin(); heartbeat = null
            try {
                val released = if (round.host) client.deleteSession(round.code, credentials.ownerToken)
                    else client.leaveSession(round.code, credentials.profile.id, credentials.memberToken)
                val failure = released.exceptionOrNull()
                if (failure != null && failure !is CoordinatorError.NotFound) throw failure
            } catch (e: Throwable) {
                keepAlive()
                throw e
            }
            client.announcePresence(credentials.profile.id, credentials.profile.name, inSession = false)
            lease = null; state.value = null
        }
    }
    private fun failLease(message: String) {
        stopRuntime()
        state.value = state.value?.copy(error = message)
    }
    private fun requireCurrentLease(credentials: Lease, round: DsSessionView) {
        val current = state.value
        require(lease === credentials && current != null && current.code == round.code && current.spec == round.spec &&
            current.error == null && CoordinatorClient.identityKey == credentials.identityKey) {
            "L’admission de la session a été retirée pendant le lancement."
        }
    }
    private fun stopRuntime() { DsHleRuntime.requestClose(); DsNemulRuntime.requestClose() }
    private fun checkIdle() {
        require(state.value == null && !DsHleRuntime.active.value && !DsNemulRuntime.active.value) {
            "Quitte la session DS en cours avant d’en ouvrir une autre."
        }
    }
    private suspend fun <T> operation(block: suspend () -> T): Result<T> = try { Result.success(block()) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    companion object {
        @Volatile private var instance: DsSessionManager? = null
        fun get(context: Context): DsSessionManager = instance ?: synchronized(this) {
            instance ?: DsSessionManager(context.applicationContext).also { instance = it }
        }
        internal fun nativeName(name: String, spec: DsSessionSpec): String {
            val clean = if (spec.mode == DsTier.SILVER) name.filter { it.code in 32..126 }
                else name.filterNot { Character.isISOControl(it) || it.isSurrogate() ||
                    Character.getType(it) == Character.FORMAT.toInt() ||
                    it in '\u2028'..'\u202e' || it in '\u2066'..'\u2069' || it in "\u061c\u200e\u200f" }
            return clean.trim().take(if (spec.gameId == "mpds") 8 else 10).ifBlank { "Joueur" }
        }
    }
}

/** Protocol checks shared by polling and launch so a changed or malformed
 * public descriptor cannot silently select another transport or native layout. */
internal object DsSessionPolicy {
    private val CODE = Regex("[A-Z0-9-]{3,16}")
    private val IDENTITY = Regex("[0-9a-f]{64}")
    fun identityKey(value: String?): String = requireNotNull(value) {
        "L’identité Emufii doit être disponible avant de rejoindre une session DS."
    }.also { require(IDENTITY.matches(it)) { "Identité Emufii invalide." } }

    fun bearer(value: String?): String = requireNotNull(value) {
        "L’admission privée de la session DS est absente."
    }.also { require(it.length in 32..128 && it.all { c -> c.code in 33..126 }) {
        "L’admission privée de la session DS est invalide."
    } }

    fun requireDescriptor(expectedCode: String, expected: DsSessionSpec,
        actualCode: String, actual: DsSessionSpec?) {
        require(CODE.matches(expectedCode) && actualCode == expectedCode && actual == expected &&
            DsSessionSpec.parse(expected.transport, expected.toJson()) == expected) {
            "Le protocole de la session DS a changé."
        }
    }

    fun terminalError(error: Throwable?): String? = when (error) {
        null -> null
        is CoordinatorError.NotFound -> "L’hôte a fermé cette session."
        is CoordinatorError.Unreachable -> null
        is CoordinatorError.Http -> if (error.status in setOf(400, 401, 403))
            "L’admission privée de cette session DS n’est plus valide." else null
        else -> "La réponse de la session DS est invalide."
    }
}

internal class DsSilverAdmission(val room: String, val seat: Int, val inputDelay: Int,
    val address: String, val port: Int, val sessionId: ByteArray, val capability: ByteArray) {
    fun erase() { sessionId.fill(0); capability.fill(0) }
    override fun toString() = "DsSilverAdmission(redacted)"
    companion object {
        fun parse(json: JSONObject, players: Int): DsSilverAdmission {
            require(json.keys().asSequence().toSet() == setOf("protocol", "sessionId", "room", "seat", "capability",
                "relayAddress", "relayPort", "inputDelay"))
            require(json.get("protocol") == "emufii-silver-udp-v1" && players in 2..4)
            fun integer(key: String): Int = requireNotNull(json.get(key) as? Int) { "Admission DS invalide." }
            val room = json.getString("room").also { require(it.matches(Regex("[A-F0-9]{15}"))) }
            val seat = integer("seat").also { require(it in 1..players) }
            val delay = integer("inputDelay").also { require(it == 4) }
            val port = integer("relayPort").also { require(it in 1..65535) }
            val address = json.getString("relayAddress")
            val parts = address.split('.')
            require(parts.size == 4 && parts.all { it.matches(Regex("0|[1-9][0-9]{0,2}")) && it.toInt() in 0..255 } &&
                parts.first().toInt() in 1..223 && address != "255.255.255.255")
            fun hex(key: String, count: Int): ByteArray = json.getString(key).also {
                require(it.matches(Regex("[0-9a-f]{$count}")))
            }.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            return DsSilverAdmission(room, seat, delay, address, port, hex("sessionId", 32), hex("capability", 64))
        }
    }
}

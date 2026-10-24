package eu.emufii.dsnemul

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.security.MessageDigest

/** Embedded SILVER entry point. Admission and ROM ownership stay with Emufii. */
object DsNemulRuntime {
    enum class LaunchResult { Started, AlreadyRunning, InvalidRequest, UnsupportedDevice, Failed }
    private val running = MutableStateFlow(false)
    val activeStateFlow: StateFlow<Boolean> = running.asStateFlow()
    val active: StateFlow<Boolean> = activeStateFlow
    val supported: Boolean get() = "arm64-v8a" in Build.SUPPORTED_ABIS
    private val pending = mutableMapOf<String, NemulRoundRequest>()
    private val consumedAdmissions = mutableSetOf<String>()
    private var lease: String? = null
    private var closer: (() -> Unit)? = null
    private var closeRequested = false
    internal const val EXTRA_HANDLE = "eu.emufii.dsnemul.handle"

    @Synchronized fun open(context: Context, request: NemulRoundRequest): LaunchResult {
        if (!request.valid()) return LaunchResult.InvalidRequest
        if (!supported) return LaunchResult.UnsupportedDevice
        if (running.value) return LaunchResult.AlreadyRunning
        val admission = MessageDigest.getInstance("SHA-256")
            .digest(request.sessionId + request.capability + request.nativeSeat.toByte())
            .joinToString("") { "%02x".format(it) }
        if (admission in consumedAdmissions) return LaunchResult.InvalidRequest
        if (!request.consume()) return LaunchResult.InvalidRequest
        consumedAdmissions += admission
        val handle = UUID.randomUUID().toString()
        pending[handle] = request
        lease = handle
        closer = null
        closeRequested = false
        running.value = true
        return try {
            context.startActivity(Intent(context, NemulGameActivity::class.java).apply {
                putExtra(EXTRA_HANDLE, handle) // No capability or relay address in the Intent.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            LaunchResult.Started
        } catch (_: Exception) {
            pending.remove(handle)?.destroySecrets()
            lease = null
            running.value = false
            LaunchResult.Failed
        }
    }

    @Synchronized internal fun claim(handle: String): NemulRoundRequest? = pending.remove(handle)

    /** The session owner may close a pending or running launch. The Activity
     * retires the service; active stays true until its Binder has really died. */
    @Synchronized fun requestClose(): Boolean {
        if (!running.value) return false
        closeRequested = true
        closer?.invoke()
        return true
    }

    @Synchronized internal fun attachCloser(handle: String, action: () -> Unit) {
        if (lease != handle) return
        closer = action
        if (closeRequested) action()
    }

    /** Matching the launch prevents a late death callback from an old Activity
     * from clearing the next round's lease. */
    @Synchronized internal fun retired(handle: String) {
        if (lease != handle) return
        pending.remove(handle)?.destroySecrets()
        lease = null
        closer = null
        closeRequested = false
        running.value = false
    }
}

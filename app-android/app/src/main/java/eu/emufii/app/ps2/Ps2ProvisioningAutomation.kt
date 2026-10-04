package eu.emufii.app.ps2

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class Ps2ProvisioningPlan(
    val cardName: String,
    val cardSha256: String,
    val sourceCardForSlot2: String? = null,
    val armedAtMs: Long = System.currentTimeMillis(),
)

sealed class Ps2ProvisioningProgress {
    data object Idle : Ps2ProvisioningProgress()
    data object OpeningArmsx2 : Ps2ProvisioningProgress()
    data object OpeningMemoryCards : Ps2ProvisioningProgress()
    data object AssigningSlot2 : Ps2ProvisioningProgress()
    data object AssigningSlot1 : Ps2ProvisioningProgress()
    data class Done(val cardName: String) : Ps2ProvisioningProgress()
    data class Failed(val reason: String) : Ps2ProvisioningProgress()
}

object Ps2ProvisioningAutomation {
    private val _plan = MutableStateFlow<Ps2ProvisioningPlan?>(null)
    val plan: StateFlow<Ps2ProvisioningPlan?> = _plan.asStateFlow()

    private val _progress = MutableStateFlow<Ps2ProvisioningProgress>(Ps2ProvisioningProgress.Idle)
    val progress: StateFlow<Ps2ProvisioningProgress> = _progress.asStateFlow()

    fun arm(plan: Ps2ProvisioningPlan, store: Ps2ProvisioningStore) {
        store.save(plan)
        _plan.value = plan
        _progress.value = Ps2ProvisioningProgress.OpeningArmsx2
    }

    fun restore(store: Ps2ProvisioningStore) {
        if (_plan.value == null) {
            _plan.value = store.load()
            if (_plan.value != null) _progress.value = Ps2ProvisioningProgress.OpeningArmsx2
        }
    }

    fun report(progress: Ps2ProvisioningProgress) {
        _progress.value = progress
    }

    fun complete(context: Context, plan: Ps2ProvisioningPlan, store: Ps2ProvisioningStore) {
        if (!Ps2NetworkProfile.markAssigned(context, plan.cardName, plan.cardSha256)) {
            fail("The verified card no longer matches the preparation under way.", store)
            return
        }
        store.clear()
        _plan.value = null
        _progress.value = Ps2ProvisioningProgress.Done(plan.cardName)
    }

    fun fail(reason: String, store: Ps2ProvisioningStore) {
        store.clear()
        _plan.value = null
        _progress.value = Ps2ProvisioningProgress.Failed(reason)
    }

    fun clear(store: Ps2ProvisioningStore? = null) {
        store?.clear()
        _plan.value = null
        _progress.value = Ps2ProvisioningProgress.Idle
    }

    /** After `install -r` the accessibility service stays bound but gets no events. */
    fun neverStarted(now: Long = System.currentTimeMillis()): Boolean {
        val current = _plan.value ?: return false
        return _progress.value == Ps2ProvisioningProgress.OpeningArmsx2 &&
            now - current.armedAtMs > SILENCE_MS
    }

    private const val SILENCE_MS = 10_000L

    fun expireIfNeeded(store: Ps2ProvisioningStore, now: Long = System.currentTimeMillis()): Boolean {
        val current = _plan.value ?: return false
        if (now >= current.armedAtMs && now - current.armedAtMs <= Ps2ProvisioningStore.TTL_MS) return false
        fail("The ARMSX2 setup expired. Start it again from Emufii.", store)
        return true
    }
}

class Ps2ProvisioningStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(plan: Ps2ProvisioningPlan, now: Long = System.currentTimeMillis()) {
        prefs.edit {
            putString(KEY, JSONObject().apply {
                put("card", plan.cardName)
                put("sha256", plan.cardSha256)
                put("slot2", plan.sourceCardForSlot2)
                put("armed_at", plan.armedAtMs.takeIf { it > 0L } ?: now)
            }.toString())
        }
    }

    fun load(now: Long = System.currentTimeMillis()): Ps2ProvisioningPlan? {
        val raw = prefs.getString(KEY, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val armedAt = json.optLong("armed_at", 0L)
            if (armedAt <= 0L || now < armedAt || now - armedAt > TTL_MS) return null
            Ps2ProvisioningPlan(
                cardName = json.getString("card").takeIf { it.isNotBlank() } ?: return null,
                cardSha256 = json.getString("sha256").takeIf { it.isNotBlank() } ?: return null,
                sourceCardForSlot2 = if (json.isNull("slot2")) null else {
                    json.optString("slot2").takeIf { it.isNotBlank() }
                },
                armedAtMs = armedAt,
            )
        }.getOrNull().also { if (it == null) clear() }
    }

    fun clear() {
        prefs.edit { remove(KEY) }
    }

    companion object {
        private const val PREFS = "ps2_provisioning_plan"
        private const val KEY = "pending"
        internal const val TTL_MS = 10 * 60 * 1000L
    }
}

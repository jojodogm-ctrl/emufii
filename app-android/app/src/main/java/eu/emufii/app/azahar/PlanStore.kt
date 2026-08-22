package eu.emufii.app.azahar

import android.content.Context
import org.json.JSONObject

/**
 * Keeps the armed netplay plan on disk, so it outlives Emufii's process.
 *
 * Why it has to: arming happens in Emufii, and the plan is consumed minutes later
 * inside the emulator. In between, Emufii is in the background while the emulator
 * loads a game, and loading a game is the most memory-hungry thing an Android
 * device does. Eden reached 3.4 GB on the test bench and Android killed Emufii
 * every single time. The plan lived in a process-global object, so it died with
 * the process: the accessibility service came back, found nothing to do, and the
 * player watched a dialog that never filled itself. No error, no log, nothing to
 * report to anyone. That is precisely the failure mode this project refuses.
 *
 * It expires. A plan that outlived its session would be worse than no plan at
 * all, it would type an address into a room the player is setting up for
 * something else entirely.
 */
class PlanStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(plan: NetplayPlan) {
        prefs.edit().putString(KEY_PLAN, PlanCodec.encode(plan)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_PLAN).apply()
    }

    /** The plan, if one was armed recently enough to still mean something. */
    fun load(now: Long = System.currentTimeMillis()): NetplayPlan? {
        val raw = prefs.getString(KEY_PLAN, null) ?: return null
        val plan = PlanCodec.decode(raw, now)
        // Expired or unreadable is the same as absent, and it should not come
        // back on the next launch either.
        if (plan == null) clear()
        return plan
    }

    private companion object {
        const val PREFS = "emufii_netplay_plan"
        const val KEY_PLAN = "plan"
    }
}

/**
 * The stored form of a plan, kept free of Android so its expiry can be tested
 * without a device, the whole point of the store is a rule about time, and a
 * rule about time is exactly what a unit test should be able to wind forward.
 */
object PlanCodec {

    /**
     * Long enough to cover launching a game and finding the multiplayer screen
     * on a slow device; short enough that a forgotten plan cannot surprise
     * anyone later.
     */
    const val TTL_MS = 15 * 60 * 1000L

    fun encode(plan: NetplayPlan, now: Long = System.currentTimeMillis()): String =
        JSONObject().apply {
            put("role", plan.role.name)
            put("ip", plan.ip)
            put("port", plan.port)
            plan.roomName?.let { put("room", it) }
            plan.username?.let { put("user", it) }
            plan.preferredGame?.let { put("game", it) }
            put("armed_at", now)
        }.toString()

    fun decode(raw: String, now: Long = System.currentTimeMillis()): NetplayPlan? = runCatching {
        val json = JSONObject(raw)
        val armedAt = json.optLong("armed_at", 0L)
        // A plan armed in the future is one we can no longer date, the clock
        // moved under us, so it is not trusted either.
        if (armedAt <= 0L || now < armedAt || now - armedAt > TTL_MS) return null
        NetplayPlan(
            role = NetplayPlan.Role.valueOf(json.getString("role")),
            ip = json.getString("ip").ifBlank { return null },
            port = json.getInt("port"),
            roomName = json.optString("room").ifBlank { null },
            username = json.optString("user").ifBlank { null },
            preferredGame = json.optString("game").ifBlank { null }
        )
    }.getOrNull()
}

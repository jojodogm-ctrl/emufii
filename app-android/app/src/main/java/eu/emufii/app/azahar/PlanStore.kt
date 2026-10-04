package eu.emufii.app.azahar

import android.content.Context
import org.json.JSONObject

/** Persisted because Android kills Emufii while the emulator loads; expires so it is never reused. */
class PlanStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(plan: NetplayPlan) {
        prefs.edit().putString(KEY_PLAN, PlanCodec.encode(plan)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_PLAN).apply()
    }

    fun load(now: Long = System.currentTimeMillis()): NetplayPlan? {
        val raw = prefs.getString(KEY_PLAN, null) ?: return null
        val plan = PlanCodec.decode(raw, now)
        if (plan == null) clear()
        return plan
    }

    private companion object {
        const val PREFS = "emufii_netplay_plan"
        const val KEY_PLAN = "plan"
    }
}

object PlanCodec {

    /** Covers launching a game and finding the multiplayer screen on a slow device. */
    const val TTL_MS = 15 * 60 * 1000L

    fun encode(plan: NetplayPlan, now: Long = System.currentTimeMillis()): String =
        JSONObject().apply {
            put("role", plan.role.name)
            put("ip", plan.ip)
            put("port", plan.port)
            plan.roomName?.let { put("room", it) }
            plan.username?.let { put("user", it) }
            if (plan.usernameOverDefaultOnly) put("user_soft", true)
            plan.preferredGame?.let { put("game", it) }
            put("armed_at", now)
        }.toString()

    fun decode(raw: String, now: Long = System.currentTimeMillis()): NetplayPlan? = runCatching {
        val json = JSONObject(raw)
        val armedAt = json.optLong("armed_at", 0L)
        // A plan dated in the future means the clock moved; not trusted.
        if (armedAt <= 0L || now < armedAt || now - armedAt > TTL_MS) return null
        NetplayPlan(
            role = NetplayPlan.Role.valueOf(json.getString("role")),
            ip = json.getString("ip").ifBlank { return null },
            port = json.getInt("port"),
            roomName = json.optString("room").ifBlank { null },
            username = json.optString("user").ifBlank { null },
            usernameOverDefaultOnly = json.optBoolean("user_soft", false),
            preferredGame = json.optString("game").ifBlank { null }
        )
    }.getOrNull()
}

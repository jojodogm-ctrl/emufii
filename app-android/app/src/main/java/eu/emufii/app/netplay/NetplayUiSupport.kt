package eu.emufii.app.netplay

import android.content.Context

object NetplayUiSupport {

    /** Only ids both emulators have: PREFERRED_GAME is Eden-only, MENU_MULTIPLAYER Azahar-only. */
    val PROBE_IDS = listOf(
        NetplayUi.BTN_CREATE,
        NetplayUi.BTN_JOIN,
        NetplayUi.IP_ADDRESS,
        NetplayUi.BTN_CONFIRM
    )

    fun isPresent(context: Context, pkg: String): Boolean {
        val res = runCatching {
            context.packageManager.getResourcesForApplication(pkg)
        }.getOrNull() ?: return false
        return PROBE_IDS.all { name ->
            runCatching { res.getIdentifier(name, "id", pkg) }.getOrDefault(0) != 0
        }
    }
}

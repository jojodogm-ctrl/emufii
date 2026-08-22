package eu.emufii.app.dolphin

import android.content.Context
import android.content.Intent
import eu.emufii.app.azahar.LaunchResult
import eu.emufii.app.azahar.NetplayAutomation
import eu.emufii.app.azahar.NetplayPlan
import eu.emufii.app.azahar.PlanStore

/**
 * Opens Dolphin with the netplay autofill armed.
 *
 * There is no ROM to hand over, and that is not a limitation here. Dolphin
 * cannot be told to boot a particular file from outside, its `AppLinkActivity`
 * takes a filesystem path through `AutoStartFile`, and a SAF `content:` uri is
 * exactly what a path cannot be. Emufii has known that since the tapserver
 * days.
 *
 * Netplay makes the point moot: the game is not chosen at launch, it is chosen
 * in the lobby, by the host, from Dolphin's own library, and every client
 * gets told which one it is. So the flow Emufii needs is the one Dolphin
 * already offers, open the app, land in the room, pick the game there. What
 * the other backends do in two steps, this one does in one, and the ROM the
 * session carries is only ever used to name the game on our own screens.
 *
 * The consequence to keep in mind: both players must already have that game in
 * Dolphin, with matching contents. Netplay verifies it with a hash and says so
 * out loud when they differ.
 */
class DolphinLauncher(private val context: Context) {

    /**
     * The installed Dolphin, if any.
     *
     * Nothing like Eden's matrix to arbitrate: release, beta and dev builds all
     * share one package name and one signing key, so there is at most one to
     * find, and the dev build simply updates the release in place.
     */
    fun installedPackage(): String? = DolphinTarget.packages.firstOrNull { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }

    fun isInstalled(): Boolean = installedPackage() != null

    /**
     * Opens Dolphin on its Netplay Setup screen, with [plan] armed.
     *
     * Arming before the launch, not after: the driver walks from the game grid
     * through the overflow menu, so it has to be ready before the first screen
     * appears. When the automation is off the plan is cleared instead, a stale
     * plan is what once made the service fight the player for a menu.
     */
    fun openForNetplay(plan: NetplayPlan, automationOn: Boolean = true): LaunchResult {
        val pkg = installedPackage() ?: return LaunchResult.NotInstalled
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return LaunchResult.Error("No launch intent for $pkg")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            val store = PlanStore(context)
            if (automationOn) NetplayAutomation.arm(plan, store) else NetplayAutomation.clear(store)
            context.startActivity(intent)
            LaunchResult.Success
        }.getOrElse { LaunchResult.Error(it.message ?: "Unknown launch error") }
    }

    /** Opens Dolphin with nothing armed, for a player who wants to drive it themselves. */
    fun launch(): LaunchResult {
        val pkg = installedPackage() ?: return LaunchResult.NotInstalled
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return LaunchResult.Error("No launch intent for $pkg")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            LaunchResult.Success
        }.getOrElse { LaunchResult.Error(it.message ?: "Unknown launch error") }
    }
}

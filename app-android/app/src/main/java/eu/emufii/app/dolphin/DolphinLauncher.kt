package eu.emufii.app.dolphin

import android.content.Context
import android.content.Intent
import eu.emufii.app.azahar.LaunchResult
import eu.emufii.app.azahar.NetplayAutomation
import eu.emufii.app.azahar.NetplayPlan
import eu.emufii.app.azahar.PlanStore
import eu.emufii.app.library.Console
import eu.emufii.app.library.EmulatorPick

class DolphinLauncher(private val context: Context) {

    // All Dolphin builds share one package, so the GameCube pick covers the Wii.
    fun installedPackage(): String? = EmulatorPick.packageFor(context, Console.GAMECUBE)

    fun isInstalled(): Boolean = installedPackage() != null

    /** Arm before launch; automation off clears the plan so it can't fight the player. */
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

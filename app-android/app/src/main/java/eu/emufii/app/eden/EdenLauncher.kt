package eu.emufii.app.eden

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.emufii.app.azahar.LaunchResult
import eu.emufii.app.netplay.NetplayUiSupport
import eu.emufii.app.azahar.NetplayAutomation
import eu.emufii.app.azahar.NetplayPlan
import eu.emufii.app.azahar.PlanStore
import eu.emufii.app.netplay.NetplayTarget
import eu.emufii.app.library.Console
import eu.emufii.app.library.EmulatorPick

/** Eden still uses yuzu's `org.yuzu.*` package names; `EmulationActivity` takes a SAF uri via ACTION_VIEW. */
class EdenLauncher(private val context: Context) {

    fun installedPackage(): String? = EmulatorPick.packageFor(context, Console.SWITCH)

    fun isInstalled(): Boolean = installedPackage() != null

    fun launchGame(romUri: Uri, plan: NetplayPlan? = null, automationOn: Boolean = false): LaunchResult {
        val pkg = installedPackage() ?: return LaunchResult.NotInstalled
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(romUri, "application/octet-stream")
            component = ComponentName(pkg, ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            val store = PlanStore(context)
            if (plan != null && automationOn) NetplayAutomation.arm(plan, store)
            else NetplayAutomation.clear(store)
            context.startActivity(intent)
            LaunchResult.Success
        }.getOrElse { LaunchResult.Error(it.message ?: "Unknown launch error") }
    }

    fun openForNetplay(plan: NetplayPlan): LaunchResult {
        val pkg = installedPackage() ?: return LaunchResult.NotInstalled
        if (!NetplayUiSupport.isPresent(context, pkg)) {
            return LaunchResult.NoNetplayUi(
                runCatching { context.packageManager.getPackageInfo(pkg, 0).versionName }.getOrNull()
            )
        }
        NetplayAutomation.arm(plan, PlanStore(context))
        return launch()
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

    private companion object {
        const val ACTIVITY = "org.yuzu.yuzu_emu.activities.EmulationActivity"
    }
}

/** `maxByOrNull` keeps the first tie, and the list puts our fork first. */
internal fun pickEden(installed: List<Pair<String, Long>>): String? =
    installed.maxByOrNull { it.second }?.first

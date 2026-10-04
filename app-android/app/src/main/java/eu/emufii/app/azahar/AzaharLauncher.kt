package eu.emufii.app.azahar

import android.content.ComponentName
import android.content.Context
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import eu.emufii.app.netplay.NetplayUiSupport
import eu.emufii.app.library.Console
import eu.emufii.app.library.EmulatorPick

sealed class LaunchResult {
    data object Success : LaunchResult()
    data object NotInstalled : LaunchResult()

    data class NoNetplayUi(val versionName: String?) : LaunchResult()

    data class Error(val message: String) : LaunchResult()
}

class AzaharLauncher(private val context: Context) {

    fun installedPackage(): String? = EmulatorPick.packageFor(context, Console.THREE_DS)

    fun installedVersionName(pkg: String): String? = runCatching {
        context.packageManager.getPackageInfo(pkg, 0).versionName
    }.getOrNull()

    fun hasNetplayUi(): Boolean {
        val pkg = installedPackage() ?: return false
        return NetplayUiSupport.isPresent(context, pkg)
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

    /** Arm the plan only while the service runs, or it fires on a later launch. */
    fun launchGame(romUri: Uri, plan: NetplayPlan? = null): LaunchResult {
        val pkg = installedPackage() ?: return LaunchResult.NotInstalled
        if (plan != null && !NetplayUiSupport.isPresent(context, pkg)) {
            return LaunchResult.NoNetplayUi(installedVersionName(pkg))
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(romUri, "application/octet-stream")
            component = ComponentName(pkg, "org.citra.citra_emu.activities.EmulationActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            val store = PlanStore(context)
            if (plan != null && isNetplayAutomationEnabled()) {
                NetplayAutomation.arm(plan, store)
            } else {
                NetplayAutomation.clear(store)
            }
            context.startActivity(intent)
            LaunchResult.Success
        }.getOrElse { LaunchResult.Error(it.message ?: "Unknown launch error") }
    }

    /** Azahar joins the room from its main menu, before the game boots. */
    fun openForNetplay(plan: NetplayPlan): LaunchResult {
        val pkg = installedPackage() ?: return LaunchResult.NotInstalled
        if (!NetplayUiSupport.isPresent(context, pkg)) {
            return LaunchResult.NoNetplayUi(installedVersionName(pkg))
        }
        NetplayAutomation.arm(plan, PlanStore(context))
        return launch()
    }

    /** Compare ComponentNames: Android stores this setting in long or short form. */
    fun isNetplayAutomationEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val us = ComponentName(context, AzaharNetplayService::class.java)
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        return splitter.any { ComponentName.unflattenFromString(it) == us }
    }

    /** No FLAG_ACTIVITY_NEW_TASK, or Back lands in another app. */
    fun openAccessibilitySettings(): LaunchResult = runCatching {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        LaunchResult.Success
    }.getOrElse { LaunchResult.Error(it.message ?: "Unknown launch error") }
}

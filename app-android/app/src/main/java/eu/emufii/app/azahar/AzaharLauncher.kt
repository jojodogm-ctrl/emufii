package eu.emufii.app.azahar

import android.content.ComponentName
import android.content.Context
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import eu.emufii.app.netplay.NetplayUiSupport

sealed class LaunchResult {
    data object Success : LaunchResult()
    data object NotInstalled : LaunchResult()

    /**
     * Installed, but this build has no multiplayer UI to drive. Distinct from
     * [Error] because the fix is the user's, not ours: update the emulator.
     */
    data class NoNetplayUi(val versionName: String?) : LaunchResult()

    data class Error(val message: String) : LaunchResult()
}

class AzaharLauncher(private val context: Context) {

    fun installedPackage(): String? = AzaharPackage.candidates.firstOrNull { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }

    /** Shown in the "update Azahar" message, so the user can tell what they have. */
    fun installedVersionName(pkg: String): String? = runCatching {
        context.packageManager.getPackageInfo(pkg, 0).versionName
    }.getOrNull()

    /** True if the installed Azahar exposes a netplay dialog Emufii can drive. */
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

    /**
     * Launches the ROM. If [plan] is non-null and the netplay automation is
     * enabled, arms it so the connection address gets filled in once the user
     * opens Azahar's multiplayer menu.
     *
     * The plan is only armed when the service is actually running, arming it
     * otherwise would leave a stale plan that fires on some later launch.
     *
     * A build without a multiplayer UI is refused outright rather than launched
     * with a plan that can never fire; see [NetplayUiSupport].
     */
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

    /**
     * Opens the emulator with the netplay plan armed, without a ROM.
     *
     * The room is joined *before* the game starts: Azahar's own flow is to
     * connect from the main menu, then boot. Bundling both into one button meant
     * the ROM launched into an emulator that had not joined anything, and the
     * player found out from the game rather than from Emufii.
     *
     * The plan is armed here and left armed, the automation fires as soon as
     * the dialog appears, whether the user opened it or the service did.
     */
    fun openForNetplay(plan: NetplayPlan): LaunchResult {
        val pkg = installedPackage() ?: return LaunchResult.NotInstalled
        if (!NetplayUiSupport.isPresent(context, pkg)) {
            return LaunchResult.NoNetplayUi(installedVersionName(pkg))
        }
        NetplayAutomation.arm(plan, PlanStore(context))
        return launch()
    }

    /**
     * True if the user has switched on Emufii's accessibility service.
     *
     * Compared as [ComponentName]s, not as strings, and that is the whole point.
     * Android stores this setting in either of two forms: the long
     * `eu.emufii.app/eu.emufii.app.azahar.AzaharNetplayService`, or the short
     * `eu.emufii.app/.azahar.AzaharNetplayService` that mirrors how the manifest
     * declares the class. `flattenToString` only ever produces the long one, so a
     * string comparison answered "off" for every device holding the short form,
     * whatever the player had actually enabled.
     *
     * The symptom was not a silent one: the session screen concluded the
     * automation was off and offered to switch it on, permanently, next to a
     * service that had been running the whole time. `unflattenFromString` resolves
     * the leading dot against the package, so both forms land on the same
     * component and the question gets answered on identity rather than spelling.
     */
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

    /** Opens Android's accessibility settings so the user can enable it. */
    /**
     * Android's accessibility settings, opened *inside* Emufii's task when we can.
     *
     * `FLAG_ACTIVITY_NEW_TASK` was set unconditionally, which put the settings
     * screen in a task of its own: pressing Back from it landed wherever the
     * system felt like, during a release check, in a completely different
     * emulator app, instead of returning to the onboarding step that had asked
     * for it. The flag is only needed when the caller isn't an Activity, so it
     * is only added then.
     */
    fun openAccessibilitySettings(): LaunchResult = runCatching {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        LaunchResult.Success
    }.getOrElse { LaunchResult.Error(it.message ?: "Unknown launch error") }
}

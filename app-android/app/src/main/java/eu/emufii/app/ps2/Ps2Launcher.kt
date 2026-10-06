package eu.emufii.app.ps2

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.emufii.app.azahar.LaunchResult
import eu.emufii.app.azahar.NetplayAutomation
import eu.emufii.app.azahar.NetplayPlan
import eu.emufii.app.azahar.PlanStore
import eu.emufii.app.session.RomRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import eu.emufii.app.library.Console
import eu.emufii.app.library.EmulatorPick

/** By named component: ARMSX2's VIEW filter declares no MIME type, so a SAF URI never resolves. */
class Ps2Launcher(private val context: Context) {

    /** Not xyz.aethersx2.android: that is the original AetherSX2, without networking. */
    fun installedPackage(): String? = EmulatorPick.packageFor(context, Console.PS2)

    fun isInstalled(): Boolean = installedPackage() != null

    fun openForLocalLink(
        plan: NetplayPlan,
        rom: Uri? = null,
        automationOn: Boolean = true
    ): LaunchResult {
        val pkg = installedPackage() ?: return LaunchResult.NotInstalled
        val intent = if (rom != null) {
            viewIntent(pkg, rom)
        } else {
            // Without CLEAR_TOP an open ARMSX2 resumes wherever it was and the driver gets lost.
            Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(pkg, VIEW_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            val store = PlanStore(context)
            Ps2ProvisioningAutomation.clear(Ps2ProvisioningStore(context))
            if (automationOn) NetplayAutomation.arm(plan, store) else NetplayAutomation.clear(store)
            context.startActivity(intent)
            LaunchResult.Success
        }.getOrElse { LaunchResult.Error(it.message ?: "Unknown launch error") }
    }

    fun openForProvisioning(plan: Ps2ProvisioningPlan): LaunchResult {
        val pkg = installedPackage() ?: return LaunchResult.NotInstalled
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(pkg, VIEW_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            NetplayAutomation.clear(PlanStore(context))
            Ps2ProvisioningAutomation.arm(plan, Ps2ProvisioningStore(context))
            context.startActivity(intent)
            LaunchResult.Success
        }.getOrElse {
            Ps2ProvisioningAutomation.clear(Ps2ProvisioningStore(context))
            LaunchResult.Error(it.message ?: "Unknown launch error")
        }
    }

    /** ARMSX2 reads this per-game file after its global prefs and before DEV9 initialises. */
    suspend fun launchPrivateGame(rom: RomRef, plan: NetplayPlan): LaunchResult =
        launchConfigured(rom) { Ps2GameSettings.apply(context, rom, plan) }

    suspend fun launchOnlineGame(rom: RomRef, dns: String): LaunchResult =
        launchConfigured(rom) { Ps2GameSettings.applyOnline(context, rom, dns) }

    private suspend fun launchConfigured(rom: RomRef, configure: () -> Ps2GameSettings.Outcome): LaunchResult {
        val pkg = installedPackage() ?: return LaunchResult.NotInstalled
        when (val configured = withContext(Dispatchers.IO) { configure() }) {
            is Ps2GameSettings.Outcome.Success -> Unit
            Ps2GameSettings.Outcome.MissingFolderGrant ->
                return LaunchResult.Error("ARMSX2 folder access is missing")
            Ps2GameSettings.Outcome.MissingPreparedCard ->
                return LaunchResult.Error("the prepared PS2 network card is missing")
            Ps2GameSettings.Outcome.UnknownDiscIdentity ->
                return LaunchResult.Error("the PS2 boot ELF CRC is unavailable")
            is Ps2GameSettings.Outcome.WriteFailed ->
                return LaunchResult.Error(configured.detail)
        }
        val intent = viewIntent(pkg, rom.uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return runCatching {
            NetplayAutomation.clear(PlanStore(context))
            Ps2ProvisioningAutomation.clear(Ps2ProvisioningStore(context))
            context.startActivity(intent)
            LaunchResult.Success
        }.getOrElse { LaunchResult.Error(it.message ?: "Unknown launch error") }
    }

    /** The activity behind the manifest's MainActivity alias, what `am start` resolves a file:// to. */
    private fun viewIntent(pkg: String, rom: Uri): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(pkg, VIEW_ACTIVITY)
            data = rom
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
        const val VIEW_ACTIVITY = "com.armsx2.Main"
    }
}

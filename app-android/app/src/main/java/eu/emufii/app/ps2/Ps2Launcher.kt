package eu.emufii.app.ps2

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.emufii.app.azahar.LaunchResult
import eu.emufii.app.azahar.NetplayAutomation
import eu.emufii.app.azahar.NetplayPlan
import eu.emufii.app.azahar.PlanStore

/**
 * Opens ARMSX2 with the Local Link autofill armed.
 *
 * Unlike Dolphin, it can be handed the ROM, but not in the way you would think.
 * Its activity is exported with a `VIEW` filter on the `content` and `file`
 * schemes, with no MIME type at all, and that is the trap: for a `content://`,
 * Android *infers* the type from the provider, and a filter declaring none then
 * matches nothing. A SAF uri can therefore never be resolved by filtering,
 * measured on the Thor on 2026-08-17: the intent went out,
 * `ActivityTaskManager` logged it, and no activity started, even with ARMSX2
 * stopped beforehand.
 *
 * Hence the explicitly named component: an intent that names its target does not
 * go through filtering. That is exactly what `AzaharLauncher` does with
 * `EmulationActivity`, and for the same reason.
 *
 * The setup still has to happen before the game starts: the Network screen is in
 * the app's settings, not in a running game, and the DEV9 adapter initialises
 * when the game boots (`Local Link host ready on port 19072`). A port or a code
 * set afterwards would not be read back.
 */
class Ps2Launcher(private val context: Context) {

    /**
     * The installed ARMSX2, if there is one.
     *
     * `xyz.aethersx2.android` does not count, even when present: that is the
     * original AetherSX2, with no network layer. Both live side by side on the
     * Thor, and that is exactly the kind of neighbourhood that would have us
     * driving the wrong one.
     */
    fun installedPackage(): String? = Ps2Target.packages.firstOrNull { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }

    fun isInstalled(): Boolean = installedPackage() != null

    /**
     * Opens ARMSX2 with [plan] armed, and the ROM if we have one.
     *
     * Arming precedes launching, as everywhere else: the driver sets off from the
     * library and has to be ready before the first screen.
     */
    fun openForLocalLink(
        plan: NetplayPlan,
        rom: Uri? = null,
        automationOn: Boolean = true
    ): LaunchResult {
        val pkg = installedPackage() ?: return LaunchResult.NotInstalled
        val intent = if (rom != null) {
            viewIntent(pkg, rom)
        } else {
            // `CLEAR_TOP`, and the named component. Without it, an already open
            // ARMSX2 comes back to the foreground *where the player left it*, mid
            // game, in another settings tab, and the driver finds itself facing a
            // screen it cannot read. It then gives up silently, which reads as
            // "the automatic setup does not work". Measured on 2026-08-17.
            Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(pkg, VIEW_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            val store = PlanStore(context)
            if (automationOn) NetplayAutomation.arm(plan, store) else NetplayAutomation.clear(store)
            context.startActivity(intent)
            LaunchResult.Success
        }.getOrElse { LaunchResult.Error(it.message ?: "Unknown launch error") }
    }

    /**
     * Starts the ROM, arming nothing.
     *
     * This is the session's second step: the network was set at the first, and
     * re-arming the driver would send it to fill the form in again over a running
     * game. Dolphin has no such screen, it cannot be handed a game from outside,
     * but ARMSX2 can.
     */
    fun launchGame(rom: Uri): LaunchResult {
        val pkg = installedPackage() ?: return LaunchResult.NotInstalled
        val intent = viewIntent(pkg, rom).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return runCatching {
            context.startActivity(intent)
            LaunchResult.Success
        }.getOrElse { LaunchResult.Error(it.message ?: "Unknown launch error") }
    }

    /**
     * The intent that opens a ROM, with a named component.
     *
     * `com.armsx2.Main` is the activity behind the manifest's `MainActivity`
     * alias, it is what `am start` resolves to when resolution works (with a
     * `file://`), and it is therefore what we target for a `content://`, which
     * filtering cannot reach.
     */
    private fun viewIntent(pkg: String, rom: Uri): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(pkg, VIEW_ACTIVITY)
            data = rom
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** Opens ARMSX2 arming nothing, for whoever wants to set it up themselves. */
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

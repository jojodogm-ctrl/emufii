package eu.emufii.app.wfc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.emufii.app.R
import eu.emufii.app.azahar.LaunchResult

/**
 * Launching melonDS, which is the easiest of the three emulators to talk to.
 *
 * Read off the 2.0.1 APK's manifest: `EmulatorActivity` is exported, with an
 * intent-filter on melonDS's own `LAUNCH_ROM` / `LAUNCH_FIRMWARE` actions, and
 * `EmulatorActivity.kt` takes the ROM from `intent.data` as a URI. Since melonDS
 * requests no broad storage permission and reads its library through SAF, a
 * `content://` is the *expected* way in, which is exactly what Emufii's library
 * holds, and exactly what Dolphin's path-based `AutoStartFile` cannot take.
 *
 * melonDS DualS is a rebrand of that same app: its classes are still named
 * `me.magnum.melonds.*`, so [EMULATOR_ACTIVITY] is unchanged, but its actions
 * carry the *applicationId*, `me.magnum.melondualds.LAUNCH_ROM`. Hence
 * [actionLaunchRom], derived from the installed package rather than hardcoded.
 * Verified against the 0.6.1 APK pulled off the device; see
 * `docs/PHASE1_SCOUT_MELONDS_DUALS.md`.
 */
object MelonDsPackage {
    const val MAIN = "me.magnum.melonds"
    const val DEBUG = "me.magnum.melonds.debug"
    const val DUALS = "me.magnum.melondualds"

    val candidates = listOf(MAIN, DEBUG, DUALS)

    const val EMULATOR_ACTIVITY = "me.magnum.melonds.ui.emulator.EmulatorActivity"

    fun actionLaunchRom(pkg: String) = "$pkg.LAUNCH_ROM"

    /** melonDS also looks for the URI under this extra; harmless to send both. */
    const val EXTRA_URI = "uri"
}

class MelonDs(private val context: Context) {

    fun installedPackage(): String? = MelonDsPackage.candidates.firstOrNull { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }

    fun launchGame(romUri: Uri): LaunchResult {
        val pkg = installedPackage() ?: return LaunchResult.NotInstalled
        val intent = Intent(MelonDsPackage.actionLaunchRom(pkg)).apply {
            component = ComponentName(pkg, MelonDsPackage.EMULATOR_ACTIVITY)
            data = romUri
            putExtra(MelonDsPackage.EXTRA_URI, romUri.toString())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(intent)
            LaunchResult.Success
        }.getOrElse { LaunchResult.Error(it.message ?: context.getString(R.string.err_launch)) }
    }
}

package eu.emufii.app.wfc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.emufii.app.R
import eu.emufii.app.azahar.LaunchResult
import eu.emufii.app.library.Console
import eu.emufii.app.library.EmulatorPick

/**
 * Read off the 2.0.1 APK's manifest: `EmulatorActivity` is exported with an
 * intent-filter on `LAUNCH_ROM`/`LAUNCH_FIRMWARE` and takes the ROM from
 * `intent.data`. It reads its library through SAF, so a `content://` is the way in,
 * which Dolphin's path-based `AutoStartFile` cannot take.
 *
 * melonDS DualS is a rebrand: the classes are still `me.magnum.melonds.*`, so
 * [EMULATOR_ACTIVITY] is unchanged, but its actions carry the applicationId, hence
 * [actionLaunchRom] deriving from the installed package.
 * pourquoi : docs/PHASE1_SCOUT_MELONDS_DUALS.md
 */
object MelonDsPackage {
    const val MAIN = "me.magnum.melonds"
    const val DEBUG = "me.magnum.melonds.debug"
    const val DUALS = "me.magnum.melondualds"

    /**
     * WatermelonDS with two-player netplay, installed next to the release. First, so
     * a DS session opens the build that can play it.
     */
    const val DUALS_DEV = "me.magnum.melondualds.dev"

    val candidates = listOf(DUALS_DEV, MAIN, DEBUG, DUALS)

    /** WatermelonDS's netplay listens here on the host. */
    const val NETPLAY_PORT = 8070

    /** Read by WatermelonDS's `EmulatorActivity`: "host" or "guest", and the host's address. */
    const val EXTRA_NETPLAY_ROLE = "eu.emufii.netplay.role"
    const val EXTRA_NETPLAY_ADDRESS = "eu.emufii.netplay.address"
    const val EXTRA_NETPLAY_PLAYERS = "eu.emufii.netplay.players"

    /** Each phone runs one console per player: four is the ceiling. */
    const val MAX_NETPLAY_PLAYERS = 4

    const val EMULATOR_ACTIVITY = "me.magnum.melonds.ui.emulator.EmulatorActivity"

    fun actionLaunchRom(pkg: String) = "$pkg.LAUNCH_ROM"

    /** melonDS also looks for the URI under this extra; harmless to send both. */
    const val EXTRA_URI = "uri"
}

class MelonDs(private val context: Context) {

    fun installedPackage(): String? = EmulatorPick.packageFor(context, Console.DS)

    /**
     * A session: the same launch, plus who hosts. WatermelonDS then waits for the
     * other player on its own and starts once both are in, as long as both opened
     * the same game.
     */
    fun launchNetplay(romUri: Uri, isHost: Boolean, hostAddress: String, players: Int): LaunchResult =
        launchGame(romUri) {
            putExtra(MelonDsPackage.EXTRA_NETPLAY_ROLE, if (isHost) "host" else "guest")
            if (!isHost) putExtra(MelonDsPackage.EXTRA_NETPLAY_ADDRESS, hostAddress)
            // The host starts as soon as this many are in, rather than after a pause
            // with nobody new arriving; left out when unknown.
            if (isHost && players in 2..MelonDsPackage.MAX_NETPLAY_PLAYERS)
                putExtra(MelonDsPackage.EXTRA_NETPLAY_PLAYERS, players)
        }

    fun launchGame(romUri: Uri, extras: Intent.() -> Unit = {}): LaunchResult {
        val pkg = installedPackage() ?: return LaunchResult.NotInstalled
        val intent = Intent(MelonDsPackage.actionLaunchRom(pkg)).apply {
            extras()
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

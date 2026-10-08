package eu.emufii.app.wfc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.emufii.app.R
import eu.emufii.app.azahar.LaunchResult
import eu.emufii.app.library.Console
import eu.emufii.app.library.EmulatorPick

/** melonDS DualS keeps `me.magnum.melonds.*` classes but prefixes its actions with its applicationId. */
object MelonDsPackage {
    const val MAIN = "me.magnum.melonds"
    const val DEBUG = "me.magnum.melonds.debug"
    const val DUALS = "me.magnum.melondualds"

    const val DUALS_DEV = "me.magnum.melondualds.dev"

    // WatermelonDS Emufii Edition: carries the netplay, installed next to the official app.
    const val DUALS_EMUFII = "me.magnum.melondualds.emufii"
    const val EDITION_URL = "https://github.com/jojodogm-ctrl/WatermelonDS/releases/latest"

    val candidates = listOf(DUALS_EMUFII, DUALS_DEV, MAIN, DEBUG, DUALS)

    const val NETPLAY_PORT = 8070

    const val EXTRA_NETPLAY_ROLE = "eu.emufii.netplay.role"
    const val EXTRA_NETPLAY_ADDRESS = "eu.emufii.netplay.address"
    const val EXTRA_NETPLAY_PLAYERS = "eu.emufii.netplay.players"

    const val MAX_NETPLAY_PLAYERS = 4

    const val EMULATOR_ACTIVITY = "me.magnum.melonds.ui.emulator.EmulatorActivity"

    fun actionLaunchRom(pkg: String) = "$pkg.LAUNCH_ROM"

    /** melonDS also reads the URI from this extra. */
    const val EXTRA_URI = "uri"
}

class MelonDs(private val context: Context) {

    fun installedPackage(): String? = EmulatorPick.packageFor(context, Console.DS)

    fun launchNetplay(romUri: Uri, isHost: Boolean, hostAddress: String, players: Int): LaunchResult =
        launchGame(romUri) {
            putExtra(MelonDsPackage.EXTRA_NETPLAY_ROLE, if (isHost) "host" else "guest")
            if (!isHost) putExtra(MelonDsPackage.EXTRA_NETPLAY_ADDRESS, hostAddress)
            if (isHost && players in 2..MelonDsPackage.MAX_NETPLAY_PLAYERS)
                putExtra(MelonDsPackage.EXTRA_NETPLAY_PLAYERS, players)
        }

    fun launchGame(romUri: Uri, extras: Intent.() -> Unit = {}): LaunchResult {
        val pkg = installedPackage() ?: return LaunchResult.NotInstalled
        val intent = Intent(MelonDsPackage.actionLaunchRom(pkg)).apply {
            extras()
            component = ComponentName(pkg, MelonDsPackage.EMULATOR_ACTIVITY)
            // No data: the Edition's filter declares none, and an explicit intent that does not
            // match the target's filter is dropped where the Android 13 rule is enforced (MIUI).
            // The URI travels as the "uri" extra it reads, and as ClipData for the read grant.
            clipData = android.content.ClipData.newRawUri("rom", romUri)
            putExtra(MelonDsPackage.EXTRA_URI, romUri.toString())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(intent)
            LaunchResult.Success
        }.getOrElse {
            // Seen for real: the package answers, its game screen does not (disabled, archived,
            // half installed, or another APK under the same name). Android's own text means nothing to a player.
            if (it is android.content.ActivityNotFoundException) {
                LaunchResult.Error(context.getString(R.string.err_ds_emulator_broken))
            } else {
                LaunchResult.Error(it.message ?: context.getString(R.string.err_launch))
            }
        }
    }
}

package eu.emufii.app.psp

import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.emufii.app.azahar.LaunchResult

/**
 * The address the player sets once inside PPSSPP.
 *
 * It belongs to nobody and never changes: the relay translates it towards the
 * current session's host. That is what replaces the address otherwise retyped
 * every game. Must stay identical to `relay/firewall.js` and to the coordinator.
 */
const val HOST_SENTINEL = "10.66.1.1"

/**
 * Starting a PSP game in PPSSPP.
 *
 * Far shorter than the other launchers, and the scout explains why
 * (`docs/PHASE1_SCOUT_PPSSPP.md`): there is nothing to drive in PPSSPP. It draws
 * its own interface on an opaque surface, the accessibility service sees neither
 * field nor button nor text in it, and its configuration lives in private
 * storage no other application can write.
 *
 * This is not a gap to be filled: it is what decided the entire PSP
 * architecture. Rather than entering the host's address every game, the player
 * sets the sentinel address once and for all, and the relay translates it
 * towards the current session's host. So Emufii has one gesture to make here:
 * open the game.
 *
 * PPSSPP accepts `VIEW` with `content://`, verified against the system on the
 * device, so a SAF uri from the library is enough, with no copy and no storage
 * permission.
 */
class PpssppLauncher(private val context: Context) {

    fun installedPackage(): String? = PpssppPackage.candidates.firstOrNull { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }

    /**
     * Opens PPSSPP on its own screen, with no game.
     *
     * That is what is needed to go and set the network up: the player has to
     * reach the settings, and the settings cannot be reached from a running game.
     * Distinct from [launchGame] for that reason alone, same program, two
     * different moments.
     */
    fun openApp(): LaunchResult {
        val pkg = installedPackage() ?: return LaunchResult.NotInstalled
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return LaunchResult.NotInstalled
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            LaunchResult.Success
        }.getOrElse { LaunchResult.Error(it.message ?: "Unknown launch error") }
    }

    fun launchGame(romUri: Uri): LaunchResult {
        val pkg = installedPackage() ?: return LaunchResult.NotInstalled
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(romUri, "application/octet-stream")
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(intent)
            LaunchResult.Success
        }.getOrElse { LaunchResult.Error(it.message ?: "Unknown launch error") }
    }
}

/**
 * The package names PPSSPP installs itself under.
 *
 * Gold and the free version carry the same code and the same interface; only the
 * identifier changes, and nothing says the player has the one we expected.
 */
object PpssppPackage {
    val candidates = listOf("org.ppsspp.ppsspp", "org.ppsspp.ppssppgold")
}

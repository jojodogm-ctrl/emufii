package eu.emufii.app.nemul

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.emufii.app.R
import eu.emufii.app.azahar.LaunchResult

/** The beta uses its own relay and rooms; it must not launch an Emufii VPN session. */
object NemulPackage {
    const val BETA = "com.mpds.nemul"
    const val ACTIVITY = "com.mpds.online.MainActivity"
    const val EXTRA_PLAYER_NAME = "player_name"
    const val EXTRA_LIVEBOOT = "liveboot"
    const val DOWNLOAD_URL = "https://beta.party.lepast.fr/"
}

class NemulLauncher(private val context: Context) {
    fun isInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(NemulPackage.BETA, 0)
    }.isSuccess

    fun joinRoom(
        roomCode: String,
        playerName: String,
        waitForHostPreparation: Boolean = false,
    ): LaunchResult {
        if (!NemulRoomCode.isValid(roomCode)) {
            return LaunchResult.Error(context.getString(R.string.nemul_room_invalid))
        }
        if (!isInstalled()) return LaunchResult.NotInstalled
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(NemulRoomCode.joinUri(roomCode, waitForHostPreparation))
        ).apply {
            component = ComponentName(NemulPackage.BETA, NemulPackage.ACTIVITY)
            putExtra(NemulPackage.EXTRA_PLAYER_NAME, playerName.trim())
            putExtra(NemulPackage.EXTRA_LIVEBOOT, waitForHostPreparation)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            LaunchResult.Success
        }.getOrElse { LaunchResult.Error(it.message ?: context.getString(R.string.err_launch)) }
    }
}

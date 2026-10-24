package eu.emufii.app.onetap

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri

sealed interface OneTapLaunchResult {
    data object Opened : OneTapLaunchResult
    data object NativeNotInstalled : OneTapLaunchResult
    data object InvalidRoomCode : OneTapLaunchResult
    data object NoBrowser : OneTapLaunchResult
    data object Failed : OneTapLaunchResult
}

/** Opens the actual production client; preparation and gameplay remain owned by it. */
class OneTapLauncher(private val context: Context) {
    fun isNativeInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(OneTapEntry.NATIVE_PACKAGE, 0)
    }.isSuccess

    fun joinNative(code: String): OneTapLaunchResult {
        val normalized = OneTapRoomCode.parse(code) ?: return OneTapLaunchResult.InvalidRoomCode
        if (!isNativeInstalled()) return OneTapLaunchResult.NativeNotInstalled
        return open(Intent(Intent.ACTION_VIEW, Uri.parse(OneTapEntry.publicInvite(normalized))).apply {
            component = ComponentName(OneTapEntry.NATIVE_PACKAGE, OneTapEntry.NATIVE_ACTIVITY)
        })
    }

    fun joinBrowser(code: String): OneTapLaunchResult {
        val normalized = OneTapRoomCode.parse(code) ?: return OneTapLaunchResult.InvalidRoomCode
        return open(Intent(Intent.ACTION_VIEW, Uri.parse(OneTapEntry.browserRoom(normalized))).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        })
    }

    fun openBrowserHost(): OneTapLaunchResult = open(
        Intent(Intent.ACTION_VIEW, Uri.parse(OneTapEntry.BROWSER_HOME)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
    )

    fun createEmufiiHost(configuration: OneTapConfiguration, game: OneTapGame, name: String): OneTapLaunchResult {
        val frontend = configuration.frontendUrl ?: return OneTapLaunchResult.Failed
        val url = Uri.parse(frontend).buildUpon().appendQueryParameter("game", game.wireName)
            .appendQueryParameter("name", name).build()
        return open(Intent(Intent.ACTION_VIEW, url).apply { addCategory(Intent.CATEGORY_BROWSABLE) })
    }

    private fun open(intent: Intent): OneTapLaunchResult = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        OneTapLaunchResult.Opened
    } catch (_: android.content.ActivityNotFoundException) {
        if (intent.component == null) OneTapLaunchResult.NoBrowser else OneTapLaunchResult.Failed
    } catch (_: Exception) {
        OneTapLaunchResult.Failed
    }
}

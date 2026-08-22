package eu.emufii.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import eu.emufii.app.settings.SettingsStore
import eu.emufii.app.ui.EmufiiApp
import eu.emufii.app.ui.theme.EmufiiTheme
import eu.emufii.app.wg.EmufiiWgManager

/**
 * Requests VPN permission before a session starts.
 *
 * [onDenied] is not optional in practice: the user can refuse, and something
 * has to undo the session that was already created on the coordinator. Without
 * it the app sat on its loading screen forever and left an orphan
 * network behind.
 */
fun interface EnsureVpnPermission {
    operator fun invoke(onGranted: () -> Unit, onDenied: () -> Unit)
}

val LocalEnsureVpnPermission =
    compositionLocalOf { EnsureVpnPermission { granted, _ -> granted() } }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val pending = remember { mutableStateOf<Pair<() -> Unit, () -> Unit>?>(null) }
            val vpnLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result: ActivityResult ->
                val cb = pending.value
                pending.value = null
                if (result.resultCode == RESULT_OK) cb?.first?.invoke() else cb?.second?.invoke()
            }
            val ensureVpn = EnsureVpnPermission { onGranted, onDenied ->
                val prep: Intent? = EmufiiWgManager.prepare(this@MainActivity)
                if (prep == null) onGranted()
                else {
                    pending.value = onGranted to onDenied
                    vpnLauncher.launch(prep)
                }
            }

            // Notifications are asked for on the onboarding step that explains
            // them, and nowhere else. This used to fire a system dialog from a
            // LaunchedEffect on first composition, so the very first thing a
            // new install did was interrupt its own welcome screen with a
            // permission prompt for a service that was not running and had not
            // been explained. Asking is the onboarding's job; the button there
            // is what the player pressed to be asked.
            //
            // Foreground services still need it: the tunnel and the Ethernet hub
            // each show a notification, and on Android 13+ that notification is
            // silently dropped without the permission, leaving no sign the
            // tunnel is up and no handle back to Emufii. Refusing stays survivable
            // - everything works, just quietly.

            // Owned here rather than inside EmufiiApp, because the theme wraps the
            // app: two stores would each hold their own StateFlow, and changing
            // the theme from the settings page would update the one the theme
            // isn't reading.
            val settings = remember { SettingsStore.get(this@MainActivity) }
            val theme by settings.theme.collectAsState()
            val accent by settings.accent.collectAsState()
            val dark = theme.isDark(isSystemInDarkTheme())

            // enableEdgeToEdge picks the status bar icon colour once, from the
            // system theme. Choosing Light on a dark phone therefore left white
            // icons on a pale wallpaper, the clock unreadable. The bars have to
            // follow the app's theme, not the phone's.
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).run {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }

            EmufiiTheme(darkTheme = dark, oled = theme.isOled, accent = accent) {
                CompositionLocalProvider(LocalEnsureVpnPermission provides ensureVpn) {
                    EmufiiApp(settings = settings)
                }
            }
        }
    }
}

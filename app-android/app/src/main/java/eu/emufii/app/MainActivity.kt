package eu.emufii.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import eu.emufii.app.notify.AppForeground
import eu.emufii.app.notify.Notifications
import eu.emufii.app.secondscreen.SecondScreenHost
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.emufii.app.settings.SettingsStore
import eu.emufii.app.ui.EmufiiApp
import eu.emufii.app.ui.SplashGate
import eu.emufii.app.ui.theme.EmufiiTheme
import eu.emufii.app.wg.EmufiiWgManager
import eu.emufii.app.ui.Sfx

/**
 * [onDenied] is not optional: a refusal has to undo the session already created on the
 * coordinator, or the app sits on its loading screen with an orphan network behind it.
 */
fun interface EnsureVpnPermission {
    operator fun invoke(onGranted: () -> Unit, onDenied: () -> Unit)
}

val LocalEnsureVpnPermission =
    compositionLocalOf { EnsureVpnPermission { granted, _ -> granted() } }

class MainActivity : ComponentActivity() {

    /** On resume, not on start: a started but unresumed activity is behind the emulator. */
    override fun onResume() {
        super.onResume()
        AppForeground.set(true)
        Notifications.PendingOpen.offer(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Notifications.PendingOpen.offer(intent)
    }

    override fun onPause() {
        super.onPause()
        AppForeground.set(false)
    }

    /** A process Android kept alive skipped the logo on reopening. */
    override fun onStart() {
        super.onStart()
        if (!isChangingConfigurations) SplashGate.rearm()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Decoded before the first screen: loaded lazily, the first hover is silent.
        // pourquoi : docs/decisions/sons.md § Two sounds, one family
        Sfx.prepare(this)
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

            // Not inside EmufiiApp: two stores, two StateFlows, and the settings page
            // updates the one the theme is not reading.
            val settings = remember { SettingsStore.get(this@MainActivity) }
            val theme by settings.theme.collectAsStateWithLifecycle()
            val dark = theme.isDark(isSystemInDarkTheme())

            // enableEdgeToEdge picks the bar icon colour once, from the system theme:
            // Light on a dark phone left white icons on a pale wallpaper.
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).run {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }

            val secondScreen by settings.secondScreen.collectAsStateWithLifecycle()
            SecondScreenHost(enabled = secondScreen)

            EmufiiTheme(darkTheme = dark, oled = theme.isOled) {
                CompositionLocalProvider(LocalEnsureVpnPermission provides ensureVpn) {
                    EmufiiApp(settings = settings)
                }
            }
        }
    }
}

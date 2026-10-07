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

/** [onDenied] must tear down the session already created on the coordinator. */
fun interface EnsureVpnPermission {
    operator fun invoke(onGranted: () -> Unit, onDenied: () -> Unit)
}

val LocalEnsureVpnPermission =
    compositionLocalOf { EnsureVpnPermission { granted, _ -> granted() } }

class MainActivity : ComponentActivity() {

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

    override fun onStart() {
        super.onStart()
        if (!isChangingConfigurations) SplashGate.rearm()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Decoded before the first screen: loaded lazily, the first hover is silent.
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
            val batteryNext = remember { mutableStateOf<(() -> Unit)?>(null) }
            val batteryLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                batteryNext.value?.invoke()
                batteryNext.value = null
            }
            // Realme/Oppo "smart control" froze the tunnel behind Azahar mid-battle (2026-10-07).
            // Asked once, at the first session: the answer goes ahead either way.
            val withBattery: (() -> Unit) -> Unit = { next ->
                val power = getSystemService(android.os.PowerManager::class.java)
                val prefs = getSharedPreferences("battery_ask", MODE_PRIVATE)
                if (power == null || power.isIgnoringBatteryOptimizations(packageName) || prefs.getBoolean("asked", false)) {
                    next()
                } else {
                    prefs.edit().putBoolean("asked", true).apply()
                    batteryNext.value = next
                    runCatching {
                        batteryLauncher.launch(
                            Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                .setData(android.net.Uri.parse("package:$packageName"))
                        )
                    }.onFailure { batteryNext.value = null; next() }
                }
            }
            val ensureVpn = EnsureVpnPermission { onGranted, onDenied ->
                val granted = { withBattery(onGranted) }
                val prep: Intent? = EmufiiWgManager.prepare(this@MainActivity)
                if (prep == null) granted()
                else {
                    pending.value = granted to onDenied
                    vpnLauncher.launch(prep)
                }
            }

            // Hoisted out of EmufiiApp so theme and settings page share one store.
            val settings = remember { SettingsStore.get(this@MainActivity) }
            val theme by settings.theme.collectAsStateWithLifecycle()
            val dark = theme.isDark(isSystemInDarkTheme())

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

package eu.emufii.app.secondscreen

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.app.Presentation
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.emufii.app.notify.AppForeground
import eu.emufii.app.settings.SettingsStore
import eu.emufii.app.ui.theme.EmufiiTheme
import kotlinx.coroutines.delay

@Composable
fun SecondScreenHost(enabled: Boolean) {
    val context = LocalContext.current
    val display by rememberPresentationDisplay()
    val foreground by AppForeground.visible.collectAsStateWithLifecycle()
    val model by SecondScreen.model.collectAsStateWithLifecycle()

    val wanted = secondScreenWanted(enabled, foreground, model)

    // Setting off, app left, or panel gone must take the window down alike.
    DisposableEffect(display, wanted) {
        val target = display
        if (!wanted || target == null) return@DisposableEffect onDispose {}

        val presentation = EmufiiPresentation(context, target)
        // A display can go dark between being listed and being shown.
        val shown = runCatching { presentation.show() }.isSuccess

        onDispose {
            if (shown) runCatching { presentation.dismiss() }
            presentation.release()
        }
    }
}

fun secondScreenWanted(
    enabled: Boolean,
    foreground: Boolean,
    model: SecondScreenModel
): Boolean = enabled && (foreground || model is SecondScreenModel.InSession)

private class EmufiiPresentation(
    context: Context,
    display: Display,
) : Presentation(context, display) {

    private val owner = SecondScreenWindowOwner()

    /** A `Presentation` is a `Dialog`, and a `Dialog` closes on back. */
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Clear FLAG_NOT_TOUCHABLE and FLAG_NOT_FOCUSABLE: the panel is touch.
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        }

        val view = ComposeView(context.withAppLocales()).apply {
            setContent { SecondScreenSurface() }
        }
        owner.attachTo(view)
        setContentView(view)
    }

    fun release() = owner.detach()
}

private fun Context.withAppLocales(): Context {
    val locales = getSystemService(LocaleManager::class.java)
        ?.applicationLocales
        ?.takeIf { !it.isEmpty }
        ?: return this
    val config = Configuration(resources.configuration).apply { setLocales(locales) }
    return createConfigurationContext(config)
}

@Composable
private fun SecondScreenSurface() {
    val context = LocalContext.current
    val settings = remember(context) { SettingsStore.get(context) }
    val theme by settings.theme.collectAsStateWithLifecycle()
    val published by SecondScreen.model.collectAsStateWithLifecycle()
    val aside by SecondScreen.aside.collectAsStateWithLifecycle()

    /** The resting face waits: switching front screens is not atomic. */
    var model by remember { mutableStateOf(published) }
    LaunchedEffect(published, aside) {
        if (published is SecondScreenModel.Idle &&
            model !is SecondScreenModel.Idle &&
            aside == null
        ) {
            delay(IDLE_GRACE_MS)
        }
        model = published
    }

    EmufiiTheme(
        darkTheme = theme.isDark(androidx.compose.foundation.isSystemInDarkTheme()),
        oled = theme.isOled
    ) {
        SecondScreenContent(model)
    }
}

private const val IDLE_GRACE_MS = 400L

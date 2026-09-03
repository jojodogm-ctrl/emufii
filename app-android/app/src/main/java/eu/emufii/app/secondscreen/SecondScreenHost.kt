package eu.emufii.app.secondscreen

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

/**
 * Mounts the second screen while there is a reason to light it, as a [Presentation].
 * pourquoi : docs/decisions/second-ecran.md § The panel only lights up if it has a reason
 */
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

/**
 * pourquoi : docs/decisions/second-ecran.md § The panel only lights up if it has a reason
 */
fun secondScreenWanted(
    enabled: Boolean,
    foreground: Boolean,
    model: SecondScreenModel
): Boolean = enabled && (foreground || model is SecondScreenModel.InSession)

/**
 * Carries its own [SecondScreenWindowOwner]: the service host has no activity to borrow from.
 * pourquoi : docs/decisions/second-ecran.md § The panel's state lives process-wide, not in the composition
 */
private class EmufiiPresentation(
    context: Context,
    display: Display,
) : Presentation(context, display) {

    private val owner = SecondScreenWindowOwner()

    /** A `Presentation` is a `Dialog`, and a `Dialog` closes on back. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The window carried `FLAG_NOT_TOUCHABLE` and `FLAG_NOT_FOCUSABLE`, and the panel
        // is touch.
        // pourquoi : docs/decisions/second-ecran.md § The panel takes the steps, because it is touch
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        }

        // `context` here is not the constructor parameter: a non-`val` parameter is out
        // of scope in a member function.
        // pourquoi : docs/decisions/second-ecran.md § The window: the context is not the one you think
        val view = ComposeView(context.withAppLocales()).apply {
            setContent { SecondScreenSurface() }
        }
        owner.attachTo(view)
        setContentView(view)
    }

    fun release() = owner.detach()
}

/**
 * pourquoi : docs/decisions/second-ecran.md § The language comes from the window, not from the process
 */
private fun Context.withAppLocales(): Context {
    val locales = getSystemService(LocaleManager::class.java)
        ?.applicationLocales
        ?.takeIf { !it.isEmpty }
        ?: return this
    val config = Configuration(resources.configuration).apply { setLocales(locales) }
    return createConfigurationContext(config)
}

/**
 * The theme is read from the store, not inherited: the service host has no enclosing one.
 * pourquoi : docs/decisions/second-ecran.md § The panel's state lives process-wide, not in the composition
 */
@Composable
private fun SecondScreenSurface() {
    val context = LocalContext.current
    val settings = remember(context) { SettingsStore.get(context) }
    val theme by settings.theme.collectAsStateWithLifecycle()
    val published by SecondScreen.model.collectAsStateWithLifecycle()
    val aside by SecondScreen.aside.collectAsStateWithLifecycle()

    /**
     * The resting face waits: switching front screens is not atomic.
     * pourquoi : docs/decisions/second-ecran.md § The resting face waits its turn
     */
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

package eu.emufii.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.emufii.app.R
import eu.emufii.app.ui.theme.socket
import androidx.compose.material3.MaterialTheme
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.wallpaper.TrayBackdrop
import kotlinx.coroutines.delay

/**
 * The opening screen: the logo, for as long as the library takes to load.
 *
 * It is not just decorative. Without it the app opened on an empty grid topped
 * by a loading indicator, the library being scanned at the moment the home
 * screen composes, and a reasonably full ROM folder takes a handful of seconds
 * to read. The app's first screen was therefore its ugliest. Here the scan
 * happens *behind* the logo, and the home screen only appears once filled.
 *
 * Two durations, and they pull in opposite directions:
 *
 * - [MIN_MS] holds the logo on screen even when the cache is already warm. An
 *   animation lasting three frames does not read as an opening but as a flicker;
 *   it is the same flaw already fixed on [PreparingScreen], except that here the
 *   screen is *always* passed through.
 * - [MAX_MS] makes it give way when the scan drags on. A first scan on a large
 *   SD card can run past ten seconds, and holding the player in front of a logo
 *   that long would be worse than letting them watch the library fill up: it has
 *   its own indicator for that.
 *
 * Deliberately non-focusable and control-free: nothing to aim at with a gamepad,
 * hence nothing to signal. The cursor takes its place back on the grid.
 */
@Composable
fun SplashScreen(ready: Boolean, onDone: () -> Unit) {
    val dark = LocalEmufiiDarkTheme.current
    TrayBackdrop(modifier = Modifier.fillMaxSize(), dark = dark)

    // The minimum runs from the first frame, alongside the scan; it is a floor,
    // not a wait that adds on top.
    var floorPassed by remember { mutableStateOf(false) }
    var expired by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MIN_MS)
        floorPassed = true
        delay(MAX_MS - MIN_MS)
        expired = true
    }
    LaunchedEffect(ready, floorPassed, expired) {
        if ((ready && floorPassed) || expired) onDone()
    }

    // The logo arrives from very slightly too small. A fade on its own reads as
    // an image slow to load; the scale, even at 6 %, makes it *enter*.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appearance by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "splash-appearance"
    )

    // The logo is centred on its own, and the bar is hooked onto it.
    //
    // Stacked in a centred column, it was the *pair* that got centred: the bar
    // pushed the logo up by half of what it occupied. Measured on the Thor, the
    // rings landed 30 px above the middle of the screen. Here the bar is
    // positioned relative to the centre without entering the logo's layout
    // calculation, so the logo stays exactly in the middle.
    //
    // The other half of the offset came from the image itself: the PNG carried
    // 113 px of empty space on the left and none on the right, 92 at the top
    // against 142 at the bottom. Cropped to its content, which is what finally
    // makes "centred" mean what is read on screen.
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.emufii_wordmark),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .widthIn(max = LOGO_WIDTH)
                .fillMaxWidth()
                .alpha(appearance)
                .scale(0.94f + 0.06f * appearance)
        )
        SweepBar(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = LOGO_WIDTH * LOGO_ASPECT / 2 + BAR_GAP)
                .alpha(appearance)
        )
    }
}

/**
 * The loading bar: a shuttle travelling back and forth in a gutter.
 *
 * A `CircularProgressIndicator` would have done the job, but it carries
 * Material's primary colour and reads as a control. Here the two tints are the
 * logo's two rings, taken from the image itself; the bar is an extension of the
 * logo, not a widget parked underneath it.
 *
 * Back and forth rather than scrolling: a shuttle that turns around has no join
 * to hide, where a looping band shows its seam at the moment it wraps.
 */
@Composable
private fun SweepBar(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "splash-sweep")
    val travel by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splash-travel"
    )

    val dark = LocalEmufiiDarkTheme.current
    val shape = RoundedCornerShape(BAR_HEIGHT / 2)
    val accent = MaterialTheme.colorScheme.primary

    // A groove with a light running in it, rather than a rectangle with a
    // gradient in it.
    //
    // This bar was the last thing left of the world the app came from: a flat
    // track carrying the logo's violet-to-green gradient, on a tray made of
    // moulded plastic. It is also the very first frame anyone sees, so it was
    // teaching the wrong world before the library had a chance to teach the
    // right one. The groove is the tray's own socket; the light is the cursor's
    // own cyan, which is what says "working" everywhere else in the app.
    Box(modifier = modifier.width(BAR_WIDTH).height(BAR_HEIGHT).socket(shape, dark)) {
        Canvas(modifier = Modifier.matchParentSize().clip(shape)) {
            // The shuttle takes a third of the groove: wide enough to read as a
            // light, narrow enough that it can be seen moving.
            val shuttle = size.width / 3f
            val left = travel * (size.width - shuttle)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, accent, Color.Transparent),
                    startX = left,
                    endX = left + shuttle
                ),
                topLeft = Offset(left, 0f),
                size = Size(shuttle, size.height)
            )
        }
    }
}

/**
 * The logo's two colours, sampled from `emufii_wordmark.png`: the left ring is
 * painted `#7700FA` at 58 % and the right one `#00CA95` at 87 %, on white. The
 * values here are the composited ones, what is actually seen on screen, since
 * the bar is opaque.
 */
private val LogoViolet = Color(0xFF9C6BF5)
private val LogoTeal = Color(0xFF21D1A3)

private val LOGO_WIDTH = 300.dp

/**
 * The logo's height over its width, `431 / 800`, the PNG's dimensions once
 * cropped. It serves to know where the logo ends so the bar can be hooked there,
 * which the layout can no longer say now that the two are not stacked.
 */
private const val LOGO_ASPECT = 431f / 800f

private val BAR_GAP = 44.dp
private val BAR_WIDTH = 168.dp
private val BAR_HEIGHT = 5.dp

/** The floor: below it, the opening reads as a flicker. */
private const val MIN_MS = 1500L

/** The ceiling: past it, the library fills up better in plain sight. */
private const val MAX_MS = 7000L

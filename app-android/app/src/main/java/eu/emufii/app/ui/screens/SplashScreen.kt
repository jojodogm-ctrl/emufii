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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import eu.emufii.app.R
import eu.emufii.app.ui.theme.socket
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.secondscreen.SecondScreen
import eu.emufii.app.secondscreen.SecondScreenModel
import eu.emufii.app.ui.wallpaper.TrayBackdrop
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun SplashScreen(ready: Boolean, onDone: () -> Unit) {
    val dark = LocalEmufiiDarkTheme.current

    DisposableEffect(Unit) {
        val token = SecondScreen.putAside(SecondScreenModel.Idle)
        onDispose { SecondScreen.takeBack(token) }
    }

    TrayBackdrop(modifier = Modifier.fillMaxSize(), dark = dark)

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

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appearance by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "splash-appearance"
    )

    val view = LocalView.current
    DisposableEffect(view) {
        val controller = WindowCompat.getInsetsController(
            (view.context as android.app.Activity).window, view
        )
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.emufii_logo_v3),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .widthIn(max = LOGO_SIZE)
                .fillMaxWidth()
                .alpha(appearance)
                .scale(0.94f + 0.06f * appearance)
        )
        SignalPips(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = LOGO_SIZE * LOGO_ASPECT / 2 + BAR_GAP)
                .alpha(appearance)
        )
    }
}

@Composable
private fun SignalPips(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "splash-pips")
    val travel by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splash-charge"
    )

    val dark = LocalEmufiiDarkTheme.current
    val shape = CircleShape

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PIP_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(PIP_COUNT) { index ->
            val stop = index / (PIP_COUNT - 1).toFloat()
            val tint = lerp(LogoPink, LogoBlue, stop)
            val distance = abs(travel - stop) * (PIP_COUNT - 1)
            val charge = (1f - distance / 1.5f).coerceIn(0f, 1f)
            Pip(tint = tint, charge = charge, shape = shape, dark = dark)
        }
    }
}

@Composable
private fun Pip(tint: Color, charge: Float, shape: Shape, dark: Boolean) {
    Box(
        modifier = Modifier.size(PIP_SIZE).socket(shape, dark),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(color = tint.copy(alpha = 0.16f + 0.14f * charge), radius = radius)
            if (charge > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(tint.copy(alpha = 0.45f * charge), Color.Transparent),
                        center = center,
                        radius = radius * 2.4f
                    ),
                    radius = radius * 2.4f
                )
                drawCircle(color = tint.copy(alpha = 0.84f * charge), radius = radius)
            }
        }
    }
}

private val LogoPink = eu.emufii.app.ui.theme.Coral.bright
private val LogoBlue = eu.emufii.app.ui.theme.Teal.bright

private val LOGO_SIZE = 232.dp

private const val LOGO_ASPECT = 1f

private val BAR_GAP = 25.dp
private val PIP_COUNT = 5
private val PIP_SIZE = 12.dp
private val PIP_GAP = 14.dp

private const val MIN_MS = 4000L

private const val MAX_MS = 12000L

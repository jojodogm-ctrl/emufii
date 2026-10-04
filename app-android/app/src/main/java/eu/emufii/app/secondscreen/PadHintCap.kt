package eu.emufii.app.secondscreen

import androidx.compose.foundation.layout.Spacer
import eu.emufii.app.ui.Motion
import eu.emufii.app.ui.FadeInPlaceBox
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import eu.emufii.app.R
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.PillShape
import eu.emufii.app.ui.theme.plate
import eu.emufii.app.ui.theme.plateColors

@Composable
fun PadHintRow(hint: PadHint, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PadKeyCap(hint)
        Text(
            stringResource(hint.label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PadLegendBar(legend: PadLegend, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendSide(legend.left)
        // A spacer, not SpaceBetween: with the left side gone, the right one stays right.
        Spacer(Modifier.weight(1f))
        LegendSide(legend.right)
    }
}

@Composable
private fun LegendSide(hints: List<PadHint>) {
    var shown by remember { mutableStateOf(hints) }
    if (hints.isNotEmpty()) shown = hints
    val size = Motion.morph<IntSize>()
    AnimatedVisibility(
        visible = hints.isNotEmpty(),
        enter = EnterTransition.None,
        exit = ExitTransition.None
    ) {
        FadeInPlaceBox(transition, LEGEND_IN, LEGEND_OUT) {
            LegendPill {
                AnimatedContent(
                    targetState = shown,
                    transitionSpec = {
                        (EnterTransition.None togetherWith ExitTransition.None)
                            .using(SizeTransform(clip = false) { _, _ -> size })
                    },
                    label = "legend-words"
                ) { words ->
                    FadeInPlaceBox(transition, LEGEND_IN, LEGEND_OUT) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) { words.forEach { PadHintRow(it) } }
                    }
                }
            }
        }
    }
}

private val LEGEND_OUT = tween<Float>(durationMillis = 110, easing = FastOutLinearInEasing)
private val LEGEND_IN = tween<Float>(durationMillis = 170, delayMillis = 110, easing = LinearOutSlowInEasing)

@Composable
private fun LegendPill(content: @Composable () -> Unit) {
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current
    Box(
        modifier = Modifier
            .plate(
                PillShape,
                dark = dark,
                oled = oled,
                lift = 3.dp,
                fill = plateColors(dark, oled && dark).first().copy(alpha = LEGEND_FACE)
            )
            .padding(start = 6.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        contentAlignment = Alignment.CenterStart
    ) { content() }
}

private const val LEGEND_FACE = 0.88f

internal val LEGEND_CAP = 26.dp

@Composable
fun PadKeyCap(hint: PadHint) {
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current
    val tint = MaterialTheme.colorScheme.onSurface
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(LEGEND_CAP)
            .plate(
                PillShape,
                dark = dark,
                oled = oled,
                lift = 2.dp,
                pressed = hint.held
            )
    ) {
        val glyph = hint.shownGlyph(rememberButtonsFlipped())
        if (glyph == null) DPadGlyph(tint) else CapLetter(glyph, tint)
    }
}

@Composable
private fun CapLetter(glyph: String, tint: Color) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val paint = remember(context, tint, density) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = runCatching { ResourcesCompat.getFont(context, R.font.rounded_bold) }
                .getOrNull() ?: android.graphics.Typeface.DEFAULT_BOLD
            textSize = with(density) { 14.sp.toPx() }
            // Pen is placed from the ink bounds; CENTER would offset it twice.
            textAlign = android.graphics.Paint.Align.LEFT
            color = tint.toArgb()
        }
    }
    val bounds = remember(paint, glyph) {
        android.graphics.Rect().also { paint.getTextBounds(glyph, 0, glyph.length, it) }
    }
    Canvas(Modifier.size(26.dp)) {
        drawContext.canvas.nativeCanvas.drawText(
            glyph,
            size.width / 2f - (bounds.left + bounds.right) / 2f,
            size.height / 2f - (bounds.top + bounds.bottom) / 2f,
            paint
        )
    }
}

@Composable
private fun DPadGlyph(tint: Color) {
    Canvas(Modifier.size(10.dp)) {
        val arm = size.width * 0.38f
        val radius = CornerRadius(size.width * 0.06f, size.width * 0.06f)
        drawRoundRect(
            color = tint,
            topLeft = Offset((size.width - arm) / 2f, 0f),
            size = Size(arm, size.height),
            cornerRadius = radius
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, (size.height - arm) / 2f),
            size = Size(size.width, arm),
            cornerRadius = radius
        )
    }
}

@Composable
fun rememberButtonsFlipped(): Boolean {
    val resolver = LocalContext.current.applicationContext.contentResolver
    fun read() = runCatching {
        android.provider.Settings.System.getInt(resolver, FLIP_BUTTON_LAYOUT, 0) == 1
    }.getOrDefault(false)
    val flipped = remember { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : android.database.ContentObserver(
            android.os.Handler(android.os.Looper.getMainLooper())
        ) {
            override fun onChange(selfChange: Boolean) {
                flipped.value = read()
            }
        }
        runCatching {
            resolver.registerContentObserver(
                android.provider.Settings.System.getUriFor(FLIP_BUTTON_LAYOUT), false, observer
            )
        }
        flipped.value = read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return flipped.value
}

private const val FLIP_BUTTON_LAYOUT = "flip_button_layout"

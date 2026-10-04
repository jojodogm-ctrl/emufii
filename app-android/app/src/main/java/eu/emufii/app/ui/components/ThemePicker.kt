package eu.emufii.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import eu.emufii.app.R
import eu.emufii.app.settings.AppTheme
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.theme.AccentCuts
import eu.emufii.app.ui.theme.ArtworkShape
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.EdgeDark
import eu.emufii.app.ui.theme.EdgeLight
import eu.emufii.app.ui.theme.InsetShape
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.PlateDark
import eu.emufii.app.ui.theme.PlateLight
import eu.emufii.app.ui.theme.PlateOled
import eu.emufii.app.ui.theme.ShellDark
import eu.emufii.app.ui.theme.ShellDarkLow
import eu.emufii.app.ui.theme.ShellLight
import eu.emufii.app.ui.theme.ShellLightLow
import eu.emufii.app.ui.theme.ShellOled
import eu.emufii.app.ui.theme.TealCuts
import eu.emufii.app.ui.tap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import eu.emufii.app.ui.rememberElastic
import eu.emufii.app.ui.Motion
import eu.emufii.app.ui.DrawnCheck

@Composable
fun ThemeSwatches(
    theme: AppTheme,
    onTheme: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
    firstIsEntry: Boolean = false
) {
    val cuts = TealCuts
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val count = AppTheme.entries.size
        val cell = (maxWidth - SWATCH_GAP * (count - 1)) / count
        val bar = rememberElastic(
            target = (cell + SWATCH_GAP) * AppTheme.entries.indexOf(theme) + cell * 0.3f,
            width = cell * 0.4f
        )
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP)) {
                AppTheme.entries.forEachIndexed { index, option ->
                    ThemeSwatch(
                        theme = option,
                        accent = cuts,
                        selected = option == theme,
                        onClick = { onTheme(option) },
                        entry = firstIsEntry && index == 0,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .offset { IntOffset(bar.start.roundToPx(), 0) }
                    .size(width = bar.length, height = 4.dp)
                    .clip(CircleShape)
                    .background(cuts.bright)
            )
        }
    }
}

private val SWATCH_GAP = 10.dp

@Composable
private fun ThemeSwatch(
    theme: AppTheme,
    accent: AccentCuts,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    entry: Boolean = false
) {
    val panelDark = LocalEmufiiDarkTheme.current
    var ringed by remember { mutableStateOf(false) }
    val mark by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = Motion.morph(),
        label = "theme-swatch-mark"
    )
    Column(
        // A Row draws in order, so the right thumbnail covered the left one's ring.
        modifier = modifier.zIndex(if (ringed) 1f else 0f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.3f)
                .then(if (entry) Modifier.padEntry() else Modifier)
                .onFocusEvent { ringed = it.hasFocus }
                .controlRing(
                    InsetShape,
                    width = 3.dp,
                    glowRadius = 18.dp,
                    bandFraction = 0.042f
                )
                .clip(InsetShape)
                .tap(onClick = onClick)
        ) {
            when (theme) {
                AppTheme.SYSTEM -> {
                    Row(Modifier.fillMaxSize()) {
                        TrayHalf(
                            AppTheme.LIGHT, accent, Plates.CURSOR,
                            Modifier.weight(1f).fillMaxHeight()
                        )
                        TrayHalf(
                            AppTheme.DARK, accent, Plates.PLAIN,
                            Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
                else -> TrayHalf(theme, accent, Plates.BOTH, Modifier.fillMaxSize())
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .border(
                        width = 1.dp,
                        // Follows the panel, not the depicted theme, or swatches lose their edge.
                        color = if (panelDark) EdgeDark else EdgeLight,
                        shape = InsetShape
                    )
            )
            DrawnCheck(
                done = selected,
                disc = accent.bright,
                ink = accent.ink,
                size = 20.dp,
                sound = false,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )
        }
        Text(
            stringResource(theme.labelShortRes),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

private enum class Plates { BOTH, CURSOR, PLAIN }

@Composable
private fun TrayHalf(
    theme: AppTheme,
    accent: AccentCuts,
    plates: Plates,
    modifier: Modifier = Modifier
) {
    val shell = when (theme) {
        AppTheme.OLED -> listOf(ShellOled, ShellOled)
        AppTheme.DARK -> listOf(ShellDark, ShellDarkLow)
        else -> listOf(ShellLight, ShellLightLow)
    }
    val plate = when (theme) {
        AppTheme.OLED -> PlateOled
        AppTheme.DARK -> PlateDark
        else -> PlateLight
    }
    val dark = theme == AppTheme.DARK || theme == AppTheme.OLED
    Box(
        modifier = modifier.background(Brush.verticalGradient(shell)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (plates != Plates.PLAIN) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(ArtworkShape)
                        .background(plate)
                        .border(1.dp, accent.bright, ArtworkShape)
                )
            }
            if (plates != Plates.CURSOR) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(ArtworkShape)
                        .background(plate)
                        .border(1.dp, if (dark) EdgeDark else EdgeLight, ArtworkShape)
                )
            }
        }
    }
}

internal val AppTheme.labelRes: Int
    get() = when (this) {
        AppTheme.SYSTEM -> R.string.settings_theme_system
        AppTheme.LIGHT -> R.string.settings_theme_light
        AppTheme.DARK -> R.string.settings_theme_dark
        AppTheme.OLED -> R.string.settings_theme_oled
    }

internal val AppTheme.labelShortRes: Int
    get() = when (this) {
        AppTheme.SYSTEM -> R.string.settings_theme_short_system
        AppTheme.LIGHT -> R.string.settings_theme_short_light
        AppTheme.DARK -> R.string.settings_theme_short_dark
        AppTheme.OLED -> R.string.settings_theme_short_oled
    }


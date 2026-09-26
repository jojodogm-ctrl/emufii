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

/**
 * Four trays to compare, in place of nine named lines the settings had to scroll past.
 * pourquoi : docs/decisions/reglages-ecran.md § One hub and seven pages, plus an accordion
 */
@Composable
fun ThemeSwatches(
    theme: AppTheme,
    onTheme: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
    /** True when the first tray is the page's first control. */
    firstIsEntry: Boolean = false
) {
    // Hardcoded: there is no configurable accent any more.
    val cuts = TealCuts
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val count = AppTheme.entries.size
        val cell = (maxWidth - SWATCH_GAP * (count - 1)) / count
        // Under the names, a bar that stretches from one theme to the next and closes up.
        // pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § The elastic indicator
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
    /**
     * The ring belongs to the cursor and keeps one weight everywhere; read here as well
     * as in [controlRing] because only layout can pass the thumbnail in front of its
     * neighbours before the band is drawn.
     * pourquoi : docs/decisions/navigation-manette.md § The ring keeps the same weight everywhere
     */
    var ringed by remember { mutableStateOf(false) }
    val mark by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = Motion.morph(),
        label = "theme-swatch-mark"
    )
    Column(
        // The cursor's band spills past the thumbnail and a `Row` draws its children in
        // order, so the right thumbnail passed over the left one's band and cut it off.
        modifier = modifier.zIndex(if (ringed) 1f else 0f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.3f)
                .then(if (entry) Modifier.padEntry() else Modifier)
                .onFocusEvent { ringed = it.hasFocus }
                // Thinner than elsewhere: the thumbnails are 10 dp apart and carry their
                // name just below, so the default share bit into both.
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
                    // One plate per side: drawing both halves complete gave four plates
                    // in a thumbnail, read as two thumbnails pushed together. And a
                    // weight only settles the width, so without `fillMaxHeight` the
                    // halves wrap their content and the swatch is a 16 dp strip.
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
            // Over the whole swatch, after the halves, so the split theme gets one
            // outline instead of two touching rectangles.
            Box(
                Modifier
                    .fillMaxSize()
                    .border(
                        width = 1.dp,
                        // Follows the panel, not the theme depicted: `EdgeLight` is a
                        // dark hairline, and on the dark panel the unselected swatches
                        // lost their bounds and the dark one became a hole.
                        color = if (panelDark) EdgeDark else EdgeLight,
                        shape = InsetShape
                    )
            )
            // It grows as it settles: choosing a theme repaints the whole panel behind
            // it, and a mark appearing at once would read as a second event.
            // The disc pops and the tick draws itself; silent, a theme is not a step done.
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
            // 12 dp, not 6: the cursor's band runs below the thumbnail and the name
            // read through it.
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

/** Which of the two plates a half draws; see [AppTheme.SYSTEM] above. */
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
        // The smallest arrangement still reading as this app, not a colour sample.
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

/** For the swatches, where "OLED (true black)" is three lines wide. */
internal val AppTheme.labelShortRes: Int
    get() = when (this) {
        AppTheme.SYSTEM -> R.string.settings_theme_short_system
        AppTheme.LIGHT -> R.string.settings_theme_short_light
        AppTheme.DARK -> R.string.settings_theme_short_dark
        AppTheme.OLED -> R.string.settings_theme_short_oled
    }


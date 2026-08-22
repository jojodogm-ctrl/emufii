package eu.emufii.app.ui.wallpaper

import android.provider.Settings
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import eu.emufii.app.ui.theme.ShellDark
import eu.emufii.app.ui.theme.ShellDarkLow
import eu.emufii.app.ui.theme.ShellLight
import eu.emufii.app.ui.theme.ShellLightLow
import eu.emufii.app.ui.theme.ShellOled
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.engravedGrid

/**
 * The tray the plates sit on. See `theme/Direction.kt`.
 *
 * A console's home menu never floats its icons over a gradient: they sit on a
 * surface, and the surface is *engraved* — a fine repeating rule that gives the
 * eye a scale reference, so a tile reads as an object of a certain size rather
 * than a shape on a plane. That grid is the whole substance of this drawing.
 *
 * What replaced: six blurred blobs rising on a loop, taken from the Wii U menu.
 * They were the glass world's idea of depth and they made a phone wallpaper out
 * of a console shell. The one thing kept from them is the motion budget: a
 * single wide sheen crossing the tray, thirty frames a second at most, because
 * that background once repainted at 120 Hz for half the processor with nothing
 * happening on screen.
 *
 * Almost colourless, and that stays a choice: with cover art on top, two
 * palettes fight and the tray stops reading. The rule "the colour comes from the
 * content, not from the chrome" holds here more than anywhere.
 */
@Composable
fun TrayBackdrop(
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    // Read from the theme rather than passed in: the six callers already pass
    // `dark`, and making them all carry a second flag that none of them computes
    // itself would only have added chances to forget one.
    oled: Boolean = LocalEmufiiOledTheme.current
) {
    val time = rememberElapsedCycles()
    val step = with(LocalDensity.current) { GRID_STEP.toPx() }

    Canvas(modifier = modifier) {
        // On OLED the gradient disappears: two different blacks would light half
        // the screen back up for a shade nobody sees, and a gradient towards
        // black bands visibly on those panels. Flat black, and the engraving
        // carries the whole surface.
        val top = if (oled) ShellOled else if (dark) ShellDark else ShellLight
        val bottom = if (oled) ShellOled else if (dark) ShellDarkLow else ShellLightLow

        drawRect(brush = Brush.verticalGradient(listOf(top, bottom)))

        // The engraving: a dark rule and a lit one a pixel apart, which is what
        // a groove in a moulded surface actually looks like. On OLED the dark
        // half is dropped — there is nothing left to darken — and the lit half
        // is halved again, since a grid of white pixels over a black panel is
        // the one pattern those screens make glow.
        engravedGrid(
            step = step,
            line = when {
                oled -> Color.Transparent
                dark -> Color(0x14000000)
                else -> Color(0x0F16233A)
            },
            highlight = when {
                oled -> Color(0x0AFFFFFF)
                dark -> Color(0x0BFFFFFF)
                else -> Color(0x99FFFFFF)
            }
        )

        // The sheen: one wide band of light crossing the tray on a slow diagonal,
        // the way a lamp travels over plastic when the console is tilted. It is
        // the only thing moving in the app when nothing is happening, and it is
        // deliberately too wide to have an edge — a band you can see the sides of
        // is a stripe, and a stripe is a pattern.
        val travel = (time.mod(1.0)).toFloat()
        val reach = size.width * 2.2f
        val x = -reach * 0.5f + travel * (size.width + reach)
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.5f to when {
                        oled -> Color(0x08FFFFFF)
                        dark -> Color(0x0DFFFFFF)
                        else -> Color(0x40FFFFFF)
                    },
                    1f to Color.Transparent
                ),
                start = Offset(x, 0f),
                end = Offset(x + reach * 0.5f, size.height)
            )
        )

        // The vignette seats the tray in its shell: without it the engraving runs
        // off all four sides and the screen reads as a swatch rather than a
        // surface with edges.
        if (!oled) {
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = if (dark) 0.32f else 0.14f)
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.42f),
                    radius = maxOf(size.width, size.height) * 0.78f
                ),
                size = Size(size.width, size.height)
            )
        }
    }
}

/**
 * The engraving's pitch.
 *
 * Read at arm's length on a handheld: below 32 dp the rules merge into a haze
 * and the tray looks dirty, above 64 they read as a table and start competing
 * with the grid of tiles laid on top. 44 is a texture you notice only once.
 */
private val GRID_STEP = androidx.compose.ui.unit.Dp(44f)

/**
 * How long one crossing of the sheen takes, at speed 1.
 */
private const val CYCLE_MS = 19_000

/**
 * The minimum gap between two repaints: 30 frames per second.
 *
 * Chosen on what the background shows, not on what the screen can do. A sheen
 * this wide advances a third of a pixel between two frames at 120 Hz; the frame
 * rate bought no smoothness, only heat.
 */
private const val FRAME_INTERVAL_NS = 1_000_000_000L / 30

/**
 * The background's time, in crossings elapsed since opening, and which never
 * goes back down.
 *
 * A counter that only ever rises, rather than a 0..1 loop: on the loop, the
 * moment the clock fell back to zero, anything deriving its position from it at
 * a non-integer speed jumped backwards, once per turn. Counted in `Double`,
 * because in `Float` the precision degrades after a few hours on screen and the
 * movement would start advancing in steps.
 *
 * Motionless if the system has turned animations off
 * (`ANIMATOR_DURATION_SCALE` at zero): that setting exists for people bothered
 * by movement, and a permanently animated background is exactly what it points
 * at. It is also the setting taken by those sparing their battery on a handheld,
 * and there the tray keeps a frozen composition that stays presentable, not a
 * blank screen.
 */
@Composable
private fun rememberElapsedCycles(): Double {
    val context = LocalContext.current
    val animated = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) != 0f
    }
    if (!animated) return 0.42

    val cycles = remember { mutableStateOf(0.0) }
    LaunchedEffect(Unit) {
        // The origin is taken at the first frame rather than at the device's boot
        // clock: that one runs to thousands of billions of nanoseconds, and
        // subtracting it up front avoids handling numbers whose useful part is
        // drowned.
        var origin = 0L
        var lastDrawn = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (origin == 0L) origin = now
                if (now - lastDrawn >= FRAME_INTERVAL_NS) {
                    lastDrawn = now
                    cycles.value = (now - origin) / 1_000_000.0 / CYCLE_MS
                }
            }
        }
    }
    return cycles.value
}

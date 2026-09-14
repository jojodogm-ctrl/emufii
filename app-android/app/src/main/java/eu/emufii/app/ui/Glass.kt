package eu.emufii.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/**
 * The pane a control stands on, for it to refract. A surface exports its own rendering
 * rather than handing down the one it reads itself: a button reading the wallpaper
 * directly would show it undeflected, next to a surface that deflects it, and the two
 * would disagree across one sheet of glass. Null where there is no pane, and every
 * caller then falls back to moulded plastic.
 * pourquoi : docs/decisions/bibliotheque.md § The header is a pebble, and the game colours it
 */
val LocalGlassPane = compositionLocalOf<Backdrop?> { null }

/**
 * One lens, two sizes. [thickness] scales the refraction with the object: the same
 * figures on a 46 dp disc as on a full-width header read as a much heavier glass, the
 * bend being a share of the shape and not of the screen.
 */
@Composable
fun Modifier.glass(
    backdrop: Backdrop,
    shape: Shape,
    dark: Boolean,
    thickness: Float = 1f,
    lift: Dp = 12.dp,
    // Lighter on the light theme: there the ground is already pale, so a white veil adds
    // opacity without adding legibility. It only has to lift the glass off the page.
    tint: Float = if (dark) 0.03f else 0.09f,
    /** Handed to the controls this pane carries, so they refract it and not what it reads. */
    exported: LayerBackdrop? = null
): Modifier = this.drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    exportedBackdrop = exported,
    effects = {
        // Before the lens, and this is the whole of the legibility fix. Contrast, not
        // brightness: it pulls both ends towards the middle, so a bright cover calms down
        // and the bare tray underneath lifts instead of going darker. Dimming everything
        // by a fixed amount read as an opaque panel wherever there was nothing to dim,
        // which is most of the screen.
        //
        // The light theme keeps far less of the colour passing through. Dark ink on a pale
        // pane has no depth to hide a tinted cover behind, so the same saturation that
        // reads as a lit edge at night reads as a stain by day: the glass goes nearly
        // colourless there, and leans on contrast instead.
        colorControls(
            contrast = if (dark) 0.72f else 0.80f,
            saturation = if (dark) 0.62f else 0.30f
        )
        // Barely any: blur is what a lens is not.
        blur(2f.dp.toPx())
        lens(
            refractionHeight = 20f.dp.toPx() * thickness,
            refractionAmount = 44f.dp.toPx() * thickness,
            depthEffect = true,
            chromaticAberration = true
        )
    },
    highlight = { Highlight() },
    shadow = { Shadow(lift) },
    onDrawSurface = { drawRect(Color.White.copy(alpha = tint)) }
)

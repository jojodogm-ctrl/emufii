package eu.emufii.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.liftShadow
import eu.emufii.app.ui.theme.plateColors

/**
 * The pane a surface exports for what stands on it. Null where there is no pane.
 * pourquoi : docs/decisions/bibliotheque.md § The header is a pebble, and the game colours it
 */
val LocalGlassPane = compositionLocalOf<Backdrop?> { null }

/**
 * Frosted, not refracting: what passes underneath is blurred behind the plate's own face
 * at 82%, under the same drop shadow as a card. The lens it replaces split the covers into
 * rainbow fringes right where the header's text sits.
 * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Frosted header
 */
@Composable
fun Modifier.glass(
    backdrop: Backdrop,
    shape: Shape,
    dark: Boolean,
    lift: Dp = 6.dp,
    /** Handed to what this pane carries. */
    exported: LayerBackdrop? = null
): Modifier {
    val oled = LocalEmufiiOledTheme.current && dark
    val face = plateColors(dark, oled).first().copy(alpha = FROST_FACE)
    return this
        .liftShadow(shape, lift, dark, oled)
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            exportedBackdrop = exported,
            effects = { blur(FROST_BLUR.toPx()) },
            highlight = null,
            shadow = null,
            onDrawSurface = { drawRect(face) }
        )
}

private const val FROST_FACE = 0.82f

private val FROST_BLUR = 10.dp

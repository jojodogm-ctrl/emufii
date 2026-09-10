package eu.emufii.app.ui

import android.net.Uri
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The layout that matches a cover in the grid with the same cover on the launch card, so
 * the artwork travels instead of one fading out while the other fades in. Null wherever
 * no [androidx.compose.animation.SharedTransitionLayout] is above: everything reading it
 * falls back to drawing itself, plainly.
 * pourquoi : docs/decisions/bibliotheque.md § The veils, and why the launch card is where it is
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedMotion = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * Which game the launch card is holding, or null. The tile reads it to know that its own
 * cover is currently being worn by the card and that it must not draw it twice.
 */
val LocalCardRom = compositionLocalOf<Uri?> { null }

/**
 * The cover of one ROM, wherever it is drawn. Keyed on the ROM's uri and never on a
 * position in a list: a key built from an index matches the wrong tile the moment the
 * library is sorted differently.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCover(rom: Uri, mine: Boolean): Modifier {
    val scope = LocalSharedMotion.current ?: return this
    // `WithCallerManagedVisibility`, not the AnimatedVisibility flavour: the grid is not
    // inside an AnimatedVisibility and wrapping forty tiles in one to gain a transition
    // is the cost this screen has already refused once.
    // pourquoi : docs/decisions/performance-rendu.md § One clock for everything that moves continuously
    return with(scope) {
        this@sharedCover.sharedElementWithCallerManagedVisibility(
            rememberSharedContentState(key = "cover:$rom"),
            visible = mine,
            boundsTransform = CoverFlight
        )
    }
}

/**
 * Critically damped, and said out loud rather than left to the default: any bounce here
 * is a cover that reaches its cell, passes it, and comes back -- read as the icon placing
 * itself a second time. It has one job, which is to arrive.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
private val CoverFlight = BoundsTransform { _, _ ->
    spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = Rect.VisibilityThreshold
    )
}

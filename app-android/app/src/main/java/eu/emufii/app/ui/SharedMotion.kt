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

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedMotion = compositionLocalOf<SharedTransitionScope?> { null }

/** The card's ROM: the tile must not draw that cover twice. */
val LocalCardRom = compositionLocalOf<Uri?> { null }

val LocalLayoutShown = compositionLocalOf { true }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCover(rom: Uri, mine: Boolean): Modifier {
    val scope = LocalSharedMotion.current ?: return this
    val shown = LocalLayoutShown.current
    return with(scope) {
        this@sharedCover.sharedElementWithCallerManagedVisibility(
            rememberSharedContentState(key = "cover:$rom"),
            visible = mine && shown,
            boundsTransform = CoverFlight
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
private val CoverFlight = BoundsTransform { _, _ ->
    spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = Rect.VisibilityThreshold
    )
}

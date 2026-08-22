package eu.emufii.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * One corner language: the radius of a moulded corner, at four sizes.
 *
 * A sampled superellipse was tried here and removed — at tile size, 128 segments
 * left a visible facet in every corner, which read as dirty rather than soft.
 * Plain radii, then, generous and consistent, which is what injection-moulded
 * plastic actually gives you.
 *
 * Slightly tighter than the values the glass world used: plastic has an edge,
 * and a 28 dp radius on a panel makes it a pill-shaped blob rather than a plate.
 */
val TileShape = RoundedCornerShape(16.dp)

/** The artwork inside a tile, tucked just inside the contour. */
val ArtworkShape = RoundedCornerShape(13.dp)

/**
 * The panel radius, named rather than written twice.
 *
 * The rows at the two ends of a settings card inherit the card's corner, since
 * the cursor traces the outline of the space a row actually occupies. That
 * number lived a second time over there, and when the plastic world took the
 * panel from 28 dp down to 22 the copy stayed behind: the ring rounded off
 * wider than the card it sat in, and overshot its corner on the first and last
 * row of every block. One constant, read from both places, is what stops that
 * happening again.
 */
val CardCorner = 22.dp

/** The panels screens are built out of. */
val CardShape = RoundedCornerShape(CardCorner)

/** The status strip and the small inset "screens" it is made of. */
val InsetShape = RoundedCornerShape(14.dp)

val PillShape = RoundedCornerShape(50)

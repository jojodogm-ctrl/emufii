package eu.emufii.app.ui.screens.library

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Portrait keeps three big tiles; landscape follows the width instead.
 * pourquoi : docs/decisions/bibliotheque.md § Whole rows, or nothing
 */
internal const val GRID_COLS_PORTRAIT = 3
internal const val TILE_MIN_WIDTH_DP = 104
internal const val MIN_ROWS = 4
internal const val EXTRA_ROWS_AFTER = 1

/** Two lines of `labelMedium`; the grid needs it before laying out. */
internal val TILE_TITLE_ROOM = 32.dp

/**
 * The grid takes it as a floor on its slack, the list adds it outright; its value is
 * the cursor's, like [SHELF_INSET].
 * pourquoi : docs/decisions/bibliotheque.md § The air under the bar is the cursor's, and it is computed
 */
internal val HEADER_GAP = 22.dp

/**
 * The cursor spills 8.7 dp around a pill, and the socket has to hold it.
 * pourquoi : docs/decisions/bibliotheque.md § The air under the bar is the cursor's, and it is computed
 */
internal val SHELF_INSET = 10.dp

/**
 * Armed when the screen opens and disarmed after; a rescan or a folder rearms it.
 * pourquoi : docs/decisions/bibliotheque.md § The tiles' arrival is armed, then disarmed
 */
internal val LocalTileEntrance = staticCompositionLocalOf { false }

/** Thinner than the default: cover art is what the grid serves. */
internal const val TILE_BAND = 0.070f

internal const val ENTRANCE_WINDOW_MS = 900L

/**
 * How far the selected tile slides on the diagonal: the logo's staircase step.
 * pourquoi : docs/decisions/theme-duotone-shelves.md § The diagonal staircase
 */
/** Just clear of the tile's moulding; beyond that a pill floats in the artwork. */
internal val BADGE_INSET = 9.dp

internal val TILE_RISE = 2.5.dp

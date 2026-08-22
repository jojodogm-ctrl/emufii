package eu.emufii.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's icons, drawn rather than typed.
 *
 * They used to be text: `‹` for back, `✕` to remove a friend, an emoji in
 * every empty state. Three problems with that, and the third is the one that
 * decided it — a character is positioned by the font's metrics, so it never sits
 * quite in the centre of the button it is in; a character inherits the system
 * font on a device whose emoji set is not yours; and an emoji is somebody else's
 * illustration, at somebody else's weight, in the middle of a world that is
 * otherwise entirely moulded.
 *
 * All of them are built the same way: a 24-unit square, round caps, round joins,
 * one stroke weight. That is the whole icon system, and anything added later
 * must be drawn to the same three rules.
 */

/** The stroke every icon here is drawn with, relative to its box. */
private const val WEIGHT = 2.6f / 24f

@Composable
private fun TrayIcon(
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    draw: (Path.(Float) -> Unit)
) {
    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val path = Path().apply { draw(unit) }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = this.size.minDimension * WEIGHT,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/** Back. Points left; [ChevronRight] is the same drawing mirrored. */
@Composable
fun ChevronLeft(size: Dp = 20.dp, color: Color, modifier: Modifier = Modifier) =
    TrayIcon(size, color, modifier) { u ->
        moveTo(15f * u, 5f * u); lineTo(9f * u, 12f * u); lineTo(15f * u, 19f * u)
    }

/** Forward, and "this row opens something". */
@Composable
fun ChevronRight(size: Dp = 20.dp, color: Color, modifier: Modifier = Modifier) =
    TrayIcon(size, color, modifier) { u ->
        moveTo(9f * u, 5f * u); lineTo(15f * u, 12f * u); lineTo(9f * u, 19f * u)
    }

/** Close, dismiss, remove. */
@Composable
fun CrossIcon(size: Dp = 18.dp, color: Color, modifier: Modifier = Modifier) =
    TrayIcon(size, color, modifier) { u ->
        moveTo(6.5f * u, 6.5f * u); lineTo(17.5f * u, 17.5f * u)
        moveTo(17.5f * u, 6.5f * u); lineTo(6.5f * u, 17.5f * u)
    }

/** Done, ready, confirmed. */
@Composable
fun CheckIcon(size: Dp = 18.dp, color: Color, modifier: Modifier = Modifier) =
    TrayIcon(size, color, modifier) { u ->
        moveTo(5f * u, 12.5f * u); lineTo(10f * u, 17.5f * u); lineTo(19f * u, 6.5f * u)
    }

/**
 * The signal mark: two arcs leaving a point.
 *
 * Stands where "📡" stood, in the empty and unreachable states of the session
 * finder. Two arcs and a dot is what every radio has been drawn as since the
 * first modem light, and unlike the emoji it is the app's own line weight.
 */
@Composable
fun SignalMark(size: Dp = 40.dp, color: Color, modifier: Modifier = Modifier) =
    TrayIcon(size, color, modifier) { u ->
        addArc(
            androidx.compose.ui.geometry.Rect(
                Offset(4f * u, 4f * u), Size(16f * u, 16f * u)
            ), -70f, -50f
        )
        addArc(
            androidx.compose.ui.geometry.Rect(
                Offset(8f * u, 8f * u), Size(8f * u, 8f * u)
            ), -70f, -50f
        )
        addOval(
            androidx.compose.ui.geometry.Rect(
                Offset(11f * u, 15f * u), Size(2f * u, 2f * u)
            )
        )
    }

/**
 * The sleep mark: a crescent.
 *
 * Stands where "🌙" stood, on the finder with nobody playing. Drawn as one
 * closed path so the stroke traces a real crescent instead of two arcs that
 * happen to meet.
 */
/**
 * The folder mark: a tab and a body.
 *
 * Stands where "📁" stood, on a library with no ROM folder chosen yet. That
 * empty state is the first thing a new player sees, and it was showing them the
 * system's emoji set rather than the app.
 */
@Composable
fun FolderMark(size: Dp = 44.dp, color: Color, modifier: Modifier = Modifier) =
    TrayIcon(size, color, modifier) { u ->
        moveTo(3.5f * u, 18.5f * u)
        lineTo(3.5f * u, 6f * u)
        lineTo(9.5f * u, 6f * u)
        lineTo(11.5f * u, 8.5f * u)
        lineTo(20.5f * u, 8.5f * u)
        lineTo(20.5f * u, 18.5f * u)
        close()
    }

/**
 * Caution: this game runs, with something to know first.
 *
 * Just the mark, with no triangle around it.
 *
 * The triangle was there first and it was the wrong shape for the place it sits
 * in: the badge is already a round bead with a white rim, so an outlined shape
 * inside an outlined shape reads as cramped, and it left the caution mark itself
 * too small to see. Its two neighbours are single stroke figures that fill the
 * bead — a tick, a cross — and this is the third of that set rather than a
 * different kind of drawing.
 *
 * The dot is a stroke of no length: a round cap renders it as a disc of exactly
 * the icon weight, so it stays part of the same drawing instead of being a
 * filled shape smuggled into a stroke-only system.
 */
@Composable
fun WarnIcon(size: Dp = 14.dp, color: Color, modifier: Modifier = Modifier) =
    TrayIcon(size, color, modifier) { u ->
        moveTo(12f * u, 5f * u); lineTo(12f * u, 14f * u)
        moveTo(12f * u, 18.6f * u); lineTo(12f * u, 18.6f * u)
    }

/** Does not usefully run. The universal "no", and it is drawn, not typed. */
@Composable
fun BlockedIcon(size: Dp = 14.dp, color: Color, modifier: Modifier = Modifier) =
    TrayIcon(size, color, modifier) { u ->
        addOval(
            androidx.compose.ui.geometry.Rect(
                Offset(4f * u, 4f * u),
                Size(16f * u, 16f * u)
            )
        )
        moveTo(7.2f * u, 7.2f * u); lineTo(16.8f * u, 16.8f * u)
    }

/**
 * Not tried yet: a wave, meaning "roughly, maybe".
 *
 * Deliberately the quietest of the four marks. A tick, a cross and a caution
 * mark are all verdicts somebody stands behind; this one says only that the
 * game has a multiplayer mode and nobody has taken it out for a run. Drawn as
 * two joined curves rather than a straight line so it reads as the `~` it is
 * meant to be at nine pixels across.
 */
@Composable
fun TildeIcon(size: Dp = 14.dp, color: Color, modifier: Modifier = Modifier) =
    TrayIcon(size, color, modifier) { u ->
        moveTo(4.5f * u, 14f * u)
        cubicTo(7f * u, 8.5f * u, 9.5f * u, 8.5f * u, 12f * u, 12f * u)
        cubicTo(14.5f * u, 15.5f * u, 17f * u, 15.5f * u, 19.5f * u, 10f * u)
    }

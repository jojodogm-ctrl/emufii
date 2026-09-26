package eu.emufii.app.ui.screens.library

import eu.emufii.app.artwork.COVER_REQUEST_PX
import eu.emufii.app.artwork.CoverTone
import androidx.compose.ui.graphics.CompositingStrategy
import eu.emufii.app.ui.Motion
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import eu.emufii.app.R
import eu.emufii.app.compat.CompatRating
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import eu.emufii.app.artwork.rememberTileArt
import eu.emufii.app.compat.LocalCompatDb
import eu.emufii.app.library.Rom
import eu.emufii.app.library.compatKeys
import eu.emufii.app.ui.RING_IN_MS
import eu.emufii.app.ui.components.CompatBadge
import eu.emufii.app.ui.components.TileMenu
import eu.emufii.app.ui.components.artworkRim
import eu.emufii.app.ui.components.tilePlate
import eu.emufii.app.ui.LocalCardRom
import eu.emufii.app.ui.focusRing
import eu.emufii.app.ui.sharedCover
import eu.emufii.app.ui.gamepadClick
import eu.emufii.app.ui.ringColor
import eu.emufii.app.ui.tapOrHold
import eu.emufii.app.ui.theme.ArtworkShape
import eu.emufii.app.ui.theme.InkText
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.TileShape
import eu.emufii.app.ui.theme.liftShadow

@Composable
internal fun RomTile(
    rom: Rom,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selected: Boolean,
    padHeld: Boolean,
    menuOpen: Boolean,
    onChangeIcon: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onDismissMenu: () -> Unit,
    /** Zero in the grid; the carousel drops it so the ring does not cross the title. */
    titleDrop: Dp = 0.dp,
    /**
     * A share of the tile's smaller side, so the carousel's card -- three times a grid
     * tile -- wore three times the band. It sends a smaller share of its own.
     */
    band: Float = TILE_BAND,
    /**
     * The carousel's recession, 1 in the grid. Applied inside the flying
     * cover, never by the caller around it: from outside, the cover flew at full size and
     * shrank once landed, a second placement.
     * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Library
     */
    rest: () -> Float = { 1f },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Keyed on the ROM: a rescan replays the arrival for what changed, a recomposition
    // does not. Composed with it already over unless the screen has just opened.
    val playEntrance = LocalTileEntrance.current
    var shown by remember(rom.uri) { mutableStateOf(!playEntrance) }
    LaunchedEffect(rom.uri) { shown = true }


    /**
     * The card, when it is holding this game, wears this very cover: the tile stops
     * drawing it and the two are matched by uri, so it travels rather than crossfades.
     */
    val onTheCard = LocalCardRom.current == rom.uri

    /**
     * The card gives the focus back to the grid from a `LaunchedEffect`, which runs
     * *after* composition: for a frame or two the card is gone, the tile is visible
     * again and [selected] is still false. The mark's fall is instant by design, so it
     * collapsed in that gap -- the tile shrank by its 7 % and slid off its step just as
     * the cover landed on it, then climbed back over [RING_IN_MS]. That was the second
     * placement. Held across the hand-over, exactly as the header holds its panel.
     * pourquoi : docs/decisions/bibliotheque.md § One animation for the cursor's three marks
     */
    var handingOver by remember { mutableStateOf(false) }
    LaunchedEffect(onTheCard) {
        if (onTheCard) {
            handingOver = true
        } else {
            delay(CARD_HANDOVER_MS)
            handingOver = false
        }
    }
    val marked = selected || onTheCard || handingOver

    // A bouncy spring split the cursor into two halves for a few frames; one animation
    // for the three marks.
    // pourquoi : docs/decisions/bibliotheque.md § One clock for everything that marks the cell
    // pourquoi : docs/decisions/bibliotheque.md § One animation for the cursor's three marks
    val mark by animateFloatAsState(
        targetValue = if (marked) 1f else 0f,
        // The trailer's morph on arrival, its exit on leaving: one spring still drives
        // the three marks, so they cannot come apart.
        // pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § The focus ring
        animationSpec = if (marked) Motion.morph() else Motion.exit(),
        label = "tile-mark"
    )
    val focusScale = 1f + 0.07f * mark

    val entrance by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = Motion.arrival(),
        label = "tile-entrance"
    )

    val scale by animateFloatAsState(
        targetValue = if (pressed || padHeld) 0.94f else 1f,
        animationSpec = Motion.press(),
        label = "tile-scale"
    )

    // Towards the top-left, the logo's own step; on the ring's clock and gone with it.
    // pourquoi : docs/decisions/theme-duotone-shelves.md § The diagonal staircase
    val riseX = TILE_RISE * mark
    val riseY = TILE_RISE * mark

    val lit = marked && entrance > 0.99f

    Column(
        // Above its neighbours while enlarged, or the next one draws over it and cuts
        // the glow clean off. The caller's modifier stays on this same node, so the
        // grid's placement animation and the zIndex do not end up on two different ones.
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (selected) 1f else 0f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Pulled from the artwork, for the tile's menu: the chrome stays neutral.
        val accent = rom.accentArgb?.let { Color(it) }
        // Read above the box: the ring's width depends on which of the two the tile shows.
        val art by rememberTileArt(rom)
        val ring = ringColor()
        val ringBand = if (art.model == null) band * PLACEHOLDER_BAND_SHARE else band
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = -riseX, y = -riseY)
                .aspectRatio(1f)
                .sharedCover(rom.uri, mine = !onTheCard)
                .graphicsLayer {
                    scaleX = rest()
                    scaleY = rest()
                }
                .scale(scale * focusScale * (0.88f + 0.12f * entrance))
                // `graphicsLayer`, never `alpha`: under 1, `alpha` lays a rectangular
                // clip that squares off the ring.
                // pourquoi : docs/decisions/navigation-manette.md § `Modifier.alpha` clips, and that is what made the cursor square
                .graphicsLayer {
                    this.alpha = entrance
                    // Per draw: an offscreen buffer cut the tile's shadow square while it faded in.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                // Lift 4 at rest, 10 selected; a press sinks it. The cover's colour glows
                // round the tile under the cursor only: forty tinted halos at once made
                // the grid a wash. Elsewhere (the card, a session) a cover always glows.
                // Never clips: the ring surrounds the tile from outside.
                // pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Flat plates, dropped shadows
                .liftShadow(
                    TileShape,
                    lift = 4.dp,
                    raisedLift = 10.dp,
                    raised = { mark },
                    dark = LocalEmufiiDarkTheme.current,
                    oled = LocalEmufiiOledTheme.current,
                    tintNow = { CoverTone.of(art.model) ?: accent ?: ring },
                    sink = { if (pressed || padHeld) 1f else 0f },
                    tinted = { mark },
                    fade = { entrance }
                )
                // Never on a tile still fading in: a glow is a shadow, and it draws
                // through a translucent layer. Thinner than elsewhere, so the cursor
                // circles the cover art without disputing the cell.
                // pourquoi : docs/decisions/bibliotheque.md § One clock for everything that marks the cell
                .focusRing(lit, TileShape, bandFraction = ringBand)
                .clip(TileShape)
                .background(tilePlate())
                // Over the artwork: box art running to the corner turns the tile
                // back into a printed square.
                // Clickable but NEVER focusable: the grid holds the cursor, so a
                // tile capturing focus makes it vanish.
                // pourquoi : docs/decisions/bibliotheque.md § The cursor is a computed index, never a guessed focus
                .focusProperties { canFocus = false }
                .tapOrHold(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .gamepadClick(interaction, onClick = onClick)
        ) {
            // A broken verdict greys the tile instead of pinning a red cross on it, which
            // read as "delete"; the cross stays on the game's card, where it is explained.
            // pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Library
            val rating = LocalCompatDb.current.ratingFor(rom.compatKeys())?.rating
            val broken = rating == CompatRating.BROKEN
            if (art.model != null) {
                AsyncImage(
                    colorFilter = if (broken) GreyedOut else null,
                    alpha = if (broken) BROKEN_ALPHA else 1f,
                    model = ImageRequest.Builder(context).data(art.model).size(COVER_REQUEST_PX).build(),
                    onSuccess = { CoverTone.learn(art.model, it.result.image) },
                    contentDescription = rom.displayName,
                    // The ROM's icon is left whole: at 48 px, cropping removes a visible
                    // part of the drawing. ES-DE serves box fronts, cropped the same way.
                    contentScale = if (art.fitsWhole) ContentScale.Fit else ContentScale.Crop,
                    // Pixel art scales up without smoothing, or it turns to mush.
                    filterQuality =
                        if (art.isPixelArt) FilterQuality.None else FilterQuality.High,
                    // A thin white contour separates artwork from background whatever the
                    // box art is; wider, it reads as the white plate this used to have.
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp)
                        .clip(ArtworkShape)
                        .border(2.dp, artworkRim(), ArtworkShape),
                    placeholder = ColorPainter(Color.Transparent),
                    error = ColorPainter(Color.Transparent)
                )
            } else {
                PlaceholderArtwork(rom.displayName)
            }

            // Inside the tile, the Popup's anchor, and never conditioned: it needs the
            // time to close.
            // pourquoi : docs/decisions/bibliotheque.md § Holding A, and the title that fades out
            TileMenu(
                expanded = menuOpen,
                title = rom.displayName,
                changeIconLabel = stringResource(R.string.tile_menu_icon),
                renameLabel = stringResource(R.string.tile_menu_rename),
                hideLabel = stringResource(R.string.tile_menu_hide),
                accent = accent,
                onChangeIcon = onChangeIcon,
                onRename = onRename,
                onHide = onHide,
                onDismiss = onDismissMenu
            )

            // 9 dp, not 6: the tile carries a moulding, and at 6 dp the pill bit into it.
            // pourquoi : docs/decisions/bibliotheque.md § The console badge is 9 dp from the edge, not 6
            ConsoleBadge(
                console = rom.console,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(BADGE_INSET)
            )

            // Opposite corner from the console badge: stacked, the pair reads as one
            // compound label.
            rating?.takeIf { !broken }?.let { verdict ->
                CompatBadge(
                    rating = verdict,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(BADGE_INSET)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        TileTitle(
            rom.displayName,
            // On the ring's clock: the title moves aside while the cursor arrives.
            modifier = Modifier.graphicsLayer {
                translationY = titleDrop.toPx() * mark
                scaleX = rest()
                scaleY = rest()
            }
        )
    }
}

/**
 * Long enough to cover the frames between the card leaving and the grid getting its
 * focus back. Two orders of magnitude above a focus hand-over, like the header's own.
 * pourquoi : docs/decisions/bibliotheque.md § One animation for the cursor's three marks
 */
private const val CARD_HANDOVER_MS = 120L

private val GreyedOut = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

private const val BROKEN_ALPHA = 0.55f

package eu.emufii.app.ui.screens.library

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
import eu.emufii.app.artwork.rememberTileArt
import eu.emufii.app.compat.LocalCompatDb
import eu.emufii.app.library.Rom
import eu.emufii.app.library.compatKeys
import eu.emufii.app.ui.RING_IN_MS
import eu.emufii.app.ui.components.CompatBadge
import eu.emufii.app.ui.components.TileMenu
import eu.emufii.app.ui.components.artworkRim
import eu.emufii.app.ui.components.tilePlate
import eu.emufii.app.ui.focusRing
import eu.emufii.app.ui.gamepadClick
import eu.emufii.app.ui.ringColor
import eu.emufii.app.ui.tapOrHold
import eu.emufii.app.ui.theme.ArtworkShape
import eu.emufii.app.ui.theme.InkText
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.TileShape
import eu.emufii.app.ui.theme.moldedRim

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
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Keyed on the ROM: a rescan replays the arrival for what changed, a recomposition
    // does not. Composed with it already over unless the screen has just opened.
    val playEntrance = LocalTileEntrance.current
    var shown by remember(rom.uri) { mutableStateOf(!playEntrance) }
    LaunchedEffect(rom.uri) { shown = true }


    // A bouncy spring split the cursor into two halves for a few frames; one animation
    // for the three marks.
    // pourquoi : docs/decisions/bibliotheque.md § One clock for everything that marks the cell
    // pourquoi : docs/decisions/bibliotheque.md § One animation for the cursor's three marks
    val mark by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(if (selected) RING_IN_MS else 0),
        label = "tile-mark"
    )
    val focusScale = 1f + 0.07f * mark

    val entrance by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "tile-entrance"
    )

    val scale by animateFloatAsState(
        targetValue = if (pressed || padHeld) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tile-scale"
    )
    val elevation by animateFloatAsState(
        targetValue = if (pressed || padHeld) 2f else 8f,
        label = "tile-elev"
    )

    // Towards the top-left, the logo's own step; on the ring's clock and gone with it.
    // pourquoi : docs/decisions/theme-duotone-shelves.md § The diagonal staircase
    val riseX = TILE_RISE * mark
    val riseY = TILE_RISE * mark

    val lit = selected && entrance > 0.99f

    Column(
        // Above its neighbours while enlarged, or the next one draws over it and cuts
        // the glow clean off.
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (selected) 1f else 0f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Pulled from the artwork: the chrome stays neutral, the content brings the
        // palette. No colour to borrow, plain shadow.
        val accent = rom.accentArgb?.let { Color(it) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = -riseX, y = -riseY)
                .aspectRatio(1f)
                .scale(scale * focusScale * (0.88f + 0.12f * entrance))
                // `graphicsLayer`, never `alpha`: under 1, `alpha` lays a rectangular
                // clip that squares off the ring.
                // pourquoi : docs/decisions/navigation-manette.md § `Modifier.alpha` clips, and that is what made the cursor square
                .graphicsLayer { this.alpha = entrance }
                .shadow(
                    elevation = (elevation + if (accent != null) 10f else 0f).dp,
                    shape = TileShape,
                    // Never clips. `shadow` defaults to `clip = elevation > 0`, which
                    // cut the ring, since the ring surrounds the tile from outside.
                    // pourquoi : docs/decisions/navigation-manette.md § The ring surrounds, it does not clip
                    clip = false,
                    // Warm ink, never blue-black: the glow reads as light under the
                    // tile, not a coloured outline.
                    ambientColor = InkText.copy(alpha = 0.22f),
                    // pourquoi : docs/decisions/theme-duotone-shelves.md § GAMEPAD FOCUS
                    spotColor = (if (selected) ringColor() else accent)
                        ?: InkText.copy(alpha = 0.30f)
                )
                // Never on a tile still fading in: a glow is a shadow, and it draws
                // through a translucent layer. Thinner than elsewhere, so the cursor
                // circles the cover art without disputing the cell.
                // pourquoi : docs/decisions/bibliotheque.md § One clock for everything that marks the cell
                .focusRing(lit, TileShape, bandFraction = TILE_BAND)
                .clip(TileShape)
                .background(tilePlate())
                // Over the artwork: box art running to the corner turns the tile
                // back into a printed square.
                .moldedRim(
                    TileShape,
                    dark = LocalEmufiiDarkTheme.current,
                    oled = LocalEmufiiOledTheme.current
                )
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
            val art by rememberTileArt(rom)
            if (art.model != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(art.model).build(),
                    contentDescription = rom.displayName,
                    // The ROM's icon is left whole: at 48 px, cropping removes a visible
                    // part of the drawing.
                    contentScale = if (art.isPixelArt) ContentScale.Fit else ContentScale.Crop,
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
            LocalCompatDb.current.ratingFor(rom.compatKeys())?.let { entry ->
                CompatBadge(
                    rating = entry.rating,
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
            modifier = Modifier.graphicsLayer { translationY = titleDrop.toPx() * mark }
        )
    }
}

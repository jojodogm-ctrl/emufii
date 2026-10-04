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
    titleDrop: Dp = 0.dp,
    band: Float = TILE_BAND,
    rest: () -> Float = { 1f },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val playEntrance = LocalTileEntrance.current
    var shown by remember(rom.uri) { mutableStateOf(!playEntrance) }
    LaunchedEffect(rom.uri) { shown = true }


    val onTheCard = LocalCardRom.current == rom.uri

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

    val mark by animateFloatAsState(
        targetValue = if (marked) 1f else 0f,
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

    val riseX = TILE_RISE * mark
    val riseY = TILE_RISE * mark

    val lit = marked && entrance > 0.99f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (selected) 1f else 0f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val accent = rom.accentArgb?.let { Color(it) }
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
                // graphicsLayer, not alpha(): alpha() clips and squares off the ring.
                .graphicsLayer {
                    this.alpha = entrance
                    // Per draw: an offscreen buffer cut the tile's shadow square while it faded in.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
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
                .focusRing(lit, TileShape, bandFraction = ringBand)
                .clip(TileShape)
                .background(tilePlate())
                // Never focusable: the grid owns the cursor.
                .focusProperties { canFocus = false }
                .tapOrHold(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .gamepadClick(interaction, onClick = onClick)
        ) {
            val rating = LocalCompatDb.current.ratingFor(rom.compatKeys())?.rating
            val broken = rating == CompatRating.BROKEN
            if (art.model != null) {
                AsyncImage(
                    colorFilter = if (broken) GreyedOut else null,
                    alpha = if (broken) BROKEN_ALPHA else 1f,
                    model = ImageRequest.Builder(context).data(art.model).size(COVER_REQUEST_PX).build(),
                    onSuccess = { CoverTone.learn(art.model, it.result.image) },
                    contentDescription = rom.displayName,
                    contentScale = if (art.fitsWhole) ContentScale.Fit else ContentScale.Crop,
                    filterQuality =
                        if (art.isPixelArt) FilterQuality.None else FilterQuality.High,
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

            ConsoleBadge(
                console = rom.console,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(BADGE_INSET)
            )

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
            modifier = Modifier.graphicsLayer {
                translationY = titleDrop.toPx() * mark
                scaleX = rest()
                scaleY = rest()
            }
        )
    }
}

private const val CARD_HANDOVER_MS = 120L

private val GreyedOut = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

private const val BROKEN_ALPHA = 0.55f

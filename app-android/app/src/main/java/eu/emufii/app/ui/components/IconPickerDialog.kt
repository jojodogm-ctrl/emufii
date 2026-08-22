package eu.emufii.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import coil3.compose.AsyncImage
import eu.emufii.app.R
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.plate
import eu.emufii.app.artwork.ArtworkStore
import eu.emufii.app.artwork.SgdbGame
import eu.emufii.app.artwork.SgdbIcon
import eu.emufii.app.artwork.SteamGridDb
import eu.emufii.app.library.Rom
import eu.emufii.app.ui.theme.ArtworkShape
import eu.emufii.app.ui.theme.PillShape

/**
 * Choosing a game's icon yourself.
 *
 * Automatic matching takes the first result the catalogue returns for the
 * filename. It gets it wrong, and not rarely: a sequel, a port, a regional
 * subtitle, an exotic dump name, and the tile then carries another game's icon.
 * No heuristic repairs that reliably, only someone who recognises the right game
 * can.
 *
 * Hence two levels, and the second is the real subject: we show every icon for
 * the matched game, but we also allow searching for a different game. Offering
 * only the icons would amount to fixing the colour while leaving the wrong game,
 * which is the commonest case.
 */
@Composable
fun IconPickerDialog(
    rom: Rom,
    apiKey: String,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember(context) { ArtworkStore(context.applicationContext) }

    var query by remember { mutableStateOf(SteamGridDb.searchTerm(rom.displayName)) }
    var games by remember { mutableStateOf<List<SgdbGame>>(emptyList()) }
    var selectedGame by remember { mutableStateOf<SgdbGame?>(null) }
    var icons by remember { mutableStateOf<List<SgdbIcon>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var loadingIcons by remember { mutableStateOf(false) }

    // One search per keystroke would flood the API for nothing: we wait for the
    // typing to settle. 400 ms is short when reading and long when typing.
    LaunchedEffect(query, apiKey) {
        searching = true
        kotlinx.coroutines.delay(400)
        games = SteamGridDb.searchGames(query, apiKey)
        // The first result is the one automatic matching would have taken:
        // preselecting it shows straight away what it did, and makes the fact
        // that it got it wrong visible.
        selectedGame = games.firstOrNull()
        searching = false
    }

    LaunchedEffect(selectedGame, apiKey) {
        val game = selectedGame
        if (game == null) {
            icons = emptyList()
            return@LaunchedEffect
        }
        loadingIcons = true
        icons = SteamGridDb.icons(game.id, apiKey)
        loadingIcons = false
    }

    // The search field must on no account take focus on opening: in landscape
    // the IME then shows fullscreen (extract mode) and covers the whole window,
    // leaving not one icon visible. An invisible anchor takes focus first and
    // keeps it.
    val anchor = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { anchor.requestFocus() } }

    val screenHeight = LocalConfiguration.current.screenHeightDp

    Dialog(
        onDismissRequest = onDismiss,
        // A Dialog's default width is narrow and fixed: on a landscape screen it
        // gave a column of icons lost in the middle of the emptiness.
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        SoftCard(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 560.dp)
                // Bounded to the screen, otherwise the grid pushes the buttons
                // off it and the window can only be closed by tapping outside,
                // which is written down nowhere.
                .heightIn(max = (screenHeight * 0.92f).dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.focusRequester(anchor).focusable())
                Text(
                    stringResource(R.string.icon_pick_title),
                    style = MaterialTheme.typography.headlineSmall
                )

                // A `PadTextField`: the frame is the traversal step and A goes
                // in, so the pad's cursor can pass over the search box without
                // the IME springing up. That is the same failure the invisible
                // anchor above guards against on opening — the anchor stays,
                // because it also has to survive a recomposition, but the field
                // no longer causes it.
                PadTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.icon_pick_search),
                    singleLine = true,
                    shape = PillShape,
                    modifier = Modifier.fillMaxWidth()
                )

                // The games found, in a scrolling row: this is the choice that
                // decides everything else, so it stays visible while the icons
                // are being looked at.
                if (games.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        games.take(12).forEach { game ->
                            GameChip(
                                label = game.name,
                                selected = game.id == selectedGame?.id,
                                onClick = { selectedGame = game }
                            )
                        }
                    }
                }

                when {
                    searching || loadingIcons -> Box(
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    icons.isEmpty() -> Box(
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.icon_pick_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(84.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        // `fill = false`: the grid takes what is left but no
                        // more than its content, so three icons do not leave a
                        // large gap underneath them.
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                    ) {
                        items(icons, key = { it.url }) { icon ->
                            IconChoice(
                                icon = icon,
                                onClick = {
                                    store.choose(rom, icon.url)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Only when a choice exists: offering to undo what was never
                    // done suggests it was.
                    if (store.chosenFor(rom) != null) {
                        GhostButton(
                            label = stringResource(R.string.icon_pick_reset),
                            onClick = {
                                store.clearChoice(rom)
                                onDismiss()
                            }
                        )
                    } else {
                        Spacer()
                    }
                    GhostButton(
                        label = stringResource(R.string.icon_pick_close),
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun Spacer() = Box(Modifier)

/**
 * One of the games the search came back with.
 *
 * A moulded pill like every other pressable thing, and not the tonal `Surface`
 * that was here: `surfaceVariant` against `primary` was Material choosing two
 * flat fills of its own, in a world where relief says "object" and the one
 * accent says "this one". Selected, the pill is pushed in — the choice already
 * made is the one that is down.
 */
@Composable
private fun GameChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .controlRing(PillShape, width = 3.dp, glowRadius = 18.dp)
            .plate(
                shape = PillShape,
                dark = dark,
                oled = oled,
                lift = if (selected) 0.dp else 4.dp,
                pressed = pressed || selected
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color =
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * A candidate icon, on a checkerboard.
 *
 * Many are transparent: laid on a solid background, emptiness cannot be told
 * from white, and you end up choosing an icon whose real shape you never saw.
 */
@Composable
private fun IconChoice(icon: SgdbIcon, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                // The ring before the clip, as everywhere: after it, the glow
                // was sliced at the tile's own outline.
                .controlRing(ArtworkShape, width = 3.dp, glowRadius = 18.dp)
                .clip(ArtworkShape)
                .background(Color(0xFFE9ECF2))
                .border(1.dp, Color(0x1A000000), ArtworkShape)
                .clickable(onClick = onClick)
        ) {
            AsyncImage(
                model = icon.thumb,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().padding(6.dp)
            )
        }
        if (icon.px > 0) {
            Text(
                "${icon.px}px",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

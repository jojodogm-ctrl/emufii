package eu.emufii.app.ui.screens

import eu.emufii.app.ui.sounded
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import eu.emufii.app.ui.ActionShape
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.LocalRingTone
import eu.emufii.app.ui.RingTone
import eu.emufii.app.ui.components.RomArtwork
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import eu.emufii.app.R
import eu.emufii.app.azahar.LaunchResult
import eu.emufii.app.library.Rom
import eu.emufii.app.psp.PpssppLauncher
import eu.emufii.app.ui.components.EmufiiScaffold
import eu.emufii.app.ui.components.SectionHeader
import eu.emufii.app.ui.components.SoftCard
import eu.emufii.app.ui.components.padEntry
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.GoodDark
import eu.emufii.app.ui.theme.GoodLight
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.Teal

@Composable
private fun good() = if (LocalEmufiiDarkTheme.current) GoodDark else GoodLight


/**
 * A screen and not a card: the player goes off into PPSSPP, sets their network up, comes
 * back, and the next button has to still be where they left it.
 *
 * Emufii creates no session and brings up no tunnel here; before opening PPSSPP it
 * restores the four network values saved before private-session play, and the player
 * chooses the third-party public server in PPSSPP's own interface.
 *
 * PPSSPP's network settings cannot be reached from a running game, so opening the
 * emulator on its own comes first and launching the game stays second.
 */
@Composable
fun PspOnlineScreen(
    rom: Rom,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ppsspp = remember { PpssppLauncher(context) }
    var status by remember { mutableStateOf<String?>(null) }

    // "The emulator has been opened", not "the public server is right".
    var opened by remember(rom.uri) { mutableStateOf(false) }

    fun report(result: LaunchResult, onSuccess: () -> Unit = {}) {
        status = when (result) {
            LaunchResult.Success -> { onSuccess(); null }
            LaunchResult.NotInstalled -> context.getString(R.string.err_not_installed, "PPSSPP")
            is LaunchResult.Error -> context.getString(R.string.err_generic, result.message)
            // No netplay to drive in PPSSPP: the case does not exist here.
            is LaunchResult.NoNetplayUi -> null
        }
    }

    // The social domain: the pad cursor turns coral here.
    // pourquoi : docs/decisions/theme-duotone-shelves.md § GAMEPAD FOCUS
    CompositionLocalProvider(LocalRingTone provides RingTone.CORAL) {
    EmufiiScaffold(
        title = stringResource(R.string.psp_online_title),
        modifier = modifier,
        onBack = onBack,
        contentScrolls = false
    ) { topPadding ->
        // Centred on the screen, not under the header: a height ceiling clipped the
        // content instead of compressing it.
        // pourquoi : docs/decisions/lancement-et-navigation.md § PSP online: two panes, and centred on the screen
        Box(
            // Centred to within 4 px geometrically, but the header's weight in the upper
            // corner made it read as high; ten dp of difference corrects that.
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            SoftCard(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp - 24.dp)
                    
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier.width(190.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RomArtwork(rom, size = 104.dp)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                rom.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                stringResource(R.string.launch_mode_online, rom.console.label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            stringResource(R.string.psp_online_what_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // The steps give way first when room runs short, never the buttons:
                        // the second is greyed out until the first has been used, so hiding
                        // it would make the screen incomprehensible.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SectionHeader(stringResource(R.string.psp_online_steps_title))
                            NumberedStep(1, stringResource(R.string.psp_online_step_1))
                            NumberedStep(2, stringResource(R.string.psp_online_step_2))
                            NumberedStep(3, stringResource(R.string.psp_online_step_3))
                            NumberedStep(4, stringResource(R.string.psp_online_step_4))
                        }

                        Button(
                            onClick = sounded { report(ppsspp.openPublicSettings(rom)) { opened = true } },
                            shape = ActionShape,
                            colors = if (opened) ButtonDefaults.buttonColors(containerColor = good())
                                     else ButtonDefaults.buttonColors(),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                                .controlRing(ActionShape).padEntry()
                        ) {
                            Text(
                                stringResource(
                                    if (opened) R.string.psp_online_open_again
                                    else R.string.psp_online_open_settings
                                ),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Button(
                            onClick = sounded { report(ppsspp.launchPublicGame(rom)) },
                            // Launching the game first lands in an ad hoc lobby still
                            // pointing at the previous game's server.
                            enabled = opened,
                            shape = ActionShape,
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                                .controlRing(ActionShape)
                        ) {
                            Text(
                                stringResource(R.string.psp_online_launch_game),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        status?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun NumberedStep(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text,
            // Four two-line steps did not fit at bodyMedium, and the fourth, the one
            // telling you to come back here, fell off screen.
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = 0.88f)
        )
    }
}

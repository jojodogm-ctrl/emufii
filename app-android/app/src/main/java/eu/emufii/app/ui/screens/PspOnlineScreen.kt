package eu.emufii.app.ui.screens

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
import eu.emufii.app.ui.components.RomArtwork
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import eu.emufii.app.R
import eu.emufii.app.azahar.LaunchResult
import eu.emufii.app.library.Rom
import eu.emufii.app.psp.PpssppLauncher
import eu.emufii.app.ui.components.EmufiiScaffold
import eu.emufii.app.ui.components.SectionHeader
import eu.emufii.app.ui.components.SoftCard
import eu.emufii.app.ui.components.padEntry
import eu.emufii.app.ui.theme.AccentGreen

/**
 * Playing a PSP game online, with strangers.
 *
 * A screen and not a card, for the same reason a session has one: there is a
 * sequence of steps to carry out in another program, you come back from it, and
 * you have to find your place again. A launch card disappears at the first
 * press; here the player goes off into PPSSPP, sets their network up, comes
 * back, and the next button is where they left it.
 *
 * Emufii creates no session and brings up no tunnel, this mode does not go
 * through it. Nor can it set PPSSPP up on the player's behalf: it can neither
 * write its `ppsspp.ini` nor drive its interface, which is an opaque surface. So
 * it opens the right doors in the right order and says what to do behind them.
 * The full reasoning is in `docs/PHASE1_SCOUT_PPSSPP_ONLINE.md`.
 *
 * The two buttons are two moments, not two ways of doing the same thing:
 * PPSSPP's network settings cannot be reached from a running game, so opening
 * the emulator on its own has to come first, and launching the game has to stay
 * second. It is the pair that already exists for Azahar, on identical grounds.
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

    // "The emulator has been opened", not "the settings are right": Emufii sees
    // nothing of what happens inside PPSSPP. The green therefore says what it
    // knows, and the button stays pressable again, since people often go back
    // into the settings a second time.
    var opened by remember(rom.uri) { mutableStateOf(false) }

    fun report(result: LaunchResult, onSuccess: () -> Unit = {}) {
        status = when (result) {
            LaunchResult.Success -> { onSuccess(); null }
            LaunchResult.NotInstalled -> context.getString(R.string.err_not_installed, "PPSSPP")
            is LaunchResult.Error -> context.getString(R.string.err_generic, result.message)
            // No netplay to drive in PPSSPP: this case does not exist here.
            is LaunchResult.NoNetplayUi -> null
        }
    }

    EmufiiScaffold(
        title = stringResource(R.string.psp_online_title),
        modifier = modifier,
        onBack = onBack,
        contentScrolls = false
    ) { topPadding ->
        // Two panes, like the session screen and like Kaeru. This screen was the
        // last one built in portrait: two full-width cards and two 56 dp buttons
        // stacked in a scrolling column, of which the Thor showed only a third,
        // and the instructions you come here for were precisely in the two thirds
        // you could not see.
        //
        // On the left the game and what this mode is, on the right what there is
        // to do and the two buttons that do it.
        // Centred on the screen, not under the header.
        //
        // Reserving `topPadding` centred the card within what was left *below*
        // the title, hence 87 px too low. A height ceiling was tried so it could
        // be centred without risking going behind the header: it clipped the
        // content instead of compressing it, the left column not being
        // scrollable, so whatever overflows disappears. Removed.
        //
        // What makes plain centring possible is that the card has slimmed down:
        // 310 dp of the device's 468, its top edge landing at 79 dp where the
        // header stops at 68. The remaining `heightIn` is only a screen stop, and
        // it does not trigger here.
        Box(
            // A little more margin at the top than at the bottom: geometrically
            // the card was centred to within 4 px, but the header carries weight
            // in the upper corner and the eye read it as sitting high. Ten dp of
            // difference put it where it is expected.
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
                        // The steps give way first when room runs short; the
                        // buttons never do. The same rule as everywhere else, and
                        // on this screen it counts double: the second button is
                        // greyed out until the first has been used, so hiding it
                        // would make the screen incomprehensible.
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
                            onClick = { report(ppsspp.openApp()) { opened = true } },
                            shape = ActionShape,
                            colors = if (opened) ButtonDefaults.buttonColors(containerColor = AccentGreen)
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
                            onClick = { report(ppsspp.launchGame(rom.uri)) },
                            // Greyed out until the emulator has been opened
                            // once: launching the game first means arriving in an
                            // ad hoc lobby still pointing at the previous game's
                            // server, the exact mistake this pair exists to
                            // prevent.
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
            // One notch below bodyMedium. Four two-line steps did not fit, and
            // the fourth, the one telling you to come back here, fell off screen:
            // the only one that cannot be guessed from inside PPSSPP.
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = 0.88f)
        )
    }
}

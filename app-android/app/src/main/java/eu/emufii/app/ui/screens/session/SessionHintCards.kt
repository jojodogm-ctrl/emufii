package eu.emufii.app.ui.screens.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.emufii.app.R
import eu.emufii.app.dolphin.DolphinTarget
import eu.emufii.app.library.Backend
import eu.emufii.app.ps2.Ps2GameSettings
import eu.emufii.app.ps2.Ps2Target
import eu.emufii.app.psp.HOST_SENTINEL
import eu.emufii.app.session.Session
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.SectionHeader
import eu.emufii.app.ui.components.SoftCard
import eu.emufii.app.ui.components.WarnIcon
import eu.emufii.app.ui.copyToClipboard
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.socket

/** One definition, so the two layouts cannot drift apart. */
@Composable
internal fun EmulatorHintCard(
    session: Session,
    automationOn: Boolean,
) {
    if (session.rom == null) {
        MissingRomCard()
        return
    }
    when (session.backend) {
        Backend.AZAHAR -> AzaharHintCard(
            automationOn = automationOn,
            hostIp = session.hostIp,
            port = session.port
        )
        // Unreachable from the library, which routes DS to the Kaeru screen; a session joined
        // from the finder carries whatever console the host had.
        Backend.EDEN -> EdenHintCard(
            automationOn = automationOn,
            // With a room on the VPS the host joins like everyone else: "Create" would open a
            // second, empty room next to the one where their guest waits.
            isHost = session.role == Session.Role.HOST && session.room == null,
            // What the player would type by hand: the room when there is one, the host otherwise.
            hostIp = session.room?.host ?: session.hostIp,
            port = session.room?.port?.toString() ?: session.port
        )

        Backend.DOLPHIN -> DolphinHintCard(
            automationOn = automationOn,
            isHost = session.role == Session.Role.HOST,
            hostIp = session.hostIp,
            port = DolphinTarget.DEFAULT_PORT.toString()
        )

        Backend.ARMSX2 -> Ps2HintCard(
            automationOn = session.rom.let {
                Ps2GameSettings.canConfigure(LocalContext.current, it)
            },
            isHost = session.role == Session.Role.HOST,
            hostIp = session.hostIp,
            port = Ps2Target.DEFAULT_PORT.toString()
        )

        Backend.PPSSPP -> Unit
        Backend.MELONDS_WFC -> WfcNotASessionCard()
        Backend.NONE -> UnsupportedHintCard(session.console?.label)
    }
}

/**
 * A line the player cannot afford to skim, inside a card of lines they can.
 * pourquoi : docs/decisions/session.md § This screen's drawing decisions
 */
@Composable
private fun ImportantNote(text: String) {
    // A recess, ordinary ink, a drawn bead, never a red field: red is spent exactly twice in
    // the whole app.
    // pourquoi : docs/decisions/session.md § This screen's drawing decisions
    val dark = LocalEmufiiDarkTheme.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .socket(shape, dark)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        WarnIcon(
            size = 17.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Aligned to the first line's ink, not centred on the block: a mark that drifts to
            // the middle of a three-line note reads as decoration.
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AzaharHintCard(
    automationOn: Boolean,
    hostIp: String,
    port: String,
) {
    SoftCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionHeader(stringResource(R.string.hint_azahar_title))
            // Loud on both paths: getting it wrong produces an error that accuses the address.
            // pourquoi : docs/decisions/session.md § What is done by hand is said before the button, never after
            ImportantNote(stringResource(R.string.hint_azahar_username))
            ImportantNote(stringResource(R.string.hint_same_version))
            if (automationOn) {
                Text(
                    stringResource(R.string.hint_azahar_automated),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    stringResource(
                        R.string.hint_azahar_manual,
                        "$hostIp:$port"
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * Eden's multiplayer is in the app's own settings, not a game drawer; host is told to Create
 * and guest to Join, the same words would put both on one side.
 * pourquoi : docs/decisions/session.md § The per-console cards, and what each must prevent
 */
@Composable
private fun EdenHintCard(
    automationOn: Boolean,
    isHost: Boolean,
    hostIp: String,
    port: String,
) {
    SoftCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionHeader(stringResource(R.string.hint_eden_title))
            Text(
                stringResource(
                    if (isHost) R.string.hint_eden_host else R.string.hint_eden_guest
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            // A prerequisite the emulator does not mention: a differing game version lets the
            // room form, then the game never starts, and nothing points at the cause.
            ImportantNote(stringResource(R.string.hint_same_version))
            if (automationOn) {
                Text(
                    stringResource(R.string.hint_eden_automated),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.hint_eden_username),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    stringResource(R.string.hint_eden_manual, "$hostIp:$port"),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * One step where the others are two, the game being picked in the lobby; both sides need the
 * same dump, byte for byte.
 * pourquoi : docs/decisions/session.md § The Dolphin prerequisite nobody checks
 */
@Composable
private fun DolphinHintCard(
    automationOn: Boolean,
    isHost: Boolean,
    hostIp: String,
    port: String,
) {
    SoftCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionHeader(stringResource(R.string.hint_dolphin_title))
            Text(
                stringResource(
                    if (isHost) R.string.hint_dolphin_host else R.string.hint_dolphin_guest
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            ImportantNote(stringResource(R.string.hint_dolphin_same_dump))
            // Worse than the dump: nobody checks the save, and mismatched saves desync
            // silently. We warn, we cannot act.
            // pourquoi : docs/decisions/session.md § The Dolphin prerequisite nobody checks
            ImportantNote(stringResource(R.string.hint_dolphin_same_save))
            if (automationOn) {
                Text(
                    stringResource(R.string.hint_dolphin_automated),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    stringResource(R.string.hint_dolphin_manual, "$hostIp:$port"),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * ARMSX2 has two unrelated multiplayers and Emufii serves only the local one.
 * pourquoi : docs/decisions/session.md § The per-console cards, and what each must prevent
 */
@Composable
private fun Ps2HintCard(
    automationOn: Boolean,
    isHost: Boolean,
    hostIp: String,
    port: String,
) {
    SoftCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionHeader(stringResource(R.string.hint_ps2_title))
            ImportantNote(stringResource(R.string.hint_ps2_lan_only))
            Text(
                stringResource(if (isHost) R.string.hint_ps2_host else R.string.hint_ps2_guest),
                style = MaterialTheme.typography.bodyMedium
            )
            // Said by ARMSX2 itself: with the network adapter attached some games stop
            // responding to the pad, which reads as a frozen app.
            ImportantNote(stringResource(R.string.hint_ps2_pad))
            if (automationOn) {
                Text(
                    stringResource(R.string.hint_ps2_automated),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    stringResource(R.string.hint_ps2_manual, "$hostIp:$port"),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun MissingRomCard() {
    SoftCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader(stringResource(R.string.hint_missing_rom_title))
            Text(
                stringResource(R.string.hint_missing_rom_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Deliberately not an error: a running game keeps running, only the presence list stops
 * being trustworthy.
 * pourquoi : docs/decisions/session.md § Only a 404 proves a room is closed
 */
@Composable
internal fun OfflineCard() {
    SoftCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader(stringResource(R.string.session_offline_title))
            Text(
                stringResource(R.string.session_offline_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * The address is copied on *display* rather than on tap: the player is about to leave for PPSSPP.
 * pourquoi : docs/decisions/session.md § The per-console cards, and what each must prevent
 */
@Composable
internal fun PspHintCard(automatic: Boolean) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(automatic) {
        if (!automatic) {
            copyToClipboard(context, "Emufii", HOST_SENTINEL)
            copied = true
        }
    }
    SoftCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionHeader(stringResource(R.string.hint_psp_title))
            Text(
                stringResource(
                    if (automatic) R.string.hint_psp_automated
                    else R.string.hint_psp_body
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (automatic) {
                Text(
                    stringResource(R.string.hint_psp_automatic_ready),
                    style = MaterialTheme.typography.bodySmall,
                    color = good()
                )
                Text(
                    stringResource(R.string.hint_psp_step4),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    HOST_SENTINEL,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = coralText()
                )
                for (step in listOf(
                    stringResource(R.string.hint_psp_step1),
                    stringResource(R.string.hint_psp_step2, HOST_SENTINEL),
                    stringResource(R.string.hint_psp_step2b),
                    stringResource(R.string.hint_psp_step3),
                    stringResource(R.string.hint_psp_step4)
                )) {
                    Text("· $step", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    stringResource(R.string.hint_psp_relay_why),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.hint_psp_why),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (copied) {
                    Text(
                        stringResource(R.string.hint_psp_copied),
                        style = MaterialTheme.typography.bodySmall,
                        color = good()
                    )
                }
                GhostButton(
                    label = stringResource(R.string.hint_psp_copy),
                    onClick = { copyToClipboard(context, "Emufii", HOST_SENTINEL) }
                )
            }
            Text(
                stringResource(R.string.hint_psp_exit_before_switch),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            // Neither an ImportantNote, reserved for what stops you playing, nor the grey
            // advisory voice.
            // pourquoi : docs/decisions/session.md § This screen's drawing decisions
            SectionHeader(stringResource(R.string.hint_psp_wifi_title))
            Text(
                stringResource(R.string.hint_psp_wifi),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun WfcNotASessionCard() {
    SoftCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader(stringResource(R.string.hint_wfc_title))
            Text(
                stringResource(R.string.hint_wfc_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun UnsupportedHintCard(consoleLabel: String?) {
    SoftCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader(consoleLabel ?: stringResource(R.string.hint_unknown_console))
            Text(
                stringResource(R.string.hint_unsupported_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

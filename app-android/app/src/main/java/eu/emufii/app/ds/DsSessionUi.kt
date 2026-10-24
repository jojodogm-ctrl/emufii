package eu.emufii.app.ds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.emufii.app.R
import eu.emufii.app.library.Console
import eu.emufii.app.library.Rom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private val identityReaders = Semaphore(2)

@Composable
fun DsTierBadge(rom: Rom, modifier: Modifier = Modifier) {
    if (rom.console != Console.DS) return
    val context = LocalContext.current
    var tier by remember(rom.uri) { mutableStateOf<DsTier?>(null) }
    var unreadable by remember(rom.uri) { mutableStateOf(false) }
    LaunchedEffect(rom.uri) {
        try { tier = identityReaders.withPermit { DsCatalog.assess(context, rom.uri).tier } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { unreadable = true }
    }
    Text(stringResource(when {
        unreadable -> R.string.ds_unreadable
        tier == DsTier.GOLD -> R.string.ds_gold
        tier == DsTier.SILVER -> R.string.ds_silver
        else -> R.string.ds_checking
    }), modifier = modifier, style = MaterialTheme.typography.labelMedium,
        color = when (tier) {
            DsTier.GOLD -> Color(0xffb88400)
            DsTier.SILVER -> MaterialTheme.colorScheme.onSurfaceVariant
            null -> MaterialTheme.colorScheme.onSurfaceVariant
        })
}

@Composable
fun DsCreateDialog(rom: Rom, private: Boolean, creating: Boolean, error: String?,
    onDismiss: () -> Unit, onCreate: (DsGameIdentity, Int) -> Unit) {
    val context = LocalContext.current
    var identity by remember(rom.uri) { mutableStateOf<DsGameIdentity?>(null) }
    var readError by remember(rom.uri) { mutableStateOf<String?>(null) }
    var players by remember(rom.uri) { mutableStateOf(2) }
    LaunchedEffect(rom.uri) {
        try { identity = identityReaders.withPermit { DsCatalog.assess(context, rom.uri) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { readError = context.getString(R.string.ds_unreadable) }
    }
    AlertDialog(onDismissRequest = { if (!creating) onDismiss() }, title = { Text(rom.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val game = identity
                if (game == null) Text(readError ?: stringResource(R.string.ds_checking))
                else {
                    Text(stringResource(if (game.tier == DsTier.GOLD) R.string.ds_gold_detail else R.string.ds_silver_detail))
                    Text(stringResource(R.string.ds_players_fixed))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        game.allowedPlayers.forEach { n ->
                            FilterChip(selected = players == n, onClick = { players = n },
                                label = { Text(n.toString()) }, enabled = !creating)
                        }
                    }
                    Text(stringResource(if (private) R.string.ds_private else R.string.ds_public))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = {
            TextButton(onClick = { identity?.let { onCreate(it, players) } },
                enabled = identity?.allowedPlayers?.contains(players) == true && !creating) {
                Text(stringResource(if (creating) R.string.ds_creating else R.string.lib_create_session))
            }
        }, dismissButton = { TextButton(onClick = onDismiss, enabled = !creating) { Text(stringResource(R.string.ds_cancel)) } })
}

@Composable
fun DsSessionScreen(session: DsSessionView, leaving: Boolean, onLaunch: () -> Unit, onLeave: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(session.code, session.host, session.title) {
        eu.emufii.app.secondscreen.SecondScreen.publish(eu.emufii.app.secondscreen.SecondScreenModel.InSession(
            session.code, if (session.host) eu.emufii.app.session.Session.Role.HOST else eu.emufii.app.session.Session.Role.GUEST,
            Console.DS, session.title))
        onDispose { eu.emufii.app.secondscreen.SecondScreen.publish(eu.emufii.app.secondscreen.SecondScreenModel.Idle) }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
        Text(session.title, style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(if (session.spec.mode == DsTier.GOLD) R.string.ds_gold else R.string.ds_silver))
        Text(session.code, style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.ds_session_players, session.members.size, session.spec.players))
        Text(stringResource(if (session.spec.mode == DsTier.GOLD) R.string.ds_gold_detail else R.string.ds_silver_detail))
        if (session.spec.mode == DsTier.SILVER) Text(stringResource(R.string.ds_same_rom))
        OutlinedButton(onClick = {
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Emufii", session.code))
        }) { Text(stringResource(R.string.ds_copy_code)) }
        session.members.forEach { Text(it.name) }
        session.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Button(onClick = onLaunch, enabled = !leaving && !session.launching && !session.runtimeActive && session.error == null,
            modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (session.launching) R.string.ds_starting else R.string.ds_play))
        }
        OutlinedButton(onClick = onLeave, enabled = !leaving && !session.launching, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (leaving) R.string.ds_leaving else R.string.ds_leave))
        }
    }
}

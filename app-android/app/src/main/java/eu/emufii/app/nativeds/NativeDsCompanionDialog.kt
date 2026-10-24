package eu.emufii.app.nativeds

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.emufii.app.network.CoordinatorClient
import kotlinx.coroutines.delay

/** Read-only companion. Only GET is used: the phone acquires no native seat,
 * mirror credential, tunnel, runtime lease or emulator launch permission. */
@Composable
fun NativeDsCompanionDialog(client: CoordinatorClient, initial: NativeDsSelection, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var shown by remember(initial.emufiiCode) { mutableStateOf(initial) }
    var error by remember(initial.emufiiCode) { mutableStateOf<String?>(null) }
    LaunchedEffect(initial.emufiiCode) {
        while (true) {
            client.getSession(initial.emufiiCode).fold(onSuccess = { remote ->
                val native = remote.native
                if (native == null || !initial.room.sameDescriptor(native)) error = "Ce salon a changé. Recherche-le à nouveau."
                else { shown = NativeDsSelection(remote.code, remote.hostName ?: "Hôte", native); error = null }
            }, onFailure = { error = "Le salon est fermé ou momentanément indisponible." })
            delay(4000)
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Salon DSi · Mario vs Luigi DS") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(shown.hostName, style = MaterialTheme.typography.titleMedium)
                Text("${shown.room.phase.label} · ${shown.room.players}/${shown.room.maxPlayers} joueurs")
                Text(shown.room.roomCode, style = MaterialTheme.typography.headlineLarge)
                Text("Code Emufii : ${shown.emufiiCode}")
                Text("Sur ta DSi avec DSPico, ouvre Mario vs Luigi DS, puis En ligne → Rejoindre et entre ce code à 4 lettres.")
                Text("Le téléphone accompagne la partie pour consulter et partager le salon.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Column {
                TextButton(enabled = error == null, onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("Code du salon DSi", shown.room.roomCode))
                }) { Text("Copier le code DSi") }
                TextButton(enabled = error == null, onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shown.shareText()) }
                    context.startActivity(Intent.createChooser(intent, "Partager le salon DSi"))
                }) { Text("Partager") }
            }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } })
}

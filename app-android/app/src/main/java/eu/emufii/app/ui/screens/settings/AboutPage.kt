package eu.emufii.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.emufii.app.BuildConfig
import eu.emufii.app.R
import eu.emufii.app.ui.components.DetailActions
import eu.emufii.app.ui.components.DetailNote
import eu.emufii.app.ui.components.DetailTone
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.PrimaryButton
import eu.emufii.app.ui.components.padEntry

@Composable
internal fun AboutPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val open = { url: String ->
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        Unit
    }

    SettingsPage(
        title = stringResource(R.string.settings_page_about),
        onBack = onBack,
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth >= 700.dp) {
                // Measure both cards and impose the taller; IntrinsicSize.Min would pick the shorter.
                var leftHeight by remember { mutableIntStateOf(0) }
                var rightHeight by remember { mutableIntStateOf(0) }
                val density = LocalDensity.current
                val floor = with(density) { maxOf(leftHeight, rightHeight).toDp() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .onSizeChanged { leftHeight = it.height }
                    ) { IdentityBlock(modifier = Modifier.heightIn(min = floor)) }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .onSizeChanged { rightHeight = it.height }
                    ) { JoinBlock(open = open, modifier = Modifier.heightIn(min = floor)) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    IdentityBlock()
                    JoinBlock(open = open)
                }
            }
        }
    }
}

@Composable
private fun IdentityBlock(modifier: Modifier = Modifier) {
    SettingsBlock(
        title = stringResource(R.string.app_name),
        modifier = modifier,
        state = BlockState(DetailTone.GOOD, BuildConfig.VERSION_NAME)
    ) {
        DetailNote(stringResource(R.string.settings_about_body))
        BlockFact(
            stringResource(R.string.settings_about_fact_build),
            BuildConfig.VERSION_CODE.toString()
        )
        BlockFact(
            stringResource(R.string.settings_about_fact_licence),
            stringResource(R.string.settings_about_licence_value)
        )
    }
}

@Composable
private fun JoinBlock(open: (String) -> Unit, modifier: Modifier = Modifier) {
    SettingsBlock(
        title = stringResource(R.string.settings_about_join),
        modifier = modifier,
        spread = true,
        footer = {
            DetailActions {
                PrimaryButton(
                    label = stringResource(R.string.settings_about_discord),
                    onClick = { open(DISCORD_URL) },
                    modifier = Modifier.padEntry().fillMaxWidth(),
                    leading = { BrandMark(R.drawable.ic_discord) }
                )
                GhostButton(
                    label = stringResource(R.string.settings_about_kofi),
                    onClick = { open(KOFI_URL) },
                    fillWidth = true,
                    leading = { BrandMark(R.drawable.ic_kofi) }
                )
            }
        }
    ) {
    }
}

private const val DISCORD_URL = "https://discord.gg/tvWcb28vBZ"

private const val KOFI_URL = "https://ko-fi.com/emufii"

@Composable
private fun BrandMark(res: Int) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = Modifier.size(20.dp)
    )
}

package eu.emufii.app.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import eu.emufii.app.R
import eu.emufii.app.notify.Notifications
import eu.emufii.app.secondscreen.rememberPresentationDisplay
import eu.emufii.app.settings.AppLanguage
import eu.emufii.app.settings.SettingsStore
import eu.emufii.app.ui.components.DetailActions
import eu.emufii.app.ui.components.DetailNote
import eu.emufii.app.ui.components.DetailTone
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.SwitchRow

@Composable
internal fun GeneralPage(
    settingsStore: SettingsStore,
    language: AppLanguage,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val notifyFriends by settingsStore.notifyFriends.collectAsStateWithLifecycle()
    val secondScreenOn by settingsStore.secondScreen.collectAsStateWithLifecycle()

    SettingsPage(
        title = stringResource(R.string.settings_page_general),
        onBack = onBack,
        modifier = modifier
    ) {
        SettingsColumns(
            {
                SettingsBlock(
                    title = stringResource(R.string.settings_language),
                    state = BlockState(DetailTone.GOOD, stringResource(language.labelRes))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppLanguage.entries.forEachIndexed { index, option ->
                        ChoiceRow(
                            label = stringResource(option.labelRes),
                            selected = option == language,
                            onClick = { settingsStore.setLanguage(option) },
                            entry = index == 0
                        )
                    }
                    }
                }
            },
            {
                NotificationsBlock(
                    friends = notifyFriends,
                    onSetFriends = settingsStore::setNotifyFriends,
                )
            },
            {
                SecondScreenBlock(
                    enabled = secondScreenOn,
                    onSetEnabled = settingsStore::setSecondScreen,
                )
            },
        )
    }
}

@Composable
private fun NotificationsBlock(
    friends: Boolean,
    onSetFriends: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    // Asked at composition, not cached: the player may just have granted it in Android settings.
    val allowed = Notifications.allowed(context)

    SettingsBlock(
        title = stringResource(R.string.settings_notifications),
        state = BlockState(
            when {
                !allowed -> DetailTone.WARN
                friends -> DetailTone.GOOD
                else -> DetailTone.BUSY
            },
            stringResource(
                when {
                    !allowed -> R.string.settings_notify_blocked
                    friends -> R.string.settings_value_autofill_on
                    else -> R.string.settings_notify_silent
                }
            )
        )
    ) {
        SwitchRow(
            label = stringResource(R.string.settings_notify_friends),
            checked = friends,
            onCheckedChange = onSetFriends
        )
        DetailNote(stringResource(R.string.settings_notifications_note))
        if (!allowed) {
            BlockNotice(stringResource(R.string.settings_notify_blocked))
            DetailActions {
                GhostButton(
                    label = stringResource(R.string.settings_notify_open_android),
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        }
                    },
                    fillWidth = true
                )
            }
        }
    }
}

@Composable
private fun SecondScreenBlock(
    enabled: Boolean,
    onSetEnabled: (Boolean) -> Unit,
) {
    val display by rememberPresentationDisplay()
    val panel = display
    SettingsBlock(
        title = stringResource(R.string.settings_second_screen),
        state = BlockState(
            when {
                panel == null -> DetailTone.WARN
                enabled -> DetailTone.GOOD
                else -> DetailTone.BUSY
            },
            stringResource(
                if (enabled) R.string.settings_value_autofill_on
                else R.string.settings_value_autofill_off
            )
        )
    ) {
        SwitchRow(
            label = stringResource(R.string.settings_second_screen_switch),
            checked = enabled,
            onCheckedChange = onSetEnabled,
            note = panel?.name ?: stringResource(R.string.settings_second_screen_absent)
        )
        DetailNote(stringResource(R.string.settings_second_screen_note))
    }
}

internal val AppLanguage.labelRes: Int
    get() = when (this) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.FRENCH -> R.string.settings_language_fr
        AppLanguage.ENGLISH -> R.string.settings_language_en
    }

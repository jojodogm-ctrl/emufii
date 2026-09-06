package eu.emufii.app.ui.screens.session

import androidx.compose.runtime.Composable
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.ErrorDark
import eu.emufii.app.ui.theme.ErrorLight
import eu.emufii.app.ui.theme.GoodDark
import eu.emufii.app.ui.theme.GoodLight
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme

@Composable
internal fun danger() = if (LocalEmufiiDarkTheme.current) ErrorDark else ErrorLight

@Composable
internal fun good() = if (LocalEmufiiDarkTheme.current) GoodDark else GoodLight

@Composable
internal fun coralText() = if (LocalEmufiiDarkTheme.current) Coral.darkBright else Coral.ink

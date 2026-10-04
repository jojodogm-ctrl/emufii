package eu.emufii.app.ui.screens.library

import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalLibraryExitBottom = staticCompositionLocalOf<() -> Boolean> { { false } }

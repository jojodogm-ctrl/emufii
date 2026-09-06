package eu.emufii.app.ui.screens.library

/**
 * Layouts without columns keep [RIGHT], where the app leads.
 * pourquoi : docs/decisions/bibliotheque.md § Leaving through the top is named, and depends on the column
 */
internal enum class HeaderSide { LEFT, RIGHT }

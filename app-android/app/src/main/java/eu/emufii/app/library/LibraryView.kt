package eu.emufii.app.library

/**
 * How the library lays itself out.
 *
 * Three shapes, each answering a way of searching: the grid shows many games at
 * once, the carousel one at a time but large, the list spells the titles out
 * when an icon no longer separates two versions of the same game.
 *
 * [GRID] stays the default: it is the design target, and the only one that holds
 * up with ten games as well as with two hundred.
 */
enum class LibraryLayout { GRID, CAROUSEL, LIST }

/**
 * The order games appear in.
 *
 * [CONSOLE] files rather than sorts: the library shows one folder per console
 * and the games live inside. That is two-level navigation, so the screen has to
 * hold an open folder on top of a cursor.
 */
enum class LibrarySort { NAME, RECENT, CONSOLE }

/**
 * The requested order, applied.
 *
 * Sorting by name ignores case and console, so two games named alike on two
 * machines end up next to each other.
 *
 * Sorting by date descends and breaks ties by name. Without that second key, a
 * library copied in one go (everyone's case on first run) carries the same date
 * throughout, and the order would follow the directory walk.
 *
 * [LibrarySort.CONSOLE] returns a folder's internal order: grouping by console
 * is the screen's business.
 */
fun List<Rom>.sortedFor(sort: LibrarySort): List<Rom> = when (sort) {
    LibrarySort.NAME -> sortedBy { it.displayName.lowercase() }
    LibrarySort.RECENT -> sortedWith(
        compareByDescending<Rom> { it.addedAt }.thenBy { it.displayName.lowercase() }
    )
    LibrarySort.CONSOLE -> sortedBy { it.displayName.lowercase() }
}

/**
 * The console folders, in enum order. A console with no game gets no folder: it
 * would take a tile without teaching anything.
 */
fun List<Rom>.byConsole(): List<Pair<Console, List<Rom>>> =
    groupBy { it.console }
        .toList()
        .sortedBy { (console, _) -> console.ordinal }
        .map { (console, roms) -> console to roms.sortedBy { it.displayName.lowercase() } }

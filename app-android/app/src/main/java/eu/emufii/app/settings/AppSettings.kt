package eu.emufii.app.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.core.content.edit
import eu.emufii.app.library.Console
import eu.emufii.app.library.LibraryLayout
import eu.emufii.app.library.LibrarySort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which language the app speaks.
 *
 * [SYSTEM] means "whatever the phone is set to", which is the right default:
 * an app that ignores the system language is an app that argues with its user.
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    FRENCH("fr"),
    ENGLISH("en");

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

/**
 * Light or dark, or whatever the phone says.
 *
 * [SYSTEM] stays the default for the same reason as the language: an app that
 * ignores the phone's setting argues with its user. The others exist because
 * the phone's setting is often a schedule, and someone reading in bed should not
 * have to change it device-wide to get a dark game library.
 *
 * [OLED] is a dark, not a third universe. The handheld screens we target are
 * OLED: a black pixel there is a pixel switched off, so the dark theme's bluish
 * background draws power where it could cost nothing, and leaves a grey halo in
 * the dark. It changes only the background and the cards' fill; everything that
 * reads [isDark] goes on seeing dark, which avoids rejudging 44 components for a
 * setting that only talks about brightness.
 */
enum class AppTheme {
    SYSTEM, LIGHT, DARK, OLED;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
        OLED -> true
    }

    /** True for [OLED] only: pure black, instead of midnight blue. */
    val isOled: Boolean get() = this == OLED

    companion object {
        fun fromName(name: String?): AppTheme =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * Which colour the accent is.
 *
 * The world's rule is that there is exactly *one* accent, spent on the cursor
 * and on the main action, and that every other colour on screen comes from the
 * box art. This setting does not touch that rule: it says which hue plays the
 * part, not how many are on screen at once.
 *
 * [SYSTEM] follows the colour Android extracted from the wallpaper, the way the
 * language and the theme follow the phone. It is not the default, though, and
 * that is the one place this setting departs from the other two: the cursor's
 * cyan is the app's own signature, and a handheld menu that changes identity
 * with the wallpaper has no identity.
 */
enum class AppAccent {
    CYAN, SYSTEM, AMBER, VIOLET, ROSE;

    companion object {
        fun fromName(name: String?): AppAccent =
            entries.firstOrNull { it.name == name } ?: CYAN
    }
}

/**
 * App-wide preferences. Small on purpose, a settings screen full of switches
 * nobody asked for is worse than no settings screen.
 *
 * The language is applied through the platform's per-app language API rather
 * than by juggling a `Configuration` ourselves. minSdk is 33, so it is simply
 * there: Android remembers the choice across launches, shows it in the system
 * app settings alongside every other app, and recreates the activity so the new
 * strings take effect immediately.
 */
class SettingsStore private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(readLanguage())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _theme = MutableStateFlow(AppTheme.fromName(prefs.getString(KEY_THEME, null)))
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    private val _accent = MutableStateFlow(AppAccent.fromName(prefs.getString(KEY_ACCENT, null)))
    val accent: StateFlow<AppAccent> = _accent.asStateFlow()

    /**
     * The player's SteamGridDB key, which unlocks high-resolution game icons.
     * Empty until they have given one: the library then keeps the ROMs' icons,
     * which are 32 or 48 pixels a side.
     *
     * Every player brings their own, and that is the point of this setting. A key
     * frozen into the APK would be the same for everybody: extractable by opening
     * the package, and it would be the author's account carrying the quota and
     * the abuse of the entire installed base.
     */
    private val _steamGridDbKey = MutableStateFlow(prefs.getString(KEY_SGDB, "").orEmpty())
    val steamGridDbKey: StateFlow<String> = _steamGridDbKey.asStateFlow()

    /**
     * The library's layout and order.
     *
     * Kept here rather than in the screen: this is a choice made once and
     * expected to still be there, and losing it on every return from a session
     * screen would be taken for a bug. An unknown value, a setting written by a
     * newer version and then downgraded, falls back to the default instead of
     * bringing the launch down.
     */
    private val _libraryLayout = MutableStateFlow(
        LibraryLayout.entries.firstOrNull { it.name == prefs.getString(KEY_LAYOUT, null) }
            ?: LibraryLayout.GRID
    )
    val libraryLayout: StateFlow<LibraryLayout> = _libraryLayout.asStateFlow()

    fun setLibraryLayout(layout: LibraryLayout) {
        prefs.edit { putString(KEY_LAYOUT, layout.name) }
        _libraryLayout.value = layout
    }

    private val _librarySort = MutableStateFlow(
        LibrarySort.entries.firstOrNull { it.name == prefs.getString(KEY_SORT, null) }
            ?: LibrarySort.NAME
    )
    val librarySort: StateFlow<LibrarySort> = _librarySort.asStateFlow()

    fun setLibrarySort(sort: LibrarySort) {
        prefs.edit { putString(KEY_SORT, sort.name) }
        _librarySort.value = sort
    }

    /**
     * The consoles the player asked *not* to see in the library.
     *
     * Stored as what is hidden, never as what is shown, and that is the load
     * bearing choice. A library holding only 3DS dumps would, under the other
     * shape, have five consoles ticked off at install time and would silently
     * hide a console added in a later version: the stored set would simply not
     * mention it. Recording refusals means anything new arrives visible, which
     * is the only default that cannot lose a game.
     *
     * A name that no enum answers to is dropped on read. That happens after a
     * downgrade, or if a console is ever retired, and the game reappearing is a
     * far better failure than a grid quietly missing a machine.
     */
    private val _hiddenConsoles = MutableStateFlow(readHiddenConsoles())
    val hiddenConsoles: StateFlow<Set<Console>> = _hiddenConsoles.asStateFlow()

    private fun readHiddenConsoles(): Set<Console> =
        prefs.getStringSet(KEY_HIDDEN_CONSOLES, null)
            .orEmpty()
            .mapNotNull { name -> Console.entries.firstOrNull { it.name == name } }
            .toSet()

    fun setConsoleVisible(console: Console, visible: Boolean) {
        val next = if (visible) _hiddenConsoles.value - console else _hiddenConsoles.value + console
        // A copy, because SharedPreferences hands back the very set it holds and
        // documents that mutating it is undefined. The bug it produces is the
        // quiet kind: it survives until the process dies.
        prefs.edit { putStringSet(KEY_HIDDEN_CONSOLES, next.map { it.name }.toSet()) }
        _hiddenConsoles.value = next
    }

    fun setSteamGridDbKey(key: String) {
        val cleaned = key.trim()
        prefs.edit { putString(KEY_SGDB, cleaned) }
        _steamGridDbKey.value = cleaned
    }

    /**
     * Unlike the language, no platform API owns this, Android has no per-app
     * dark mode below API 31's `setApplicationNightMode`, and even that only
     * covers the two forced values. So the choice lives here and the theme reads
     * it, which also means switching is instant instead of recreating the
     * activity the way a language change does.
     */
    fun setTheme(theme: AppTheme) {
        prefs.edit { putString(KEY_THEME, theme.name) }
        _theme.value = theme
    }

    /** Read by the theme, like [setTheme], so the change is instant. */
    fun setAccent(accent: AppAccent) {
        prefs.edit { putString(KEY_ACCENT, accent.name) }
        _accent.value = accent
    }

    private fun readLanguage(): AppLanguage {
        // The platform is the source of truth once a choice has been made, so a
        // change from Android's own settings screen is reflected here too.
        val fromSystem = localeManager()?.applicationLocales
            ?.takeIf { !it.isEmpty }
            ?.get(0)
            ?.language
        return AppLanguage.fromTag(fromSystem ?: prefs.getString(KEY_LANGUAGE, null))
    }

    /**
     * Whether the first-run walkthrough has been completed. Kept here rather
     * than in the library store because it is about the app's state, not the
     * ROM folder's, a user who clears their folder should not be walked through
     * onboarding again.
     */
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING_DONE, value) }

    fun setLanguage(language: AppLanguage) {
        prefs.edit { putString(KEY_LANGUAGE, language.tag) }
        _language.value = language
        localeManager()?.applicationLocales = language.tag
            ?.let { LocaleList.forLanguageTags(it) }
            ?: LocaleList.getEmptyLocaleList()
    }

    private fun localeManager(): LocaleManager? =
        appContext.getSystemService(LocaleManager::class.java)

    companion object {
        /**
         * The one store for the process, because every flow here is held in
         * memory and a second instance would not see the first one's writes.
         *
         * Built per screen until 2026-08-19, and the bug that came of it is the
         * quiet kind: consoles switched off during the onboarding came back the
         * moment the library appeared. The library had built its own store while
         * the onboarding was still up, seeded it from disk before anything was
         * written, and nothing ever told it otherwise. On disk the choice was
         * right the whole time, so it survived a restart, which is what makes
         * this sort of thing read as random.
         *
         * SharedPreferences is already process-wide and thread-safe; what is not
         * shareable is the `StateFlow` in front of it. So there is one.
         */
        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }

        private const val PREFS = "emufii_settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_THEME = "theme"
        private const val KEY_ACCENT = "accent"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_SGDB = "steamgriddb_key"
        private const val KEY_LAYOUT = "library_layout"
        private const val KEY_SORT = "library_sort"
        private const val KEY_HIDDEN_CONSOLES = "hidden_consoles"
    }
}

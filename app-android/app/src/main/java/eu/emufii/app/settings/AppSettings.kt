package eu.emufii.app.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.core.content.edit
import eu.emufii.app.artwork.ArtworkFrontend
import eu.emufii.app.library.Console
import eu.emufii.app.library.LibraryLayout
import eu.emufii.app.library.LibrarySort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * pourquoi : docs/decisions/reglages-et-consoles.md § Following the phone is the right default, except for the accent
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
 * [OLED] is a *dark*, not a third universe: everything reading [isDark] sees dark.
 * pourquoi : docs/decisions/reglages-et-consoles.md § OLED is a dark, not a third universe
 */
enum class AppTheme {
    SYSTEM, LIGHT, DARK, OLED;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
        OLED -> true
    }

    val isOled: Boolean get() = this == OLED

    companion object {
        fun fromName(name: String?): AppTheme =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * The language goes through the platform's per-app API, never a hand-juggled
 * `Configuration`.
 * pourquoi : docs/decisions/reglages-et-consoles.md § Language goes through the platform, the theme cannot
 */
class SettingsStore private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(readLanguage())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _theme = MutableStateFlow(AppTheme.fromName(prefs.getString(KEY_THEME, null)))
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    /**
     * A key frozen into the APK is extractable and carries the whole fleet's quota.
     * pourquoi : docs/decisions/reglages-et-consoles.md § Every player brings their own key
     */
    private val _steamGridDbKey = MutableStateFlow(prefs.getString(KEY_SGDB, "").orEmpty())
    val steamGridDbKey: StateFlow<String> = _steamGridDbKey.asStateFlow()

    /**
     * A frontend's folder serves the library with no key and no network.
     * pourquoi : docs/decisions/reglages-et-consoles.md § Every player brings their own key
     */
    private val _frontendFolder = MutableStateFlow(prefs.getString(KEY_FRONTEND_FOLDER, "").orEmpty())
    val frontendFolder: StateFlow<String> = _frontendFolder.asStateFlow()

    fun setFrontendFolder(uri: String) {
        _frontendFolder.value = uri
        prefs.edit { putString(KEY_FRONTEND_FOLDER, uri) }
    }

    /**
     * Which frontend's layout the folder is read with. Cocoon by default: it was the only
     * one before, and a folder linked back then is a Cocoon folder.
     */
    private val _artworkFrontend = MutableStateFlow(
        ArtworkFrontend.fromName(prefs.getString(KEY_FRONTEND, null))
    )
    val artworkFrontend: StateFlow<ArtworkFrontend> = _artworkFrontend.asStateFlow()

    fun setArtworkFrontend(frontend: ArtworkFrontend) {
        prefs.edit { putString(KEY_FRONTEND, frontend.name) }
        _artworkFrontend.value = frontend
    }

    /**
     * An unknown stored value falls back to the default instead of failing the launch.
     * pourquoi : docs/decisions/reglages-et-consoles.md § What is stored is what was refused
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
     * Stored as what is hidden, never as what is shown: the only default that cannot
     * lose a game.
     * pourquoi : docs/decisions/reglages-et-consoles.md § What is stored is what was refused
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
        // SharedPreferences hands back the very set it holds and documents mutating it
        // as undefined; hence a copy.
        prefs.edit { putStringSet(KEY_HIDDEN_CONSOLES, next.map { it.name }.toSet()) }
        _hiddenConsoles.value = next
    }

    /**
     * pourquoi : docs/decisions/reglages-et-consoles.md § The "on" defaults, and why they are switches all the same
     */
    private val _secondScreen = MutableStateFlow(prefs.getBoolean(KEY_SECOND_SCREEN, true))
    val secondScreen: StateFlow<Boolean> = _secondScreen.asStateFlow()

    fun setSecondScreen(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SECOND_SCREEN, enabled) }
        _secondScreen.value = enabled
    }

    /**
     * pourquoi : docs/decisions/reglages-et-consoles.md § The "on" defaults, and why they are switches all the same
     */
    private val _notifyFriends = MutableStateFlow(prefs.getBoolean(KEY_NOTIFY_FRIENDS, true))
    val notifyFriends: StateFlow<Boolean> = _notifyFriends.asStateFlow()

    fun setNotifyFriends(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_NOTIFY_FRIENDS, enabled) }
        _notifyFriends.value = enabled
    }

    /**
     * On by default: Emufii is sideloaded and no store speaks for it.
     * pourquoi : docs/decisions/reglages-et-consoles.md § The "on" defaults, and why they are switches all the same
     */
    private val _notifyUpdates = MutableStateFlow(prefs.getBoolean(KEY_NOTIFY_UPDATES, true))
    val notifyUpdates: StateFlow<Boolean> = _notifyUpdates.asStateFlow()

    fun setNotifyUpdates(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_NOTIFY_UPDATES, enabled) }
        _notifyUpdates.value = enabled
    }

    fun setSteamGridDbKey(key: String) {
        val cleaned = key.trim()
        prefs.edit { putString(KEY_SGDB, cleaned) }
        _steamGridDbKey.value = cleaned
    }

    /**
     * No platform API owns the theme, so the choice lives here and the theme reads it.
     * pourquoi : docs/decisions/reglages-et-consoles.md § Language goes through the platform, the theme cannot
     */
    fun setTheme(theme: AppTheme) {
        prefs.edit { putString(KEY_THEME, theme.name) }
        _theme.value = theme
    }

    private fun readLanguage(): AppLanguage {
        // The platform is the source of truth once a choice has been made: a change from
        // Android's own settings screen is reflected here.
        val fromSystem = localeManager()?.applicationLocales
            ?.takeIf { !it.isEmpty }
            ?.get(0)
            ?.language
        return AppLanguage.fromTag(fromSystem ?: prefs.getString(KEY_LANGUAGE, null))
    }

    /** Here and not in the library store: clearing a ROM folder must not replay onboarding. */
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
         * `SharedPreferences` is already shared, the `StateFlow` in front of it is not:
         * building one store per screen made onboarding choices silently revert.
         * pourquoi : docs/decisions/reglages-et-consoles.md § One store for the process
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
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_SGDB = "steamgriddb_key"
        /** Kept under its old name: a folder linked before ES-DE existed here must survive. */
        private const val KEY_FRONTEND_FOLDER = "cocoon_folder"
        private const val KEY_FRONTEND = "artwork_frontend"
        private const val KEY_LAYOUT = "library_layout"
        private const val KEY_SORT = "library_sort"
        private const val KEY_HIDDEN_CONSOLES = "hidden_consoles"
        private const val KEY_SECOND_SCREEN = "second_screen"
        private const val KEY_NOTIFY_FRIENDS = "notify_friends"
        private const val KEY_NOTIFY_UPDATES = "notify_updates"
    }
}

package eu.emufii.app.netplay

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * Reads a label out of the *emulator's own* resources.
 *
 * Everything else in this package matches on view ids, for the reason spelled
 * out in [NetplayTarget]: labels differ per locale. One screen leaves no
 * choice, Azahar's home settings list is a RecyclerView whose rows all share
 * the same ids (`option_card`, `option_title`), so the only thing that tells the
 * Multiplayer row from "System Files" is its text.
 *
 * Matching a hardcoded "Multiplayer" would break for every player not running in
 * English, so the text comes from Azahar itself: `getResourcesForApplication`
 * gives us its string table, and `multiplayer` resolves to whatever that build
 * shows on the device's locale. Same trick as [NetplayUiSupport], same
 * requirement, the package must be visible in `<queries>`, which it is.
 *
 * Returns null when the package or the string is missing; callers treat that as
 * "can't identify the row" and stop, rather than clicking something arbitrary.
 */
object NetplayLabels {

    /** The emulator's own name for the multiplayer entry, in the current locale. */
    const val MULTIPLAYER = "multiplayer"

    /**
     * Every string a settings row might be labelled with, most specific first.
     *
     * One name was not enough: the hub shows a title and a description, and
     * which of the two carries the word upstream calls `multiplayer` differs
     * between builds.
     */
    val MULTIPLAYER_STRINGS = listOf(MULTIPLAYER, "multiplayer_description")

    /**
     * Every translation the emulator ships for this string.
     *
     * Not "the right one", all of them: the language a third-party app displays
     * is its own, set per application since Android 13, and no public API allows
     * it to be read. The measurement that settled it: on the Thor the system
     * announces `[en, fr_FR]` and Azahar nonetheless displays "Multijoueur".
     * Looking for "the" translation therefore amounts to a bet.
     *
     * We no longer bet. The question asked is "is this row title one of the ways
     * this emulator writes *multiplayer*?", and it is answered by resolving the
     * same resource in every plausible language. Some thirty string reads, once
     * per screen: nothing.
     *
     * The original flaw was invisible because Emufii and Azahar were both in
     * French. The rename gave Emufii a fresh identity, hence English, and the
     * accidental agreement ended.
     */
    fun of(context: Context, pkg: String, name: String): List<String> {
        val res = runCatching {
            context.packageManager.getResourcesForApplication(pkg)
        }.getOrNull() ?: return emptyList()
        val id = runCatching { res.getIdentifier(name, "string", pkg) }.getOrDefault(0)
        if (id == 0) return emptyList()

        val out = LinkedHashSet<String>()
        runCatching { res.getString(id) }.getOrNull()?.let { out += it }
        for (tag in CANDIDATE_LANGUAGES) {
            runCatching {
                val cfg = Configuration(res.configuration)
                cfg.setLocale(Locale.forLanguageTag(tag))
                @Suppress("DEPRECATION")
                Resources(res.assets, res.displayMetrics, cfg).getString(id)
            }.getOrNull()?.let { out += it }
        }
        return out.toList()
    }

    /**
     * The languages these emulators are translated into.
     *
     * A deliberately closed list: a language missing from it only breaks the
     * automatic opening, never the form filling; the player opens multiplayer
     * themselves and everything else works. One language too many costs a single
     * string read.
     */
    private val CANDIDATE_LANGUAGES = listOf(
        "en", "fr", "de", "es", "it", "pt", "nl", "pl", "ru", "tr",
        "ja", "ko", "zh", "ar", "cs", "da", "fi", "hu", "id", "nb",
        "ro", "sv", "uk", "vi", "el", "he", "th", "ca", "sr", "hr"
    )

}

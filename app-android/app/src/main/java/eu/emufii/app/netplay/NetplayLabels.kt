package eu.emufii.app.netplay

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

// Azahar's settings rows share view ids, so they're matched by text.
object NetplayLabels {

    const val MULTIPLAYER = "multiplayer"

    const val UPDATE_AVAILABLE = "update_available"

    val MULTIPLAYER_STRINGS = listOf(MULTIPLAYER, "multiplayer_description")

    /** All translations: a per-app language (Android 13+) can't be read from outside. */
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

    private val CANDIDATE_LANGUAGES = listOf(
        "en", "fr", "de", "es", "it", "pt", "nl", "pl", "ru", "tr",
        "ja", "ko", "zh", "ar", "cs", "da", "fi", "hu", "id", "nb",
        "ro", "sv", "uk", "vi", "el", "he", "th", "ca", "sr", "hr"
    )

}

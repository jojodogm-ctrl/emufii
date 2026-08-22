package eu.emufii.app.update

import android.content.Context
import androidx.core.content.edit
import eu.emufii.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * What the server says the current version is.
 *
 * [url] and [notes] are optional: a version can be announced without a link to
 * hand out, and that is already better than silence.
 *
 * The note cannot come from the app's resources: it describes the next version,
 * which the installed app knows nothing about. So it travels in both languages,
 * and [notesFor] decides at display time.
 */
data class LatestVersion(
    val versionCode: Int,
    val versionName: String,
    val url: String?,
    val notes: String?,
    /** The same note in English, when the server publishes it. */
    val notesEn: String? = null
) {

    /**
     * The note in [locale]'s language, failing that in whichever exists.
     *
     * French stays the fallback: it is the only one the old `latest.json` files
     * carry, and a note in the wrong language beats a banner that announces a
     * version without saying what it changes.
     */
    fun notesFor(locale: java.util.Locale): String? =
        if (locale.language == "fr") notes ?: notesEn else notesEn ?: notes
}

/**
 * Telling the player a new version exists.
 *
 * This is S5 of `docs/SECURITY_REVIEW.md`, and it was the last open point: a
 * sideloaded application has no store behind it, so nothing warns anybody. A
 * flaw fixed on our side is only worth anything if it reaches the players, and
 * until now the only route was for one of them to ask for the APK again.
 *
 * Deliberately minimal, and the choice is owned: Emufii downloads nothing and
 * installs nothing. It says a version exists and shows where to get it. An app
 * that updates itself from a URL it read off the network is a code execution
 * path this project has no reason to open; the security review starts from the
 * assumption "strangers use the app", and it also holds against the day the
 * server is no longer ours.
 *
 * Silence is the default answer. Unreachable server, missing file, broken JSON:
 * we display nothing. "We do not know" must never reach the screen as "you are
 * behind".
 */
object UpdateCheck {

    /**
     * The published version, or null when the server announces none or does not
     * answer. Never fails: the caller has no use for an error here.
     */
    suspend fun fetch(baseUrl: String = BuildConfig.COORDINATOR_BASE_URL): LatestVersion? =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL("$baseUrl/latest").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                try {
                    // 204 = nothing published. Everything else outside 2xx is a
                    // failure, and a failure is silence.
                    if (conn.responseCode != 200) return@runCatching null
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    LatestVersion(
                        versionCode = json.getInt("version_code"),
                        versionName = json.optString("version_name"),
                        url = json.optString("url").takeIf { it.isNotBlank() && it != "null" },
                        notes = json.optString("notes").takeIf { it.isNotBlank() && it != "null" },
                        notesEn = json.optString("notes_en").takeIf { it.isNotBlank() && it != "null" }
                    )
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }

    /** Is there really something better than what is running here? */
    fun isNewer(latest: LatestVersion): Boolean = latest.versionCode > BuildConfig.VERSION_CODE
}

/**
 * What the player has already dismissed.
 *
 * Remembered by version number and not by a boolean: dismissing the announcement
 * for 12 must say nothing about 13. A "seen it" flag would have silenced every
 * later version, which amounts to removing the feature at the first refusal.
 */
class UpdateDismissals(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isDismissed(versionCode: Int): Boolean = prefs.getInt(KEY, 0) >= versionCode

    fun dismiss(versionCode: Int) {
        prefs.edit { putInt(KEY, versionCode) }
    }

    private companion object {
        const val PREFS = "emufii_updates"
        const val KEY = "dismissed_version_code"
    }
}

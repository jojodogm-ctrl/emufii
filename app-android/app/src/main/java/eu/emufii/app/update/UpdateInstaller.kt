package eu.emufii.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import eu.emufii.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "UpdateInstaller"

/**
 * The largest APK we agree to pull. 1.9.2 weighs 32 MB; this ceiling leaves room
 * and stops a chatty server from filling the device's cache while we look away.
 */
private const val MAX_APK_BYTES = 200L * 1024 * 1024

/** What the download and then the install can come to. */
sealed interface UpdateOutcome {
    /** The APK is validated and handed to Android: the system dialog takes over. */
    data object HandedToAndroid : UpdateOutcome

    /**
     * Android does not let Emufii install applications yet. Not an error, a
     * permission to grant once, and [settings] opens the exact screen for it.
     */
    data class NeedsPermission(val settings: Intent) : UpdateOutcome

    /** Nothing to install: the server has no binary to offer. */
    data object Unavailable : UpdateOutcome

    /** Download impossible or interrupted. */
    data object DownloadFailed : UpdateOutcome

    /**
     * The downloaded file is not an Emufii update: foreign signature, older
     * version, or unreadable APK. The one case worth saying loudly, since it is
     * exactly what the check exists to catch.
     */
    data object Rejected : UpdateOutcome
}

/**
 * Downloads the new version and hands it to Android.
 *
 * ## Why this is acceptable when S5 ruled it out
 *
 * `docs/SECURITY_REVIEW.md` (S5) had decided: the app neither downloads nor
 * installs. The reasoning rested on one thing, that updating from a URL read off
 * the network is a code execution path, and the review assumes the network is
 * not trusted. This file reopens the path and closes it again with three locks,
 * in this order:
 *
 * 1. The URL is not followed as given. Only the coordinator's host is accepted,
 *    over HTTPS. A `url` pointing elsewhere in `latest.json` is ignored, so
 *    compromising the JSON is not enough to download an arbitrary binary, one
 *    would already have to hold the server.
 * 2. The signature decides, not the provenance. The downloaded APK is opened
 *    here and its certificate compared against the running application's. A
 *    binary signed with another key is discarded without ever being shown to
 *    Android. This is the lock that still holds "the day the server is no longer
 *    ours", the assumption the review stated explicitly.
 * 3. Nothing starts without a tap. The download begins when the player presses
 *    "Install", never on its own.
 *
 * On that third point, measured on the Thor and contrary to expectation: Android
 * shows no confirmation dialog. Since Android 12 an app updating *itself* with
 * the same signature is installed without asking, and the session goes straight
 * to `INSTALL_SUCCEEDED`. The [UpdateInstallReceiver] relay is still needed
 * though: nothing guarantees that shortcut across versions and manufacturers,
 * and where it does not exist the button would visibly do nothing without it.
 *
 * A consequence to own rather than hide: the tap on "Install" is the only
 * consent collected. Lock 2 carries the security, not a system screen, and it is
 * stricter than what a browser would offer on the same link, since the refusal
 * comes before Android opens the file, with a message saying what happened
 * rather than "parse failed".
 */
object UpdateInstaller {

    /**
     * The link to follow, or null if none is acceptable.
     *
     * With no `url` published we fall back on the coordinator's `/download`: the
     * server that announced the version also serves it, which avoids keeping two
     * fields consistent in order to publish.
     */
    fun downloadUrl(
        published: String?,
        baseUrl: String = BuildConfig.COORDINATOR_BASE_URL
    ): String? {
        val fallback = "$baseUrl/download"
        val candidate = published?.takeIf { it.isNotBlank() } ?: return fallback
        val base = runCatching { URL(baseUrl) }.getOrNull() ?: return null
        val target = runCatching { URL(candidate) }.getOrNull() ?: return fallback
        // Same host, and HTTPS: a link elsewhere is not followed. Nor is it
        // treated as an attack, the field also serves to publish a page to read,
        // and the "View" button opens it in the browser, where the player
        // judges.
        val sameOrigin = target.protocol == "https" && target.host.equals(base.host, ignoreCase = true)
        return if (sameOrigin) candidate else fallback
    }

    /**
     * Pulls the APK, checks it, and hands it to Android. Long-running: to be
     * called off the main thread, which [withContext] handles here.
     */
    suspend fun downloadAndInstall(
        context: Context,
        latest: LatestVersion
    ): UpdateOutcome = withContext(Dispatchers.IO) {
        val app = context.applicationContext

        if (!app.packageManager.canRequestPackageInstalls()) {
            return@withContext UpdateOutcome.NeedsPermission(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:${app.packageName}".toUri()
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        val url = downloadUrl(latest.url) ?: return@withContext UpdateOutcome.Unavailable
        // In the cache: if the install succeeds the file is of no further use,
        // and if it fails Android reclaims it on its own when space runs short.
        // An APK forgotten in the player's documents would be this feature's
        // only lasting trace.
        val target = File(app.cacheDir, "update.apk")
        when (download(url, target)) {
            Fetched.Ok -> Unit
            Fetched.Missing -> {
                target.delete()
                return@withContext UpdateOutcome.Unavailable
            }
            Fetched.Broken -> {
                target.delete()
                return@withContext UpdateOutcome.DownloadFailed
            }
        }

        if (!isGenuine(app, target, latest.versionCode)) {
            target.delete()
            return@withContext UpdateOutcome.Rejected
        }

        val handed = runCatching { hand(app, target) }
            .onFailure { Log.w(TAG, "remise à Android impossible", it) }
            .getOrDefault(false)
        if (!handed) {
            target.delete()
            return@withContext UpdateOutcome.DownloadFailed
        }
        UpdateOutcome.HandedToAndroid
    }

    /**
     * What a download can come to.
     *
     * Three outcomes, not two, and the distinction was paid for: a boolean made a
     * transfer that had started perfectly well and stalled midway report "this
     * version is not downloadable here yet". The player went looking for a binary
     * missing from a server that was serving it fine.
     */
    private enum class Fetched { Ok, Missing, Broken }

    /** Whether the file arrived whole, and if not why. */
    private fun download(url: String, target: File): Fetched = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            // 60 s: a 32 MB APK over a living-room network is not an API call.
            // At 30 s a transfer that stalled for a moment was abandoned,
            // measured for real on the Thor, `broken pipe` server-side to the
            // second.
            readTimeout = 60_000
            // A redirect can leave the host checked above; following it would
            // silently undo the first lock.
            instanceFollowRedirects = false
        }
        try {
            // 404 is the only answer meaning "there is nothing to take here";
            // everything else is a transport incident, and saying otherwise
            // would send people looking in the wrong place.
            if (conn.responseCode == 404) return Fetched.Missing
            if (conn.responseCode != 200) return Fetched.Broken
            val announced = conn.contentLengthLong
            if (announced > MAX_APK_BYTES) return Fetched.Broken
            var written = 0L
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        written += read
                        // The ceiling holds without `Content-Length` too: a
                        // server is not obliged to announce one.
                        if (written > MAX_APK_BYTES) return Fetched.Broken
                        output.write(buffer, 0, read)
                    }
                }
            }
            // A download cut halfway gives a truncated ZIP, which the check
            // would reject, but it may as well be reported as the network
            // problem it actually is.
            if (announced <= 0 || written == announced) Fetched.Ok else Fetched.Broken
        } finally {
            conn.disconnect()
        }
    }.onFailure { Log.w(TAG, "téléchargement échoué", it) }.getOrDefault(Fetched.Broken)

    /**
     * Is the APK really an Emufii version signed with the same key?
     *
     * The central lock. Two questions, and both must hold: the certificate is
     * ours, and the version is the one announced. The second closes the
     * rollback, serving an old signed (therefore authentic) version to bring
     * back an already-fixed defect.
     */
    private fun isGenuine(context: Context, apk: File, announcedVersion: Int): Boolean {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val info = runCatching { pm.getPackageArchiveInfo(apk.path, flags) }.getOrNull()
            ?: return false.also { Log.w(TAG, "APK illisible") }

        if (info.packageName != context.packageName) {
            Log.w(TAG, "APK d'un autre paquet : ${info.packageName}")
            return false
        }
        val downloadedVersion = info.longVersionCode.toInt()
        if (downloadedVersion < announcedVersion || downloadedVersion <= BuildConfig.VERSION_CODE) {
            Log.w(TAG, "APK en version $downloadedVersion, refusé")
            return false
        }

        val mine = runCatching {
            pm.getPackageInfo(context.packageName, flags).signingInfo
        }.getOrNull() ?: return false
        return sameCertificates(mine, info.signingInfo)
    }

    /**
     * Compares certificates rather than key pairs.
     *
     * `hasMultipleSigners` separates two worlds that must not be mixed: an app
     * with multiple signers has no rotation history, and reading the wrong array
     * returns an empty list, which would compare "equal" to another empty list.
     * Hence the explicit refusal when there is nothing to compare.
     */
    private fun sameCertificates(mine: SigningInfo?, theirs: SigningInfo?): Boolean {
        if (mine == null || theirs == null) return false
        fun certs(info: SigningInfo): Set<String> {
            val list =
                if (info.hasMultipleSigners()) info.apkContentsSigners
                else info.signingCertificateHistory
            return (list ?: emptyArray()).map { it.toCharsString() }.toSet()
        }
        val ours = certs(mine)
        val other = certs(theirs)
        if (ours.isEmpty() || other.isEmpty()) return false
        // Intersection, not equality: after a key rotation the installed app
        // knows its history and the new APK carries only part of it. Demanding
        // equality would fail the one update we would really need to succeed
        // that day.
        return ours.any { it in other }
    }

    /**
     * Hands the file to Android, which shows its confirmation dialog.
     *
     * [PackageInstaller] rather than `ACTION_INSTALL_PACKAGE`: the latter has
     * been deprecated since Oreo and wants a `FileProvider` plus URI permissions
     * just to name a file we already own.
     */
    private fun hand(context: Context, apk: File): Boolean {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("emufii", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, UpdateInstallReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pending.intentSender)
        }
        return true
    }
}

/**
 * The install's outcome, as Android reports it.
 *
 * The only case demanding anything is [PackageInstaller.STATUS_PENDING_USER_ACTION]:
 * Android hands back the intent that shows the confirmation dialog, and it is
 * ours to launch. Without this relay the session is created, validated, and
 * visibly nothing happens, so the button would pass for dead.
 */
class UpdateInstallReceiver : android.content.BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            Log.i(TAG, "installation : statut $status ${message.orEmpty()}")
            return
        }
        // minSdk 33: the typed overload is present, so the deprecated one does
        // not have to be carried.
        val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { confirm?.let(context::startActivity) }
            .onFailure { Log.w(TAG, "dialogue de confirmation non ouvert", it) }
    }
}

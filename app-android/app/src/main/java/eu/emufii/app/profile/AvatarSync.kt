package eu.emufii.app.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.edit
import eu.emufii.app.network.CoordinatorClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom

class AvatarSync private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("avatar_sync", Context.MODE_PRIVATE)
    private val cacheDir = File(appContext.filesDir, "friend_avatars").apply { mkdirs() }

    private val rejected: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private val _friendFiles = MutableStateFlow(scanCache())
    val friendFiles: StateFlow<Map<String, File>> = _friendFiles.asStateFlow()

    /** Never sent in clear: the coordinator keeps only its hash. */
    fun ownerKey(): String = prefs.getString(KEY_OWNER, null) ?: ByteArray(32)
        .also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }
        .also { prefs.edit { putString(KEY_OWNER, it) } }

    suspend fun syncOwn(client: CoordinatorClient, profile: Profile) = withContext(Dispatchers.IO) {
        val file = profile.avatarFile?.takeIf { it.exists() }
        val sent = prefs.getString(KEY_SENT, null)
        if (file == null) {
            if (sent != null && client.deleteAvatar(profile.id, ownerKey()).isSuccess) {
                prefs.edit { remove(KEY_SENT) }
            }
            return@withContext
        }
        val signature = "${file.lastModified()}:${file.length()}"
        if (signature == sent) return@withContext
        val webp = withContext(Dispatchers.Default) { encode(file) } ?: return@withContext
        if (client.uploadAvatar(profile.id, ownerKey(), webp).isSuccess) {
            prefs.edit { putString(KEY_SENT, signature) }
        }
    }

    suspend fun syncFriends(client: CoordinatorClient, codes: List<String>, hashes: Map<String, String>) {
        withContext(Dispatchers.IO) {
            for (code in codes) {
                val hash = hashes[code]
                val target = File(cacheDir, "$code-$hash.webp")
                if (hash == null || target.exists() || target.name in rejected) continue
                val bytes = client.fetchAvatar(code).getOrNull() ?: continue
                if (!isSanePicture(bytes)) {
                    rejected += target.name // refetched every poll otherwise, until the hash changes
                    continue
                }
                File(cacheDir, "$code-$hash.tmp").apply { writeBytes(bytes) }.renameTo(target)
            }
            cacheDir.listFiles()?.forEach { f ->
                val code = f.name.substringBefore('-')
                val hash = f.name.substringAfter('-').substringBefore('.')
                if (code !in codes || hashes[code] != hash) f.delete()
            }
        }
        _friendFiles.value = scanCache()
    }

    private fun scanCache(): Map<String, File> =
        cacheDir.listFiles { f -> f.name.endsWith(".webp") }
            ?.associateBy { it.name.substringBefore('-') }
            ?: emptyMap()

    companion object {
        private const val KEY_OWNER = "owner_key"
        private const val KEY_SENT = "sent_signature"
        private const val SIDE = 256
        private const val MAX_BYTES = 40 * 1024

        @Volatile
        private var instance: AvatarSync? = null

        fun get(context: Context): AvatarSync =
            instance ?: synchronized(this) { instance ?: AvatarSync(context).also { instance = it } }

        /** Checks bounds before decoding, against crafted files asking for a huge bitmap. */
        fun isSanePicture(bytes: ByteArray): Boolean {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            return opts.outWidth in 1..1024 && opts.outHeight in 1..1024
        }

        /** Centre square, 256 px, WebP stepped down until under 40 KB. */
        private fun encode(file: File): ByteArray? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIDE) sample *= 2
            val source = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            val side = minOf(source.width, source.height)
            val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
            val scaled = Bitmap.createScaledBitmap(square, SIDE, SIDE, true)
            var quality = 85
            while (quality >= 30) {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, out)
                if (out.size() <= MAX_BYTES) return out.toByteArray()
                quality -= 15
            }
            null
        }.getOrNull()
    }
}

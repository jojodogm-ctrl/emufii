package eu.emufii.app.library

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32

// CRC32 of the whole file. DS netplay emulates every console on every device,
// so one differing byte (region, revision, trim, WFC patch) splits the session.
object RomFileHash {

    private val cache = ConcurrentHashMap<String, String>()

    suspend fun of(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        cache[uri.toString()]?.let { return@withContext it }
        runCatching {
            val crc = CRC32()
            context.contentResolver.openInputStream(uri)!!.use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    crc.update(buf, 0, n)
                }
            }
            "%08x".format(crc.value).also { cache[uri.toString()] = it }
        }.getOrNull()
    }
}

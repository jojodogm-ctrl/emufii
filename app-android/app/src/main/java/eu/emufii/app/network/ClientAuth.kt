package eu.emufii.app.network

import eu.emufii.app.BuildConfig
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * What tells Emufii apart from `curl` in the coordinator's eyes.
 *
 * ## What this protects, and what it does not
 *
 * The coordinator's address travels in the clear inside the APK, `strings` on
 * the dex is enough to read it, and the API required nothing of its callers. A
 * session could therefore be created in production with a plain `curl`, measured
 * on 2026-08-09. Anyone holding the public APK could run their games on a VPS
 * they do not pay for.
 *
 * This is not proof of identity and cannot be. The client is in the hands of the
 * very person we want to keep out: the key is in the binary, so it is
 * extractable, and claiming otherwise would be a lie. What the signature changes
 * is the cost: reading a URL is no longer enough, the APK has to be taken apart,
 * the key found, and this computation reimplemented. And since the key changes
 * at every version, the exercise has to be redone each time.
 *
 * The rest of the defence is server-side, where it really lies: the coordinator
 * logs the version calling it, which makes a stale or foreign client visible,
 * hence blockable.
 *
 * ## The shape
 *
 * `HMAC-SHA256(secret, method + "\n" + path + "\n" + timestamp + "\n" +
 * SHA-256(body))`, in lowercase hexadecimal.
 *
 * The body enters the computation, otherwise a signature valid for one request
 * would be valid for any other at the same path. The timestamp bounds the
 * replayability of an intercepted signature to a few minutes.
 */
object ClientAuth {

    /** The header carrying the signature. */
    const val HEADER_AUTH = "X-Emufii-Auth"

    /** The timestamp the signature was computed over, in seconds. */
    const val HEADER_TIMESTAMP = "X-Emufii-Ts"

    /** The calling version, so the server can see what is calling it. */
    const val HEADER_CLIENT = "X-Emufii-Client"

    /**
     * Empty on a build that received no key, typically a dev build.
     *
     * Such a build sends no signature at all, and it is the local coordinator
     * that decides to accept it: development must not depend on a production
     * secret.
     */
    private val secret: String get() = BuildConfig.CLIENT_SECRET

    val isConfigured: Boolean get() = secret.isNotEmpty()

    /** The app's version, as it announces itself to the coordinator. */
    val clientVersion: String get() = BuildConfig.VERSION_CODE.toString()

    /**
     * Signs a request, or returns null when this build has no key.
     *
     * [timestampSeconds] is a parameter so the test can freeze the clock; the app
     * never passes it.
     */
    fun sign(
        method: String,
        path: String,
        body: String?,
        timestampSeconds: Long = System.currentTimeMillis() / 1000
    ): Signature? {
        if (!isConfigured) return null
        val payload = buildString {
            append(method.uppercase()).append('\n')
            append(path).append('\n')
            append(timestampSeconds).append('\n')
            append(sha256Hex(body ?: ""))
        }
        return Signature(hmacHex(secret, payload), timestampSeconds.toString())
    }

    data class Signature(val value: String, val timestamp: String)

    private fun hmacHex(key: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .toHex()

    private fun ByteArray.toHex(): String {
        val out = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return out.toString()
    }

    private const val HEX = "0123456789abcdef"
}

package eu.emufii.app.network

import eu.emufii.app.BuildConfig
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** HMAC-SHA256(secret, method\npath\ntimestamp\nSHA-256(body)), lowercase hex. */
object ClientAuth {

    const val HEADER_AUTH = "X-Emufii-Auth"

    const val HEADER_TIMESTAMP = "X-Emufii-Ts"

    const val HEADER_CLIENT = "X-Emufii-Client"

    /** Empty on builds without a key: requests go unsigned. */
    private val secret: String get() = BuildConfig.CLIENT_SECRET

    val isConfigured: Boolean get() = secret.isNotEmpty()

    val clientVersion: String get() = BuildConfig.VERSION_CODE.toString()

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

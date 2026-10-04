package eu.emufii.app.wg

import android.content.Context
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair

// Raw key in prefs, not the keystore: WireGuard needs it. Same key, same address.
object WgKeys {

    private const val PREFS = "emufii_wg"
    private const val KEY_PRIVATE = "private_key"

    @Volatile
    private var cached: KeyPair? = null

    fun keyPair(ctx: Context): KeyPair {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stored = prefs.getString(KEY_PRIVATE, null)
            val pair = stored?.let { existing ->
                // A corrupt value mints a new identity instead of bricking the tunnel.
                runCatching { KeyPair(Key.fromBase64(existing)) }.getOrNull()
            } ?: KeyPair().also {
                prefs.edit().putString(KEY_PRIVATE, it.privateKey.toBase64()).apply()
            }
            cached = pair
            return pair
        }
    }

    fun publicKeyBase64(ctx: Context): String = keyPair(ctx).publicKey.toBase64()

    fun privateKeyBase64(ctx: Context): String = keyPair(ctx).privateKey.toBase64()

    fun reset(ctx: Context) {
        synchronized(this) {
            cached = null
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_PRIVATE).apply()
        }
    }
}

package eu.emufii.app.wg

import android.content.Context
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair

/**
 * This device's WireGuard identity, generated once and kept.
 *
 * It has to persist, and the reason is on the server side: the coordinator is
 * idempotent on the public key, so the same key always gets the same address. A
 * key regenerated per session would take a fresh address every time and leave the
 * relay holding a route to a peer nobody is behind, visible to the other player
 * as a game that connects and then goes quiet.
 *
 * Kept in the app's private preferences, alongside the profile and friend list.
 * Not in the keystore: WireGuard needs the raw private key in userspace to do the
 * handshake, so a hardware-backed key it could never extract would be useless
 * here. App-private storage is the honest boundary, and the same one the friend
 * code already relies on.
 */
object WgKeys {

    private const val PREFS = "emufii_wg"
    private const val KEY_PRIVATE = "private_key"

    @Volatile
    private var cached: KeyPair? = null

    /** The device's key pair, creating and storing one on first use. */
    fun keyPair(ctx: Context): KeyPair {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stored = prefs.getString(KEY_PRIVATE, null)
            val pair = stored?.let { existing ->
                // A corrupt or truncated value must not brick the tunnel for good:
                // fall through and mint a new identity. The cost is one new address
                // from the coordinator, which is cheap and self-correcting.
                runCatching { KeyPair(Key.fromBase64(existing)) }.getOrNull()
            } ?: KeyPair().also {
                prefs.edit().putString(KEY_PRIVATE, it.privateKey.toBase64()).apply()
            }
            cached = pair
            return pair
        }
    }

    /** What the coordinator is asked to assign an address to. */
    fun publicKeyBase64(ctx: Context): String = keyPair(ctx).publicKey.toBase64()

    fun privateKeyBase64(ctx: Context): String = keyPair(ctx).privateKey.toBase64()

    /**
     * Drops the identity, so the next tunnel uses a new one.
     *
     * Belongs with deleting the profile: the public key is a stable identifier the
     * coordinator sees, so leaving it behind would outlive the profile it came with.
     */
    fun reset(ctx: Context) {
        synchronized(this) {
            cached = null
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_PRIVATE).apply()
        }
    }
}

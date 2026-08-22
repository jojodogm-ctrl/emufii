package eu.emufii.app.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.abs

/**
 * Who you are to the other players.
 *
 * [id] is a stable random identifier, not derived from anything about the
 * device: it's what the coordinator counts presence by, so it has to survive a
 * rename but must not identify the person beyond this app.
 *
 * It doubles as the friend code you hand out (see [FriendCode]), which is why
 * it is short enough to read aloud. That is deliberate: with the code carrying
 * the identity, adding a friend needs no server-side directory. It also means
 * the id is public by design, it always was, in practice, since it travels
 * with every session as the host or member id.
 */
data class Profile(
    val id: String,
    val name: String,
    val avatarFile: File? = null
) {
    /** True once the user has actually chosen a name rather than kept the default. */
    val isNamed: Boolean get() = name.isNotBlank() && name != DEFAULT_NAME

    /** The id as you'd show it to someone: `E7K2-9QM4-XR8T`. */
    val friendCode: String get() = FriendCode.format(id)

    companion object {
        /**
         * The pseudo of someone who never picked one. Stored as-is and sent over
         * the wire, so it stays a fixed sentinel rather than a resource: it is
         * what [isNamed] compares against, and it is already persisted on
         * devices. Translating it happens at the point of display, see
         * [playerDisplayName], which also gets the other player's placeholder
         * name read in *your* language.
         */
        const val DEFAULT_NAME = "Joueur"
        const val MAX_NAME_LENGTH = 20

        /**
         * Azahar's netplay form rejects a pseudo shorter than this, "Invalid
         * address or name is too short!", and Emufii sends the profile name
         * straight into it. Enforced where the name is *entered*, so the value
         * on disk is always usable, rather than patched at the point of use.
         *
         * Observed on the device, not read from a constant: the validator lives
         * in Azahar's DEX and its message doesn't carry the number. [DEFAULT_NAME]
         * clears it, so a profile that was never named stays valid.
         */
        const val MIN_NAME_LENGTH = 4
    }
}

/**
 * Local store. There is no account and no server-side profile: the pseudo
 * travels with each session as a plain string, and the picture never leaves the
 * device.
 *
 * Other players are therefore drawn with [avatarPaletteFor], initials on a
 * colour derived from their name, rather than a picture we'd have to host,
 * moderate and pay for. Uploading real avatars is a product decision, not a
 * missing feature.
 *
 * The identity is durable but device-bound: it lives here and nowhere else, so
 * a reinstall is a new person to your friends. Restoring one across devices
 * would take a recovery secret and somewhere to put it, which is exactly the
 * hosted account this design avoids.
 */
class ProfileStore(context: Context) {

    private val prefs = context.getSharedPreferences("emufii_profile", Context.MODE_PRIVATE)
    private val avatarTarget = File(context.filesDir, "avatar.png")
    private val appContext = context.applicationContext

    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    private fun load(): Profile {
        // Early builds stored a UUID here. It was never shown to anyone and
        // nothing durable hangs off it, friends did not exist yet, so a
        // profile carrying one is simply reissued a shareable code.
        val stored = prefs.getString(KEY_ID, null)
        val id = stored?.takeIf { FriendCode.isValid(it) }
            ?: FriendCode.generate().also { prefs.edit { putString(KEY_ID, it) } }
        return Profile(
            id = id,
            name = prefs.getString(KEY_NAME, Profile.DEFAULT_NAME) ?: Profile.DEFAULT_NAME,
            avatarFile = avatarTarget.takeIf { it.exists() }
        )
    }

    /**
     * A name below [Profile.MIN_NAME_LENGTH] is stored as [Profile.DEFAULT_NAME]
     * rather than as typed. The UI refuses it first, with an error the user can
     * act on; this is the backstop that keeps the invariant true for callers
     * that don't go through a form, nothing downstream should have to wonder
     * whether the stored pseudo is one the emulator will accept.
     */
    fun setName(name: String) {
        val trimmed = name.trim()
        val usable = if (trimmed.length < Profile.MIN_NAME_LENGTH) "" else trimmed
        val clean = usable.take(Profile.MAX_NAME_LENGTH).ifBlank { Profile.DEFAULT_NAME }
        prefs.edit { putString(KEY_NAME, clean) }
        _profile.value = _profile.value.copy(name = clean)
    }

    /**
     * Copies the picked image into our own storage, downscaled.
     *
     * Two reasons not to keep the original. The SAF grant from the picker isn't
     * persisted, so holding the Uri would leave a broken avatar after a
     * restart. And a modern phone photo is 50 megapixels, decoding one whole
     * to draw a 40dp circle is how an app gets killed for memory.
     *
     * [BitmapFactory.Options.inSampleSize] means the full image is never
     * decoded in the first place: the decoder subsamples as it reads.
     */
    fun setAvatar(source: Uri): Result<Unit> = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(source).use {
            BitmapFactory.decodeStream(requireNotNull(it) { "image illisible" }, null, bounds)
        }

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= AVATAR_PX &&
            bounds.outHeight / (sample * 2) >= AVATAR_PX
        ) {
            sample *= 2
        }

        val decoded = appContext.contentResolver.openInputStream(source).use {
            BitmapFactory.decodeStream(
                requireNotNull(it) { "image illisible" },
                null,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        } ?: error("format d'image non reconnu")

        avatarTarget.outputStream().use { out ->
            decoded.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        decoded.recycle()

        // New File instance so Compose sees a changed value even though the
        // path is identical, otherwise the picture updates only on restart.
        _profile.value = _profile.value.copy(avatarFile = File(avatarTarget.path))
    }

    fun clearAvatar() {
        avatarTarget.delete()
        _profile.value = _profile.value.copy(avatarFile = null)
    }

    /**
     * Erase this identity and start over with a fresh one.
     *
     * The new code is unrelated to the old, so anyone who kept the previous one
     * can no longer see you, which is the point, and the only way out if a
     * code ends up somewhere you did not intend. It also cuts you off from your
     * own friends list, so the caller is expected to clear that too and to ask
     * first.
     */
    fun reset() {
        avatarTarget.delete()
        prefs.edit { clear() }
        _profile.value = load()
    }

    private companion object {
        const val KEY_ID = "id"
        const val KEY_NAME = "name"

        /** Generous for the largest place an avatar is drawn (104dp on a dense screen). */
        const val AVATAR_PX = 512
    }
}

/** Up to two letters standing in for a player with no picture. */
fun initialsFor(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/**
 * Index into a caller-supplied palette, stable for a given name so a player
 * keeps the same colour between sessions.
 */
fun avatarPaletteFor(name: String, paletteSize: Int): Int =
    if (paletteSize <= 0) 0 else abs(name.hashCode()) % paletteSize

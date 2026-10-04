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

data class Profile(
    val id: String,
    val name: String,
    val avatarFile: File? = null
) {
    val isNamed: Boolean get() = name.isNotBlank() && name != DEFAULT_NAME

    val friendCode: String get() = FriendCode.format(id)

    companion object {
        const val DEFAULT_NAME = "Joueur"
        const val MAX_NAME_LENGTH = 20

        /** Azahar's netplay form rejects shorter nicknames (validator in its DEX). */
        const val MIN_NAME_LENGTH = 4
    }
}

class ProfileStore(context: Context) {

    private val prefs = context.getSharedPreferences("emufii_profile", Context.MODE_PRIVATE)
    private val avatarTarget = File(context.filesDir, "avatar.png")
    private val appContext = context.applicationContext

    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    private fun load(): Profile {
        val stored = prefs.getString(KEY_ID, null)
        val id = stored?.takeIf { FriendCode.isValid(it) }
            ?: FriendCode.generate().also { prefs.edit { putString(KEY_ID, it) } }
        return Profile(
            id = id,
            name = prefs.getString(KEY_NAME, Profile.DEFAULT_NAME) ?: Profile.DEFAULT_NAME,
            avatarFile = avatarTarget.takeIf { it.exists() }
        )
    }

    fun setName(name: String) {
        val trimmed = name.trim()
        val usable = if (trimmed.length < Profile.MIN_NAME_LENGTH) "" else trimmed
        val clean = usable.take(Profile.MAX_NAME_LENGTH).ifBlank { Profile.DEFAULT_NAME }
        prefs.edit { putString(KEY_NAME, clean) }
        _profile.value = _profile.value.copy(name = clean)
    }

    /** Copied and downscaled: the SAF grant is not persisted, and phone photos are huge. */
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

        // New File instance so Compose sees a change at the same path.
        _profile.value = _profile.value.copy(avatarFile = File(avatarTarget.path))
    }

    fun clearAvatar() {
        avatarTarget.delete()
        _profile.value = _profile.value.copy(avatarFile = null)
    }

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

fun initialsFor(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

fun avatarPaletteFor(name: String, paletteSize: Int): Int =
    if (paletteSize <= 0) 0 else abs(name.hashCode()) % paletteSize

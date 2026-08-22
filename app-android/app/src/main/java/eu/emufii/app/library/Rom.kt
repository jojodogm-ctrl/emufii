package eu.emufii.app.library

import android.net.Uri
import java.io.File

data class Rom(
    val uri: Uri,
    val filename: String,
    val displayName: String,
    val console: Console,
    val titleIdHex: String? = null,
    val productCode: String? = null,
    val iconFile: File? = null,
    /**
     * ARGB pulled from the artwork, for the tile's glow. Null when the icon has
     * no colour worth borrowing, see [IconAccent].
     */
    val accentArgb: Int? = null,
    /**
     * When the file arrived in the library, in milliseconds, being the
     * last-modified date the document provider reports.
     *
     * This is not the date added in the strict sense, and nothing on Android
     * gives that: a `DocumentsProvider` only exposes `LAST_MODIFIED`. For a ROM,
     * a file copied once and never rewritten, the two coincide. Zero when the
     * provider says nothing, which files the game at the end of the sort.
     */
    val addedAt: Long = 0L
) {
    /**
     * What a session publishes its game under, and what a guest finds it by on
     * their own device.
     *
     * The 3DS and the Switch carry a title id; the PSP and the DS have none and
     * file their disc id under [productCode]. Taking [titleIdHex] alone amounted
     * to publishing "nothing" for a PSP session: the guest then searched for a
     * game no ROM could match, and the app told them they did not have the game
     * they had right there.
     *
     * Used to *find*, never to *refuse*; the wrong-game safeguard still compares
     * title ids only, because two regional dumps of the same PSP game carry two
     * disc ids and may well play together perfectly.
     */
    val sessionId: String? get() = titleIdHex ?: productCode
}

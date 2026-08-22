package eu.emufii.app.ps2

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log

/**
 * The PS2's network configuration, handed to the player as a memory card image.
 *
 * A PS2 LAN game will not open its local menu until the console's network
 * configuration exists on a memory card. It is save data, not a setting: no
 * amount of correct tunnelling makes up for its absence, and a player without it
 * meets a dead menu with nothing explaining why. Measured on the bench on
 * 2026-08-20, and the reason this exists at all.
 *
 * The trap is that **most games only expect that data and cannot create it**.
 * The utility that writes it is embedded in a handful of titles — Midnight Club
 * 3: DUB Edition Remix among them — so a player who owns none of those has no
 * way in. Emufii therefore ships the configuration itself: the card here is the
 * one the console wrote, byte for byte, not one built by this app.
 *
 * ### The player's saves move, not the configuration
 *
 * `BWNETCNF` is copy-protected — mode `0x842f`, the `0x08` bit — so the BIOS
 * browser refuses to copy it onto the player's own card. The transfer only runs
 * the other way: this card goes to slot 1, the player's card to slot 2, and the
 * game saves come over. They are not protected (`0x8427`), which is what makes
 * the whole procedure possible. Whatever *is* protected on the old card, such as
 * a game's options save, never moves; the player has to keep that card.
 *
 * ### Why a card image and not a folder memory card
 *
 * ARMSX2 imports folder memory cards — a directory holding `_pcsx2_superblock`
 * (8192 bytes, written by the console formatting it; a 512-byte page is read as
 * unformatted) plus one subdirectory per save. It is a dead end here, for two
 * independent reasons measured the same day:
 *
 * - **PCSX2 filters a folder card by the running game.** The log says
 *   `FolderMcd: Indexing slot 0 with filter "SLES-52942/SLES-53717"`: only saves
 *   whose name matches the serial, or the GameDB's `memcardFilters`, are shown.
 *   `BWNETCNF` matches nothing, so the game sees a configuration it cannot read
 *   and reports it invalid. The GameDB cannot be patched around it either —
 *   ARMSX2 rewrites `resources/` from its assets at every start.
 * - **Mounted without a filter, the BIOS reads the save as damaged.** Same bytes,
 *   same card, so it is the folder-to-card reconstruction that loses something.
 *
 * A card image goes through neither path, and the BIOS reads it correctly.
 *
 * ### Why the player does the importing
 *
 * Since Android 11 an app cannot write into another app's `Android/data`, and
 * the document picker refuses that path too, so Emufii cannot drop this into
 * ARMSX2's folder however much it would like to. What it can do is put it in
 * Downloads, which ARMSX2's own "import file" reads.
 *
 * ### What is on it
 *
 * One save, `BWNETCNF`: two plain-text files that name the interface, two small
 * binaries the console writes, and the Sony icon that goes with them.
 */
object Ps2NetworkProfile {

    private const val ASSET = "ps2/emufii-ps2-net.ps2"

    /** The file the player will pick in ARMSX2's importer. */
    const val FILE_NAME = "emufii-ps2-net.ps2"

    /**
     * Writes the folder into Downloads, or null when the store refuses.
     *
     * Idempotent by deletion: writing again over an existing name would leave
     * MediaStore holding two files called the same thing, and the importer would
     * show the player a choice that means nothing to them.
     */
    fun export(context: Context): String? = runCatching {
        val resolver = context.contentResolver
        resolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(FILE_NAME)
        )
        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending)
            ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            context.assets.open(ASSET).use { it.copyTo(out) }
        } ?: return null
        resolver.update(uri, ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
        }, null, null)
        FILE_NAME
    }.onFailure { Log.w("Ps2NetworkProfile", "export impossible", it) }.getOrNull()

    /**
     * Has the player told us the card is in ARMSX2?
     *
     * Their word, not a measurement, and it cannot be anything else: since
     * Android 11 nothing here can look inside ARMSX2's folder to check. So the
     * flag records a claim, and the value of recording it is that a PS2 session
     * refuses to start before it is made — a dead local menu twenty minutes into
     * a session is far more expensive than one question asked up front.
     */
    fun isReady(context: Context): Boolean = prefs(context).getBoolean(KEY_READY, false)

    fun setReady(context: Context, ready: Boolean) {
        prefs(context).edit().putBoolean(KEY_READY, ready).apply()
    }

    private const val KEY_READY = "imported"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("ps2_network_profile", Context.MODE_PRIVATE)
}

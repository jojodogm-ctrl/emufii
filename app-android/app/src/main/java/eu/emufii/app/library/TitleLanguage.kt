package eu.emufii.app.library

import android.content.Context

/**
 * Which language a cartridge should say its name in.
 *
 * Every format Emufii reads carries its title several times over, a DS banner in
 * six languages, a 3DS SMDH in twelve, a Switch control in sixteen, and each
 * reader used to pick from a list frozen at "French, then English, then
 * Japanese". So an app running in English showed "Pokémon Version Blanche 2",
 * and there was no way to ask for anything else. The cartridge knows both
 * names; the only question is which one to read, and the answer is the language
 * the app itself is speaking.
 *
 * The app is bilingual, so each order names its own language first and the
 * other second, then keeps the old tail: a Japanese cartridge that carries no
 * French and no English still has to produce *something*, and a Japanese title
 * beats a filename.
 *
 * [tag] exists because titles are cached on disk. Two languages of the same
 * cartridge are two different strings under the same game code, so the cache
 * key has to carry the language or switching the app's language would show the
 * previous one until the next rescan.
 */
object TitleLanguage {

    /**
     * The language Emufii is *displayed* in, which is not the same as the
     * device's.
     *
     * `Locale.getDefault()` was the obvious answer and the wrong one: Emufii sets
     * a per-app locale, so an app running in French on an English phone still
     * reports English there, and the library came back in English while every
     * label around it was French. The resources configuration is the one that
     * carries the per-app locale, so that is what is read, once per scan.
     */
    @Volatile
    private var current: String = "en"

    /** Call before reading anything below; cheap, and the only place the locale enters. */
    fun apply(context: Context) {
        set(context.resources.configuration.locales[0].language)
    }

    /**
     * The same thing without Android, so the readers can be tested in both
     * languages, which is the only way to catch a preference list that names
     * the right slots in the wrong order.
     */
    fun set(language: String) {
        current = language
    }

    val tag: String get() = if (current == "fr") "fr" else "en"

    private val isFrench: Boolean get() = tag == "fr"

    /**
     * SMDH title slots: 0 Japanese, 1 English, 2 French, 3 German, 4 Italian,
     * 5 Spanish, 6 Simplified Chinese, 7 Korean, 8 Dutch, 9 Portuguese,
     * 10 Russian, 11 Traditional Chinese.
     */
    val smdh: IntArray
        get() = if (isFrench) intArrayOf(2, 1, 0, 3, 4, 5, 8, 9, 10, 6, 11, 7)
        else intArrayOf(1, 2, 0, 3, 4, 5, 8, 9, 10, 6, 11, 7)

    /** DS banner title slots: 0 Japanese, 1 English, 2 French, 3 German, 4 Italian, 5 Spanish. */
    val ndsBanner: IntArray
        get() = if (isFrench) intArrayOf(2, 1, 0, 3, 4, 5) else intArrayOf(1, 2, 0, 3, 4, 5)

    /**
     * Switch control entries, named rather than numbered, `icon_French.dat`,
     * `icon_AmericanEnglish.dat`. Both English variants are tried: a European
     * dump often carries only the British one.
     */
    val switch: List<String>
        get() = if (isFrench) {
            listOf("French", "CanadianFrench", "BritishEnglish", "AmericanEnglish", "Spanish", "German", "Italian", "Japanese")
        } else {
            listOf("AmericanEnglish", "BritishEnglish", "French", "CanadianFrench", "Spanish", "German", "Italian", "Japanese")
        }

    /**
     * GameCube `BNR2` slots: 0 English, 1 German, 2 French, 3 Spanish,
     * 4 Italian, 5 Dutch. `BNR1` carries a single title and ignores this.
     */
    val gcBanner: IntArray
        get() = if (isFrench) intArrayOf(2, 0, 1, 3, 4, 5) else intArrayOf(0, 2, 1, 3, 4, 5)

    /**
     * Wii `IMET` slots: 0 Japanese, 1 English, 2 German, 3 French, 4 Spanish,
     * 5 Italian, 6 Dutch, 7 Simplified Chinese, 8 Traditional Chinese, 9 Korean.
     *
     * Not the same order as [gcBanner], which is exactly why they are two lists:
     * slot 2 is French on a GameCube disc and German on a Wii one, so sharing a
     * list would have given German titles to French players on half the shelf.
     */
    val wiiImet: IntArray
        get() = if (isFrench) intArrayOf(3, 1, 0, 2, 4, 5, 6) else intArrayOf(1, 3, 0, 2, 4, 5, 6)
}

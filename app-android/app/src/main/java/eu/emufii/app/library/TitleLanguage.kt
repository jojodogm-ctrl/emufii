package eu.emufii.app.library

import android.content.Context

object TitleLanguage {

    /** The app's language, not the device's: `Locale.getDefault()` ignores the per-app locale. */
    @Volatile
    private var current: String = "en"

    fun apply(context: Context) {
        set(context.resources.configuration.locales[0].language)
    }

    fun set(language: String) {
        current = language
    }

    val tag: String get() = if (current == "fr") "fr" else "en"

    private val isFrench: Boolean get() = tag == "fr"

    /** SMDH slots: 0 ja, 1 en, 2 fr, 3 de, 4 it, 5 es, 6 zh-Hans, 7 ko, 8 nl, 9 pt, 10 ru, 11 zh-Hant. */
    val smdh: IntArray
        get() = if (isFrench) intArrayOf(2, 1, 0, 3, 4, 5, 8, 9, 10, 6, 11, 7)
        else intArrayOf(1, 2, 0, 3, 4, 5, 8, 9, 10, 6, 11, 7)

    /** 0 ja, 1 en, 2 fr, 3 de, 4 it, 5 es. */
    val ndsBanner: IntArray
        get() = if (isFrench) intArrayOf(2, 1, 0, 3, 4, 5) else intArrayOf(1, 2, 0, 3, 4, 5)

    val switch: List<String>
        get() = if (isFrench) {
            listOf("French", "CanadianFrench", "BritishEnglish", "AmericanEnglish", "Spanish", "German", "Italian", "Japanese")
        } else {
            listOf("AmericanEnglish", "BritishEnglish", "French", "CanadianFrench", "Spanish", "German", "Italian", "Japanese")
        }

    /** BNR2 slots: 0 en, 1 de, 2 fr, 3 es, 4 it, 5 nl. BNR1 has one title. */
    val gcBanner: IntArray
        get() = if (isFrench) intArrayOf(2, 0, 1, 3, 4, 5) else intArrayOf(0, 2, 1, 3, 4, 5)

    /** IMET slots: 0 ja, 1 en, 2 de, 3 fr, 4 es, 5 it, 6 nl, 7 zh-Hans, 8 zh-Hant, 9 ko. */
    val wiiImet: IntArray
        get() = if (isFrench) intArrayOf(3, 1, 0, 2, 4, 5, 6) else intArrayOf(1, 3, 0, 2, 4, 5, 6)
}

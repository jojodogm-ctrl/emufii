package eu.emufii.app.secondscreen

import androidx.annotation.StringRes
import eu.emufii.app.R

/** Glyphs as printed on the Thor in Nintendo mode; Xbox mode swaps A/B and X/Y (see shownGlyph). */
enum class PadHint(
    /** Only characters Rounded M+ carries: a fallback glyph renders with other metrics. */
    val glyph: String?,
    @StringRes val label: Int,
    val held: Boolean = false,
) {

    CONFIRM(glyph = "A", label = R.string.pad_hint_confirm),

    BACK(glyph = "B", label = R.string.pad_hint_back),

    HOLD(glyph = "A", label = R.string.pad_hint_hold, held = true),

    ERASE(glyph = "B", label = R.string.pad_hint_erase),

    SCREENSHOTS(glyph = "X", label = R.string.pad_hint_screenshots),

    BROWSE(glyph = null, label = R.string.pad_hint_browse),

    CLOSE(glyph = "B", label = R.string.pad_hint_close),
    ;

    fun shownGlyph(flipped: Boolean): String? =
        if (!flipped) glyph else when (glyph) {
            "A" -> "B"
            "B" -> "A"
            "X" -> "Y"
            "Y" -> "X"
            else -> glyph
        }
}

data class PadLegend(
    val left: List<PadHint> = emptyList(),
    val right: List<PadHint> = emptyList(),
) {
    val isEmpty: Boolean get() = left.isEmpty() && right.isEmpty()

    companion object {
        val BROWSING = PadLegend(
            left = listOf(PadHint.BACK),
            right = listOf(PadHint.CONFIRM, PadHint.HOLD),
        )

        val FOLDER = PadLegend(
            left = listOf(PadHint.BACK),
            right = listOf(PadHint.CONFIRM),
        )

        val IN_SESSION = PadLegend()
    }
}

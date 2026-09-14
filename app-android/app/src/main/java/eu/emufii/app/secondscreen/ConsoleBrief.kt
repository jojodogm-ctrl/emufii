package eu.emufii.app.secondscreen

import androidx.annotation.StringRes
import eu.emufii.app.R
import eu.emufii.app.library.Console

/**
 * How Emufii plays together on one machine, in as few words as it takes. Two lines and
 * at most one warning, and that ceiling is the design. [warning] is for what would
 * otherwise be discovered as a fault, not for nuance.
 * pourquoi : docs/decisions/second-ecran.md § A console card fits in two lines and a warning
 */
data class ConsoleBrief(
    /** What sets this machine apart, under its name. Never "playing together": the person
     *  reading this opened Emufii, so they know that much already. */
    @StringRes val lead: Int,
    @StringRes val first: Int,
    @StringRes val second: Int,
    @StringRes val warning: Int? = null,
)

fun consoleBrief(console: Console): ConsoleBrief = when (console) {
    Console.THREE_DS -> ConsoleBrief(
        lead = R.string.brief_3ds_lead,
        first = R.string.brief_3ds_1,
        second = R.string.brief_3ds_2,
        warning = R.string.brief_3ds_warning,
    )
    Console.SWITCH -> ConsoleBrief(
        lead = R.string.brief_switch_lead,
        first = R.string.brief_switch_1,
        second = R.string.brief_switch_2,
    )
    Console.PSP -> ConsoleBrief(
        lead = R.string.brief_psp_lead,
        first = R.string.brief_psp_1,
        second = R.string.brief_psp_2,
    )
    Console.DS -> ConsoleBrief(
        lead = R.string.brief_ds_lead,
        first = R.string.brief_ds_1,
        second = R.string.brief_ds_2,
        warning = R.string.brief_ds_warning,
    )
    Console.PS2 -> ConsoleBrief(
        lead = R.string.brief_ps2_lead,
        first = R.string.brief_ps2_1,
        second = R.string.brief_ps2_2,
        warning = R.string.brief_ps2_warning,
    )
    Console.GAMECUBE, Console.WII -> ConsoleBrief(
        lead = R.string.brief_dolphin_lead,
        first = R.string.brief_dolphin_1,
        second = R.string.brief_dolphin_2,
        warning = R.string.brief_dolphin_warning,
    )
}

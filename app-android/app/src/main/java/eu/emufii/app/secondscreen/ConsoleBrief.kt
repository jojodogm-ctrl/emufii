package eu.emufii.app.secondscreen

import androidx.annotation.StringRes
import eu.emufii.app.R
import eu.emufii.app.library.Console

data class ConsoleBrief(
    @StringRes val mode: Int,
    val sessionCode: Boolean,
    @StringRes val games: Int,
    @StringRes val warning: Int? = null,
)

fun consoleBrief(console: Console): ConsoleBrief = when (console) {
    Console.THREE_DS -> ConsoleBrief(
        mode = R.string.console_mode_3ds,
        sessionCode = true,
        games = R.string.console_games_3ds,
        warning = R.string.brief_3ds_warning,
    )
    Console.SWITCH -> ConsoleBrief(
        mode = R.string.console_mode_switch,
        sessionCode = true,
        games = R.string.console_games_switch,
    )
    Console.PSP -> ConsoleBrief(
        mode = R.string.console_mode_psp,
        sessionCode = true,
        games = R.string.console_games_psp,
    )
    Console.DS -> ConsoleBrief(
        mode = R.string.console_mode_ds,
        sessionCode = true,
        games = R.string.console_games_ds,
        warning = R.string.brief_ds_warning,
    )
    Console.PS2 -> ConsoleBrief(
        mode = R.string.console_mode_ps2,
        sessionCode = true,
        games = R.string.console_games_ps2,
    )
    Console.GAMECUBE, Console.WII -> ConsoleBrief(
        mode = R.string.console_mode_dolphin,
        sessionCode = true,
        games = R.string.console_games_dolphin,
        warning = R.string.brief_dolphin_warning,
    )
}

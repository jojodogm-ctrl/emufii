package eu.emufii.app.netplay

import android.content.Context

/**
 * Does the installed build actually *have* a multiplayer UI to drive?
 *
 * Emufii drives netplay by filling in the emulator's own dialog through the
 * accessibility service. That only works if the dialog exists, and it does not
 * always. Azahar 2125.1.3-vanilla, an official release (signed by the Lime3DS
 * team, the build shipped on the AYN Thor), carries the whole network engine in
 * its native library, `Network::RoomMember`, ENet, wifi packet handling, but
 * none of the Android views that reach it. Its 36 765 resources contain no
 * `menu_multiplayer`, no `btn_join`, no `ip_address`; the only occurrence of the
 * word "multiplayer" is the description of an unrelated LLE setting.
 *
 * Armed against such a build, the automation waits for a screen that will never
 * appear, and the failure looks like Emufii doing nothing at all. Hence this
 * probe, run *before* arming.
 *
 * It asks the package manager for the emulator's resources rather than comparing
 * version numbers. A version threshold would need a magic constant per build
 * channel and would be wrong for any fork; asking whether the view id resolves
 * is the same question the accessibility service will ask at runtime, so it
 * cannot disagree with it. Requires only that the package be visible in
 * `<queries>`, which it already is.
 */
object NetplayUiSupport {

    /**
     * The ids that must resolve for the automation to have anything to fill in:
     * the entry buttons and the address field of the room form. Deliberately not
     * the whole of [NetplayUi], [NetplayUi.PREFERRED_GAME] is Eden-only and
     * [NetplayUi.MENU_MULTIPLAYER] is Azahar-only, so requiring either would
     * report a false negative on the other.
     */
    val PROBE_IDS = listOf(
        NetplayUi.BTN_CREATE,
        NetplayUi.BTN_JOIN,
        NetplayUi.IP_ADDRESS,
        NetplayUi.BTN_CONFIRM
    )

    /**
     * True if [pkg] exposes a netplay dialog Emufii can drive.
     *
     * Returns false when the package is absent or its resources can't be read,
     * the caller wants to know "can I drive this", and both answers are no.
     */
    fun isPresent(context: Context, pkg: String): Boolean {
        val res = runCatching {
            context.packageManager.getResourcesForApplication(pkg)
        }.getOrNull() ?: return false
        return PROBE_IDS.all { name ->
            runCatching { res.getIdentifier(name, "id", pkg) }.getOrDefault(0) != 0
        }
    }
}

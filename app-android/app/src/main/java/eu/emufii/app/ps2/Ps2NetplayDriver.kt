package eu.emufii.app.ps2

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import eu.emufii.app.R
import eu.emufii.app.azahar.NetplayAutomation
import eu.emufii.app.azahar.NetplayPlan
import eu.emufii.app.azahar.NetplayProgress
import eu.emufii.app.dolphin.Bounds
import eu.emufii.app.dolphin.Node
import eu.emufii.app.wg.WgConfig

/**
 * Sets ARMSX2's Settings -> Network screen up for a Local Link game.
 *
 * Third driver, and third shape of screen: Azahar and Eden are read by view
 * ids, Dolphin by nesting of Compose texts, ARMSX2 by rows, label on the left,
 * value on the right, and it is the container that takes the click. See
 * [Ps2Target] for why the three do not converge.
 *
 * Two peculiarities that exist nowhere else in this project:
 *
 * 1. There is no text field at all. Opening a row brings up ARMSX2's own
 *    keyboard, and input goes in key by key. `ACTION_SET_TEXT` has nothing to
 *    aim at, and injected key events are ignored (measured).
 * 2. That keyboard has no dot key, so the guest cannot write an IPv4 address.
 *    They write a name, `emufii`, which the tunnel's DNS resolves to the relay's
 *    sentinel. See `relay/dns.js` and [WgConfig.PS2_HOST_NAME].
 *
 * Like its two elders: at the first screen it no longer recognises, it stops and
 * says what to type, rather than clicking at random.
 */
class Ps2NetplayDriver(
    private val context: Context,
    /**
     * Re-reads the tree between two keystrokes.
     *
     * Essential here and nowhere else: entering a value means a dozen clicks in
     * a row on a screen that redraws at every character. Keeping the nodes from
     * the first sweep would mean clicking at stale positions.
     */
    private val readTree: () -> AccessibilityNodeInfo?,
    /**
     * The system "back" gesture.
     *
     * To get out of a screen we cannot read. An ARMSX2 that is already open
     * comes back to the foreground where the player left it, and there is no
     * path from an unknown screen to the settings other than this one.
     */
    private val goBack: () -> Boolean,
    private val onFinished: (success: Boolean) -> Unit
) {

    private val labels by lazy { Ps2Labels(context) }

    private var navClicks = 0
    private var navPlan: NetplayPlan? = null

    /** Scrolls spent on the current plan. */
    private var scrolls = 0

    /** System backs spent looking for a screen we know. */
    private var backs = 0

    /** Clicks spent on the mode choice. One is enough; two are a bug. */
    private var modeClicks = 0

    /** Passes in a row on a screen we cannot read. Reset as soon as we read one. */
    private var unknownPasses = 0

    /**
     * Entries attempted on the current plan, all fields together.
     *
     * A ceiling, because a screen that does not read back the value just written
     * into it would make the driver start over endlessly; that happened, and
     * without this counter it would have rewritten the room code until the
     * player closed the app.
     */
    private var writes = 0

    /**
     * The steps already set.
     *
     * Needed because we go down the screen and never back up: once the DEV9
     * toggle has been passed it is no longer in the tree, and with no memory the
     * driver would conclude it still had to be set.
     */
    private val done = HashSet<String>()

    /** Returns true if this pass advanced the flow. */
    fun step(root: AccessibilityNodeInfo, pkg: String, plan: NetplayPlan): Boolean {
        if (navPlan !== plan) {
            navPlan = plan
            navClicks = 0
            scrolls = 0
            writes = 0
            backs = 0
            modeClicks = 0
            unknownPasses = 0
            done.clear()
        }

        val nodes = flatten(root)
        val hosting = plan.role == NetplayPlan.Role.Host
        Log.d(TAG, "passe: ${nodes.size} nœuds, rôle=${if (hosting) "hôte" else "invité"}")

        // The keyboard first: while it is open nothing else is reachable, and
        // the screen we were reading is behind it.
        if (Ps2Screen.keyboardIsOpen(nodes)) {
            val wanted = pendingValue ?: run {
                // Open with nothing we know to put in it: close it rather than
                // leave the player facing a keyboard we brought up.
                Log.w(TAG, "clavier ouvert sans valeur à saisir, fermeture")
                return Ps2Screen.commandKey(nodes, Ps2Screen.KEY_DONE)?.live?.click() ?: false
            }
            return type(nodes, wanted)
        }

        // 3. The Network screen.
        //
        //    Recognised by any one of its markers, and not by the DEV9 switch
        //    alone: this screen is taller than the device, it has to be
        //    scrolled, and an accessibility tree only contains what is actually
        //    drawn. Hanging on the first label would have meant losing sight of
        //    the screen at the very first scroll.
        val dev9 = labels.of(Ps2Target.I18n.KEY_ENABLE_DEV9, Ps2Target.LABEL_ENABLE_DEV9)
            .firstNotNullOfOrNull { Ps2Screen.label(nodes, it) }
        val onNetworkScreen = dev9 != null || NETWORK_MARKERS.any { Ps2Screen.label(nodes, it) != null }
        if (onNetworkScreen) {
            unknownPasses = 0
            return settleNetwork(nodes, plan, hosting, dev9)
        }

        if (navClicks >= MAX_NAV_CLICKS) {
            Log.w(TAG, "plafond de $MAX_NAV_CLICKS clics atteint, on rend la main")
            return false
        }

        // 2. The settings screen -> the Network tab.
        labels.of(Ps2Target.I18n.KEY_NETWORK_TAB, Ps2Target.LABEL_NETWORK)
            .firstNotNullOfOrNull { Ps2Screen.modeButton(nodes, it) }
            ?.let {
                Log.d(TAG, "ouverture de l'onglet Réseau")
                unknownPasses = 0
                navClicks++
                return it.live.click()
            }

        // 1. The menu -> Settings.
        labels.of(KEY_SETTINGS, Ps2Target.LABEL_SETTINGS)
            .firstNotNullOfOrNull { Ps2Screen.modeButton(nodes, it) }
            ?.let {
                Log.d(TAG, "ouverture des réglages")
                NetplayAutomation.report(NetplayProgress.OpeningMenu)
                unknownPasses = 0
                navClicks++
                return it.live.click()
            }

        // 0. The library -> the menu. The button has no translatable text: it
        //    is a glyph, and so much the better, it does not change language.
        Ps2Screen.modeButton(nodes, MENU_GLYPH)?.let {
            Log.d(TAG, "ouverture du menu")
            NetplayAutomation.report(NetplayProgress.OpeningMenu)
            unknownPasses = 0
            navClicks++
            return it.live.click()
        }

        // Unknown screen. We do not go back straight away.
        //
        // A screen mid-animation is an unknown screen: right after a click
        // ARMSX2's tree drops to a handful of nodes while the next page is
        // drawn. Going back at that instant undoes the click we just made, and
        // the driver starts going round in circles, menu, settings, back,
        // menu... up to the ceiling. Seen for real on 2026-08-17, on a tree of
        // 18 nodes.
        //
        // So we only insist after [UNKNOWN_BEFORE_BACK] lost passes in a row,
        // which leaves a transition plenty of time, and the counter goes back to
        // zero as soon as we recognise something.
        unknownPasses++
        if (unknownPasses < UNKNOWN_BEFORE_BACK) {
            Log.d(TAG, "écran inconnu (${nodes.size} nœuds), on laisse l'écran s'établir")
            return false
        }
        if (backs < MAX_BACKS) {
            backs++
            unknownPasses = 0
            Log.d(TAG, "écran non reconnu (${nodes.size} nœuds), retour ($backs)")
            return goBack()
        }
        Log.w(TAG, "écran non reconnu et $MAX_BACKS retours dépensés, on rend la main")
        return false
    }

    /** What we are in the middle of writing, set just before opening a row. */
    private var pendingValue: String? = null

    /**
     * Sets the Network screen up, one setting per pass, in the order in which
     * they depend on each other.
     *
     * The order is not cosmetic: changing mode redraws the bottom half of the
     * screen, the host's fields and the guest's are not the same, so a value
     * written beforehand would be lost.
     */
    private fun settleNetwork(
        nodes: List<Node>,
        plan: NetplayPlan,
        hosting: Boolean,
        dev9Label: Node?
    ): Boolean {
        // a. The network adapter, without which nothing that follows exists.
        //
        //    An absent label does not mean "no toggle" but "we have scrolled
        //    past it", and we do not scroll back up: the order of this list
        //    follows the order of the screen.
        if (dev9Label != null && STEP_DEV9 !in done) {
            val toggle = Ps2Screen.toggleFor(nodes, dev9Label.text)
            if (toggle != null && !toggle.checked) {
                Log.d(TAG, "activation de DEV9")
                NetplayAutomation.report(NetplayProgress.ChoosingMode)
                return toggle.live.click()
            }
            done += STEP_DEV9
        }

        // b. The mode. The three buttons are visible together, so there is no
        //    list to open, unlike Dolphin's connection type.
        //
        //    The current mode cannot be read off the button: measured, none of
        //    the three carries `selected` or `checked` in the tree. We infer it
        //    from the fields present, the way the Dolphin driver tells its tabs
        //    apart by the absence of the address field.
        if (STEP_MODE !in done) {
            val marker = if (hosting) Ps2Target.LABEL_OWN_ADDRESS else Ps2Target.LABEL_HOST_ADDRESS
            when {
                Ps2Screen.label(nodes, marker) != null -> done += STEP_MODE
                // One click, never two. The marker that confirms the mode sits
                // lower than the button: until we have scrolled it is absent
                // from the tree, and the driver concluded it had not clicked.
                // Seen for real on 2026-08-17, eight clicks in a row on "Host
                // local game" before the screen moved enough to set it straight.
                // After the click, we scroll down to find the marker.
                modeClicks > 0 -> return scroll(nodes, plan)
                else -> {
                    val wanted =
                        if (hosting) Ps2Target.LABEL_MODE_HOST else Ps2Target.LABEL_MODE_JOIN
                    val button = Ps2Screen.modeButton(nodes, wanted) ?: return scroll(nodes, plan)
                    Log.d(TAG, "passage en « $wanted »")
                    NetplayAutomation.report(NetplayProgress.ChoosingMode)
                    modeClicks++
                    return button.live.click()
                }
            }
        }

        NetplayAutomation.report(NetplayProgress.FillingForm)

        // c. The address, on the guest only, and it is a name, for want of a
        //    dot key. The host has nothing to enter: ARMSX2 shows its own
        //    addresses, tunnel included.
        if (!hosting && STEP_ADDRESS !in done) {
            val row = Ps2Screen.label(nodes, Ps2Target.LABEL_HOST_ADDRESS)
                ?: return scroll(nodes, plan)
            val current = Ps2Screen.valueFor(nodes, row.text)?.text?.trim()
            if (!current.equals(WgConfig.PS2_HOST_NAME, ignoreCase = true)) {
                return open(nodes, Ps2Target.LABEL_HOST_ADDRESS, WgConfig.PS2_HOST_NAME, plan)
            }
            done += STEP_ADDRESS
        }

        // d. The port: the same everywhere, "there is no automatic negotiation".
        if (STEP_PORT !in done) {
            Ps2Screen.label(nodes, Ps2Target.LABEL_PORT) ?: return scroll(nodes, plan)
            val port = plan.port.toString()
            if (Ps2Screen.valueFor(nodes, Ps2Target.LABEL_PORT)?.text?.trim() != port) {
                return open(nodes, Ps2Target.LABEL_PORT, port, plan)
            }
            done += STEP_PORT
        }

        // e. The room code: the session code, which both sides already know
        //    without anything having to be transmitted.
        val room = roomCode(plan)
        if (room != null && STEP_ROOM !in done) {
            Ps2Screen.label(nodes, Ps2Target.LABEL_ROOM_CODE) ?: return scroll(nodes, plan)
            val current = Ps2Screen.valueFor(nodes, Ps2Target.LABEL_ROOM_CODE)?.text?.trim()
            if (!current.equals(room, ignoreCase = true)) {
                return open(nodes, Ps2Target.LABEL_ROOM_CODE, room, plan)
            }
            done += STEP_ROOM
        }

        Log.d(TAG, "écran réseau réglé")
        NetplayAutomation.report(NetplayProgress.Done)
        onFinished(true)
        return true
    }

    /**
     * Scrolls the screen down a notch, having failed to find what we were after.
     *
     * The Network screen is taller than the device, and an accessibility tree
     * only contains what is drawn: a row below the fold is not in it at all.
     * This is the same trap as Azahar's OK button in landscape, in another form;
     * there we had to search without the visibility filter, here we have to
     * bring the row into view.
     *
     * Bounded: if scrolling never brings up what we are after, we hand back and
     * say what to set, rather than scrolling the screen forever under the
     * player's thumb.
     */
    private fun scroll(nodes: List<Node>, plan: NetplayPlan): Boolean {
        if (scrolls >= MAX_SCROLLS) {
            Log.w(TAG, "rien trouvé après $MAX_SCROLLS défilements")
            giveUp(plan, R.string.netplay_fields_filled)
            return true
        }
        val scrollable = nodes
            .filter { it.live.isScrollable && it.bounds.bottom - it.bounds.top > MIN_SCROLL_HEIGHT }
            .maxByOrNull { it.bounds.area }
        if (scrollable == null) {
            giveUp(plan, R.string.netplay_fields_filled)
            return true
        }
        scrolls++
        Log.d(TAG, "défilement ($scrolls)")
        return scrollable.live.performAction(
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.id
        )
    }

    /** Opens a row, remembering what we mean to write into it. */
    private fun open(nodes: List<Node>, label: String, value: String, plan: NetplayPlan): Boolean {
        if (!Ps2Screen.canType(value)) {
            // Never try: the keyboard has neither dot nor punctuation, and
            // typing half a value is worse than typing nothing.
            Log.w(TAG, "« $value » n'est pas saisissable sur ce clavier")
            giveUp(plan, R.string.netplay_automation_stopped)
            return true
        }
        if (writes >= MAX_WRITES) {
            Log.w(TAG, "« $label » ne retient pas ce qu'on y écrit, on rend la main")
            giveUp(plan, R.string.netplay_fields_filled)
            return true
        }
        val row = Ps2Screen.row(nodes, label)
        if (row == null) {
            giveUp(plan, R.string.netplay_fields_filled)
            return true
        }
        Log.d(TAG, "ouverture de « $label » pour y écrire « $value »")
        writes++
        pendingValue = value
        return row.live.click()
    }

    /**
     * Enters a value on ARMSX2's keyboard: clear, type, confirm.
     *
     * All in one pass, with the tree re-read between each key. Splitting it into
     * one pass per character would have looked safer: it would have made it
     * depend on one accessibility event per key, when nothing guarantees ARMSX2
     * emits one for each.
     */
    private fun type(first: List<Node>, value: String): Boolean {
        var nodes = first
        Ps2Screen.commandKey(nodes, Ps2Screen.KEY_CLEAR)?.live?.click()
        for (ch in value) {
            nodes = flatten(readTree() ?: return false)
            val key = Ps2Screen.key(nodes, ch)
            if (key == null) {
                Log.w(TAG, "touche « $ch » introuvable, saisie abandonnée")
                pendingValue = null
                return false
            }
            key.live.click()
        }
        nodes = flatten(readTree() ?: return false)
        pendingValue = null
        Log.d(TAG, "« $value » saisi, validation")
        return Ps2Screen.commandKey(nodes, Ps2Screen.KEY_DONE)?.live?.click() ?: false
    }

    /**
     * The room code, cut to ARMSX2's bounds.
     *
     * The session code is already the two players' shared secret, and it is
     * alphanumeric, hence typeable. Too short, and we do not invent one: better
     * to leave ARMSX2's own, identical on both sides only if the players copy it
     * across, than to set one the other will not have.
     */
    internal fun roomCode(plan: NetplayPlan): String? {
        val raw = plan.password?.filter { it.isLetterOrDigit() && it.code < 128 } ?: return null
        val cut = raw.take(Ps2Target.ROOM_CODE_LENGTH.last)
        return cut.takeIf { it.length >= Ps2Target.ROOM_CODE_LENGTH.first }
    }

    private fun giveUp(plan: NetplayPlan, message: Int) {
        NetplayAutomation.report(
            NetplayProgress.Failed(
                context.getString(message, EMULATOR, "${WgConfig.PS2_HOST_NAME}:${plan.port}")
            )
        )
        onFinished(false)
    }

    private fun flatten(root: AccessibilityNodeInfo): List<Node> =
        flattenRaw(root).map { node ->
            Node(
                text = node.text?.toString().orEmpty(),
                className = node.className?.toString().orEmpty(),
                bounds = node.bounds(),
                viewId = node.viewIdResourceName?.substringAfter(":id/").orEmpty(),
                description = node.contentDescription?.toString().orEmpty(),
                clickable = node.isClickable,
                checked = node.isChecked,
                handle = node
            )
        }

    private fun flattenRaw(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue += root
        while (queue.isNotEmpty() && out.size < MAX_NODES) {
            val node = queue.removeFirst()
            out += node
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue += it }
        }
        return out
    }

    private fun AccessibilityNodeInfo.bounds(): Bounds {
        val r = android.graphics.Rect().also { getBoundsInScreen(it) }
        return Bounds(r.left, r.top, r.right, r.bottom)
    }

    private val Node.live: AccessibilityNodeInfo get() = handle as AccessibilityNodeInfo

    /** Clicks the node, or the first ancestor that accepts a click. */
    private fun AccessibilityNodeInfo.click(): Boolean {
        var node: AccessibilityNodeInfo? = this
        var hops = 0
        while (node != null && hops < MAX_ANCESTOR_HOPS) {
            if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node = node.parent
            hops++
        }
        return false
    }

    private companion object {
        const val TAG = "Ps2Netplay"
        const val EMULATOR = "ARMSX2"

        /** ARMSX2's i18n key for "Settings". */
        const val KEY_SETTINGS = "action.settings"

        /** The library's menu button: a glyph, hence language-free. */
        const val MENU_GLYPH = "☰"

        const val STEP_DEV9 = "dev9"
        const val STEP_MODE = "mode"
        const val STEP_ADDRESS = "address"
        const val STEP_PORT = "port"
        const val STEP_ROOM = "room"

        /** The markers that say "we are on the Network screen", at any height. */
        val NETWORK_MARKERS = listOf(
            Ps2Target.LABEL_NETWORK_MODE,
            Ps2Target.LABEL_MODE_HOST,
            Ps2Target.LABEL_PORT,
            Ps2Target.LABEL_ROOM_CODE,
            Ps2Target.LABEL_OWN_ADDRESS,
            Ps2Target.LABEL_HOST_ADDRESS
        )

        /** A tab bar scrolls too: we only want the large container. */
        const val MIN_SCROLL_HEIGHT = 400
        const val MAX_SCROLLS = 8

        /** Enough to get out of a settings sub-screen, not enough to quit a game. */
        const val MAX_BACKS = 3

        /** Three fields, plus one retry each: past that, the screen is not reading us. */
        const val MAX_WRITES = 6
        const val MAX_ANCESTOR_HOPS = 5
        /**
         * The PS2 route is longer than the others: library, menu, settings, tab.
         * Four were enough for Dolphin, not here, and a ceiling set too low
         * reads as "the setup does not work".
         */
        const val MAX_NAV_CLICKS = 8

        /** Enough to let a transition draw before drawing conclusions. */
        const val UNKNOWN_BEFORE_BACK = 4
        const val MAX_NODES = 600
    }
}

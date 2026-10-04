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

/** Sets up ARMSX2's Network screen for Local Link. Its keyboard has no dot key, hence the `emufii` host name. */
class Ps2NetplayDriver(
    private val context: Context,
    private val readTree: () -> AccessibilityNodeInfo?,
    private val goBack: () -> Boolean,
    private val onFinished: (success: Boolean) -> Unit
) {

    private val labels by lazy { Ps2Labels(context) }

    private var navClicks = 0
    private var navPlan: NetplayPlan? = null

    private var scrolls = 0

    private var backs = 0

    /** One click is enough; two are a bug. */
    private var modeClicks = 0

    private var unknownPasses = 0

    private var writes = 0

    private val done = HashSet<String>()

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
        Log.d(TAG, "pass: ${nodes.size} nodes, role=${if (hosting) "host" else "guest"}")

        // The keyboard first: while it is open nothing else is reachable.
        if (Ps2Screen.keyboardIsOpen(nodes)) {
            val wanted = pendingValue ?: run {
                Log.w(TAG, "keyboard open with no value to type, closing")
                return Ps2Screen.commandKey(nodes, Ps2Screen.KEY_DONE)?.live?.click() ?: false
            }
            return type(nodes, wanted)
        }

        val dev9 = labels.of(Ps2Target.I18n.KEY_ENABLE_DEV9, Ps2Target.LABEL_ENABLE_DEV9)
            .firstNotNullOfOrNull { Ps2Screen.label(nodes, it) }
        val onNetworkScreen = dev9 != null || NETWORK_MARKERS.any { Ps2Screen.label(nodes, it) != null }
        if (onNetworkScreen) {
            unknownPasses = 0
            return settleNetwork(nodes, plan, hosting, dev9)
        }

        if (navClicks >= MAX_NAV_CLICKS) {
            Log.w(TAG, "cap of $MAX_NAV_CLICKS clicks reached, handing control back")
            return false
        }

        labels.of(Ps2Target.I18n.KEY_NETWORK_TAB, Ps2Target.LABEL_NETWORK)
            .firstNotNullOfOrNull { Ps2Screen.modeButton(nodes, it) }
            ?.let {
                Log.d(TAG, "opening the Network tab")
                unknownPasses = 0
                navClicks++
                return it.live.click()
            }

        labels.of(KEY_SETTINGS, Ps2Target.LABEL_SETTINGS)
            .firstNotNullOfOrNull { Ps2Screen.modeButton(nodes, it) }
            ?.let {
                Log.d(TAG, "opening settings")
                NetplayAutomation.report(NetplayProgress.OpeningMenu)
                unknownPasses = 0
                navClicks++
                return it.live.click()
            }

        Ps2Screen.modeButton(nodes, MENU_GLYPH)?.let {
            Log.d(TAG, "ouverture du menu")
            NetplayAutomation.report(NetplayProgress.OpeningMenu)
            unknownPasses = 0
            navClicks++
            return it.live.click()
        }

        // Don't go back yet: a screen mid-animation is unknown, and back would undo the click.
        unknownPasses++
        if (unknownPasses < UNKNOWN_BEFORE_BACK) {
            Log.d(TAG, "unknown screen (${nodes.size} nodes), letting it settle")
            return false
        }
        if (backs < MAX_BACKS) {
            backs++
            unknownPasses = 0
            Log.d(TAG, "unrecognised screen (${nodes.size} nodes), going back ($backs)")
            return goBack()
        }
        Log.w(TAG, "unrecognised screen and $MAX_BACKS backs spent, giving up")
        return false
    }

    private var pendingValue: String? = null

    /** One setting per pass, in dependency order: changing mode redraws the lower half. */
    private fun settleNetwork(
        nodes: List<Node>,
        plan: NetplayPlan,
        hosting: Boolean,
        dev9Label: Node?
    ): Boolean {
        if (dev9Label != null && STEP_DEV9 !in done) {
            val toggle = Ps2Screen.toggleFor(nodes, dev9Label.text)
            if (toggle != null && !toggle.checked) {
                Log.d(TAG, "activation de DEV9")
                NetplayAutomation.report(NetplayProgress.ChoosingMode)
                return toggle.live.click()
            }
            done += STEP_DEV9
        }

        // None of the mode buttons carries selected/checked; inferred from the fields.
        if (STEP_MODE !in done) {
            val marker = if (hosting) Ps2Target.LABEL_OWN_ADDRESS else Ps2Target.LABEL_HOST_ADDRESS
            when {
                Ps2Screen.label(nodes, marker) != null -> done += STEP_MODE
                modeClicks > 0 -> return scroll(nodes, plan)
                else -> {
                    val wanted =
                        if (hosting) Ps2Target.LABEL_MODE_HOST else Ps2Target.LABEL_MODE_JOIN
                    val button = Ps2Screen.modeButton(nodes, wanted) ?: return scroll(nodes, plan)
                    Log.d(TAG, "switching to \"$wanted\"")
                    NetplayAutomation.report(NetplayProgress.ChoosingMode)
                    modeClicks++
                    return button.live.click()
                }
            }
        }

        NetplayAutomation.report(NetplayProgress.FillingForm)

        if (!hosting && STEP_ADDRESS !in done) {
            val row = Ps2Screen.label(nodes, Ps2Target.LABEL_HOST_ADDRESS)
                ?: return scroll(nodes, plan)
            val current = Ps2Screen.valueFor(nodes, row.text)?.text?.trim()
            if (!current.equals(WgConfig.PS2_HOST_NAME, ignoreCase = true)) {
                return open(nodes, Ps2Target.LABEL_HOST_ADDRESS, WgConfig.PS2_HOST_NAME, plan)
            }
            done += STEP_ADDRESS
        }

        if (STEP_PORT !in done) {
            Ps2Screen.label(nodes, Ps2Target.LABEL_PORT) ?: return scroll(nodes, plan)
            val port = plan.port.toString()
            if (Ps2Screen.valueFor(nodes, Ps2Target.LABEL_PORT)?.text?.trim() != port) {
                return open(nodes, Ps2Target.LABEL_PORT, port, plan)
            }
            done += STEP_PORT
        }

        val room = roomCode(plan)
        if (room != null && STEP_ROOM !in done) {
            Ps2Screen.label(nodes, Ps2Target.LABEL_ROOM_CODE) ?: return scroll(nodes, plan)
            val current = Ps2Screen.valueFor(nodes, Ps2Target.LABEL_ROOM_CODE)?.text?.trim()
            if (!current.equals(room, ignoreCase = true)) {
                return open(nodes, Ps2Target.LABEL_ROOM_CODE, room, plan)
            }
            done += STEP_ROOM
        }

        Log.d(TAG, "network screen set")
        NetplayAutomation.report(NetplayProgress.Done)
        onFinished(true)
        return true
    }

    private fun scroll(nodes: List<Node>, plan: NetplayPlan): Boolean {
        if (scrolls >= MAX_SCROLLS) {
            Log.w(TAG, "nothing found after $MAX_SCROLLS scrolls")
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
        Log.d(TAG, "scrolling ($scrolls)")
        return scrollable.live.performAction(
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.id
        )
    }

    private fun open(nodes: List<Node>, label: String, value: String, plan: NetplayPlan): Boolean {
        if (!Ps2Screen.canType(value)) {
            // The keyboard has no dot or punctuation; typing half a value is worse than nothing.
            Log.w(TAG, "\"$value\" cannot be typed on this keyboard")
            giveUp(plan, R.string.netplay_automation_stopped)
            return true
        }
        if (writes >= MAX_WRITES) {
            Log.w(TAG, "\"$label\" does not keep what is typed into it, giving up")
            giveUp(plan, R.string.netplay_fields_filled)
            return true
        }
        val row = Ps2Screen.row(nodes, label)
        if (row == null) {
            giveUp(plan, R.string.netplay_fields_filled)
            return true
        }
        Log.d(TAG, "opening \"$label\" to type \"$value\"")
        writes++
        pendingValue = value
        return row.live.click()
    }

    private fun type(first: List<Node>, value: String): Boolean {
        var nodes = first
        Ps2Screen.commandKey(nodes, Ps2Screen.KEY_CLEAR)?.live?.click()
        for (ch in value) {
            nodes = flatten(readTree() ?: return false)
            val key = Ps2Screen.key(nodes, ch)
            if (key == null) {
                Log.w(TAG, "key \"$ch\" not found, input abandoned")
                pendingValue = null
                return false
            }
            key.live.click()
        }
        nodes = flatten(readTree() ?: return false)
        pendingValue = null
        Log.d(TAG, "\"$value\" typed, confirming")
        return Ps2Screen.commandKey(nodes, Ps2Screen.KEY_DONE)?.live?.click() ?: false
    }

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

        const val KEY_SETTINGS = "action.settings"

        const val MENU_GLYPH = "☰"

        const val STEP_DEV9 = "dev9"
        const val STEP_MODE = "mode"
        const val STEP_ADDRESS = "address"
        const val STEP_PORT = "port"
        const val STEP_ROOM = "room"

        val NETWORK_MARKERS = listOf(
            Ps2Target.LABEL_NETWORK_MODE,
            Ps2Target.LABEL_MODE_HOST,
            Ps2Target.LABEL_PORT,
            Ps2Target.LABEL_ROOM_CODE,
            Ps2Target.LABEL_OWN_ADDRESS,
            Ps2Target.LABEL_HOST_ADDRESS
        )

        const val MIN_SCROLL_HEIGHT = 400
        const val MAX_SCROLLS = 8

        /** Enough to get out of a settings sub-screen, not enough to quit a game. */
        const val MAX_BACKS = 3

        /** Three fields, plus one retry each: past that, the screen is not reading us. */
        const val MAX_WRITES = 6
        const val MAX_ANCESTOR_HOPS = 5
        const val MAX_NAV_CLICKS = 8

        /** Enough to let a transition draw before drawing conclusions. */
        const val UNKNOWN_BEFORE_BACK = 4
        const val MAX_NODES = 600
    }
}

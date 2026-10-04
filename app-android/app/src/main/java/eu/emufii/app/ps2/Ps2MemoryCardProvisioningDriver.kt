package eu.emufii.app.ps2

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class Ps2MemoryCardProvisioningDriver(
    context: Context,
    private val goBack: () -> Boolean,
    private val onFinished: (success: Boolean, reason: String?) -> Unit,
) {
    private val labels = Ps2Labels(context)
    private val route = Ps2ProvisioningRoute()
    private var currentPlan: Ps2ProvisioningPlan? = null
    private var navClicks = 0
    private var scrolls = 0
    private var drawerScrolls = 0
    private var unknownPasses = 0
    private var awaitingConfirmation = false
    private var rewinding = false
    private var drawerSeen = false
    private var stage = Stage.TARGET_SLOT1
    private var lastTrace: String? = null

    fun step(root: AccessibilityNodeInfo, plan: Ps2ProvisioningPlan): Boolean {
        if (currentPlan != plan) {
            currentPlan = plan
            route.reset()
            navClicks = 0
            scrolls = 0
            drawerScrolls = 0
            unknownPasses = 0
            awaitingConfirmation = false
            rewinding = false
            drawerSeen = false
            stage = if (plan.sourceCardForSlot2 != null) Stage.SOURCE_SLOT2 else Stage.TARGET_SLOT1
        }
        val nodes = flatten(root)
        val memoryLabels = labels.of("memcard.title", "Memory Cards")
        val slot1Labels = labels.of("memcard.slot1", "Slot 1")
        val slot1ActiveLabels = labels.of("memcard.slot1.active", "✓ Slot 1")
        val slot2Labels = labels.of("memcard.slot2", "Slot 2")
        val slot2ActiveLabels = labels.of("memcard.slot2.active", "✓ Slot 2")
        val bootBiosLabels = labels.of("bios.boot.title", "Boot BIOS")
        val achievementsLabels = labels.of("ra.title", "RetroAchievements")
        val openNavigationLabels = labels.of("games.overflow.openNavigation", "Open navigation")
        val biosLocationLabels = labels.of("setup.step.bios.title", "BIOS Location")
        val setupAgainLabels = labels.of("games.overflow.setup", "Setup Again")
        val okLabels = labels.of("action.ok", "OK")
        val onManager = memoryLabels.any { text(nodes, it) != null } &&
            (slot1Labels + slot2Labels).any { text(nodes, it) != null }
        val memoryItem = memoryLabels.firstNotNullOfOrNull { text(nodes, it) }
        val drawerTopMarker = (bootBiosLabels + achievementsLabels)
            .firstNotNullOfOrNull { text(nodes, it) }
        val drawerManagerMarker = (setupAgainLabels + biosLocationLabels)
            .firstNotNullOfOrNull { text(nodes, it) }
        // "Memory Cards" is below the landscape viewport; match the drawer by other rows or Home's button wins.
        val hasDrawerMarker = drawerTopMarker != null || drawerManagerMarker != null
        if (hasDrawerMarker) drawerSeen = true
        val inDrawer = hasDrawerMarker || (drawerSeen && !onManager)
        val onHome = openNavigationLabels.any { text(nodes, it) != null } || text(nodes, MENU_GLYPH) != null
        val screen = Ps2ProvisioningRoute.classify(onManager, inDrawer, onHome)
        val navigation = route.next(screen)
        trace(
            "screen=$screen action=$navigation manager=$onManager drawer=$inDrawer " +
                "target=${memoryItem != null} topMarker=${drawerTopMarker != null} " +
                "managerMarker=${drawerManagerMarker != null} nav=$navClicks " +
                "drawerScroll=$drawerScrolls cardScroll=$scrolls"
        )

        if (navigation == Ps2ProvisioningRoute.Action.USE_MEMORY_CARDS && onManager) {
            unknownPasses = 0
            if (awaitingConfirmation) {
                val ok = okLabels.firstNotNullOfOrNull { text(nodes, it) }
                if (ok != null) {
                    ok.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                    return ok.click()
                }
                // Only the active chip below finishes the flow: a click is not evidence.
                awaitingConfirmation = false
            }
            val scrollable = nodes.firstOrNull { it.isScrollable }
            if (rewinding) {
                if (scrollable != null && scrolls++ < MAX_SCROLLS &&
                    scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
                    return true
                }
                rewinding = false
                scrolls = 0
            }
            val desiredName = if (stage == Stage.SOURCE_SLOT2) plan.sourceCardForSlot2!! else plan.cardName
            val slotLabels = if (stage == Stage.SOURCE_SLOT2) slot2Labels else slot1Labels
            val activeLabels = if (stage == Stage.SOURCE_SLOT2) slot2ActiveLabels else slot1ActiveLabels
            val filename = nodes.firstOrNull { it.text?.toString() == desiredName }
            if (filename != null) {
                val row = ancestorWithAnyText(filename, slotLabels + activeLabels)
                if (row == null) return fail("ARMSX2 shows the card, but not its slot controls.")
                val rowNodes = flatten(row)
                if (activeLabels.any { text(rowNodes, it) != null }) {
                    if (stage == Stage.SOURCE_SLOT2) {
                        stage = Stage.TARGET_SLOT1
                        rewinding = true
                        scrolls = 0
                    } else {
                        onFinished(true, null)
                    }
                    return true
                }
                val slot = slotLabels.firstNotNullOfOrNull { text(rowNodes, it) }
                if (slot != null && slot.click()) {
                    awaitingConfirmation = true
                    Ps2ProvisioningAutomation.report(
                        if (stage == Stage.SOURCE_SLOT2) Ps2ProvisioningProgress.AssigningSlot2
                        else Ps2ProvisioningProgress.AssigningSlot1
                    )
                    return true
                }
                return fail("ARMSX2 refuses to assign $desiredName to its slot.")
            }
            if (scrollable != null && scrolls++ < MAX_SCROLLS &&
                scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                return true
            }
            return fail("Card $desiredName does not appear in ARMSX2.")
        }

        // Back out exactly once: re-clicking Back during the Compose animation loops.
        if (navigation == Ps2ProvisioningRoute.Action.BACK_TO_HOME) {
            if (navClicks++ >= MAX_NAV_CLICKS) return fail("Could not get back to the global ARMSX2 manager.")
            val changed = goBack()
            if (changed) route.performed(navigation)
            return changed
        }

        if (navigation == Ps2ProvisioningRoute.Action.OPEN_MEMORY_CARDS && inDrawer) {
            unknownPasses = 0
            if (memoryItem != null) {
                if (!memoryItem.isVisibleToUser) {
                    if (drawerScrolls++ >= MAX_DRAWER_SCROLLS) {
                        return fail("Memory Cards stays off screen in the ARMSX2 menu.")
                    }
                    if (memoryItem.performAction(
                            AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id
                        ) || memoryItem.ancestorScrollable()
                            ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true) {
                        return true
                    }
                    return fail("The ARMSX2 menu cannot scroll to Memory Cards.")
                }
                if (memoryItem.click()) {
                    route.performed(navigation)
                    drawerSeen = false
                    drawerScrolls = 0
                    scrolls = 0
                    Ps2ProvisioningAutomation.report(Ps2ProvisioningProgress.OpeningMemoryCards)
                    return true
                }
                return fail("The ARMSX2 menu shows Memory Cards, but refuses to open it.")
            }

            // The drawer is a `Column.verticalScroll()`: walk up from a drawer-only row.
            val drawerScrollable = (drawerTopMarker ?: drawerManagerMarker)
                ?.ancestorScrollable()
            if (drawerScrollable != null && drawerScrolls++ < MAX_DRAWER_SCROLLS &&
                drawerScrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                return true
            }
            return fail("The ARMSX2 menu is open, but Memory Cards stays out of reach.")
        }

        if (navigation == Ps2ProvisioningRoute.Action.OPEN_DRAWER) {
            val menu = openNavigationLabels.firstNotNullOfOrNull { text(nodes, it) } ?: text(nodes, MENU_GLYPH)
            if (menu == null) return false
            if (navClicks++ >= MAX_NAV_CLICKS) return fail("ARMSX2 navigation gave up.")
            if (menu.click()) {
                route.performed(navigation)
                Ps2ProvisioningAutomation.report(Ps2ProvisioningProgress.OpeningMemoryCards)
                return true
            }
        }

        if (navigation == Ps2ProvisioningRoute.Action.WAIT && screen != Ps2ProvisioningRoute.Screen.UNKNOWN) {
            return false
        }

        unknownPasses++
        if (unknownPasses < UNKNOWN_BEFORE_BACK) return false
        unknownPasses = 0
        if (navClicks++ >= MAX_NAV_CLICKS) return fail("Unrecognised ARMSX2 screen.")
        val changed = goBack()
        if (changed) route.performed(Ps2ProvisioningRoute.Action.BACK_TO_HOME)
        return changed
    }

    private fun fail(reason: String): Boolean {
        Log.w(TAG, reason)
        onFinished(false, reason)
        return true
    }

    private fun trace(message: String) {
        if (message == lastTrace) return
        lastTrace = message
        Log.d(TAG, message)
    }

    private fun text(nodes: List<AccessibilityNodeInfo>, wanted: String): AccessibilityNodeInfo? =
        nodes.firstOrNull {
            it.text?.toString()?.equals(wanted, ignoreCase = true) == true ||
                it.contentDescription?.toString()?.equals(wanted, ignoreCase = true) == true
        }

    private fun ancestorWithAnyText(
        node: AccessibilityNodeInfo,
        labels: List<String>,
    ): AccessibilityNodeInfo? {
        var candidate: AccessibilityNodeInfo? = node
        repeat(MAX_ANCESTOR_HOPS) {
            val current = candidate ?: return null
            val descendants = flatten(current)
            if (labels.any { text(descendants, it) != null }) return current
            candidate = current.parent
        }
        return null
    }

    private fun flatten(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue += root
        while (queue.isNotEmpty() && out.size < MAX_NODES) {
            val node = queue.removeFirst()
            out += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return out
    }

    private fun AccessibilityNodeInfo.click(): Boolean {
        var node: AccessibilityNodeInfo? = this
        repeat(MAX_ANCESTOR_HOPS) {
            val current = node ?: return false
            if (current.isClickable) return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node = current.parent
        }
        return false
    }

    private fun AccessibilityNodeInfo.ancestorScrollable(): AccessibilityNodeInfo? {
        var node: AccessibilityNodeInfo? = this
        repeat(MAX_ANCESTOR_HOPS) {
            val current = node ?: return null
            if (current.isScrollable) return current
            node = current.parent
        }
        return null
    }

    private companion object {
        const val TAG = "Ps2CardProvision"
        const val MENU_GLYPH = "☰"
        const val MAX_NODES = 700
        const val MAX_SCROLLS = 16
        const val MAX_DRAWER_SCROLLS = 8
        const val MAX_NAV_CLICKS = 10
        const val MAX_ANCESTOR_HOPS = 7
        const val UNKNOWN_BEFORE_BACK = 3
    }

    private enum class Stage { SOURCE_SLOT2, TARGET_SLOT1 }
}

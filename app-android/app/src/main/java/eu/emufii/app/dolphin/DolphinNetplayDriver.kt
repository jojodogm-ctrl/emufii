package eu.emufii.app.dolphin

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import eu.emufii.app.R
import eu.emufii.app.azahar.NetplayAutomation
import eu.emufii.app.azahar.NetplayPlan
import eu.emufii.app.azahar.NetplayProgress
import eu.emufii.app.netplay.NetplayLabels

class DolphinNetplayDriver(
    private val context: Context,
    private val onFinished: (success: Boolean) -> Unit
) {

    private val labels = HashMap<String, List<String>>()

    private var navClicks = 0
    private var navPlan: NetplayPlan? = null

    private var lobbyClicks = 0

    fun step(root: AccessibilityNodeInfo, pkg: String, plan: NetplayPlan): Boolean {
        // Reset before reading the screen, or a ceiling from the last session blocks the lobby.
        if (navPlan !== plan) {
            navPlan = plan
            navClicks = 0
            lobbyClicks = 0
            DolphinTreeDump.reset()
        }

        val nodes = flatten(root)
        val direct = labelsFor(pkg, DolphinTarget.LABEL_DIRECT_CONNECTION)
        val traversal = labelsFor(pkg, DolphinTarget.LABEL_TRAVERSAL_SERVER)
        Log.d(TAG, "pass: ${nodes.size} nodes, direct=${direct.size} labels")

        // Lobby first: it is the last screen and the form must not be touched again.
        val gameField = DolphinScreen.fieldFor(nodes, labelsFor(pkg, DolphinTarget.LABEL_GAME))
        if (gameField != null) return settleLobby(gameField, plan)

        // `lobbyClicks > 0` matters: without it this fires on the start-up grid and launches the game.
        val wantedGame = plan.preferredGame
        if (wantedGame != null && lobbyClicks > 0 && nodes.none { it.isField }) {
            DolphinScreen.looseOption(nodes, wantedGame)?.let {
                Log.d(TAG, "picking game \"${it.text}\" for \"$wantedGame\"")
                return it.live.click()
            }
        }

        // Direct connection, never Traversal: Traversal removes the port field.
        if (DolphinScreen.isDropdownOpen(nodes, direct, traversal)) {
            val option = DolphinScreen.option(nodes, direct)
            if (option == null) {
                giveUp(plan, R.string.netplay_automation_stopped)
                return true
            }
            Log.d(TAG, "picking direct connection")
            return option.live.click()
        }

        // The nickname field is on both tabs, so it identifies the form.
        if (DolphinScreen.fieldFor(nodes, labelsFor(pkg, DolphinTarget.LABEL_NICKNAME)) != null) {
            return fillForm(nodes, pkg, plan, direct)
        }

        if (navClicks >= MAX_NAV_CLICKS) {
            DolphinTreeDump.capture(context, pkg, nodes, "plafond de $MAX_NAV_CLICKS clics de navigation atteint")
            return false
        }

        // By text: appcompat renders menu titles into `id/title`, so the item id is not in the tree.
        DolphinScreen.option(nodes, labelsFor(pkg, DolphinTarget.LABEL_MENU_NETPLAY))?.let {
            Log.d(TAG, "opening netplay from the grid menu")
            NetplayAutomation.report(NetplayProgress.OpeningMenu)
            navClicks++
            return it.live.click()
        }

        val overflow = DolphinScreen.overflow(nodes, nodes.first().bounds)
        if (overflow == null) {
            Log.w(TAG, "overflow button not found among ${nodes.size} nodes")
            DolphinTreeDump.capture(context, pkg, nodes, "overflow button not found")
            return false
        }
        Log.d(TAG, "opening the overflow menu")
        NetplayAutomation.report(NetplayProgress.OpeningMenu)
        navClicks++
        return overflow.live.click()
    }

    private fun settleLobby(gameField: Node, plan: NetplayPlan): Boolean {
        val wanted = plan.preferredGame
        // The guest does not choose the game: for them the lobby is the destination.
        if (wanted == null) {
            Log.d(TAG, "room reached, no game to set")
            finishInLobby()
            return true
        }
        if (DolphinScreen.looselyMatches(gameField.text, wanted)) {
            Log.d(TAG, "room ready: \"${gameField.text}\" matches \"$wanted\"")
            finishInLobby()
            return true
        }
        if (lobbyClicks >= MAX_LOBBY_CLICKS) {
            Log.w(TAG, "game \"$wanted\" not in the list, room left as is")
            finishInLobby()
            return true
        }
        lobbyClicks++
        Log.d(TAG, "room shows \"${gameField.text}\", opening the game list")
        NetplayAutomation.report(NetplayProgress.ChoosingMode)
        return gameField.live.click()
    }

    private fun finishInLobby() {
        NetplayAutomation.report(NetplayProgress.Done)
        onFinished(true)
    }

    private fun fillForm(
        nodes: List<Node>,
        pkg: String,
        plan: NetplayPlan,
        direct: List<String>
    ): Boolean {
        val hosting = plan.role == NetplayPlan.Role.Host
        val roleLabels = labelsFor(
            pkg,
            if (hosting) DolphinTarget.LABEL_ROLE_HOST else DolphinTarget.LABEL_ROLE_CONNECT
        )
        val ipLabels = labelsFor(pkg, DolphinTarget.LABEL_IP_ADDRESS)

        // The host form has no address field; that is how the tabs are told apart.
        val onHostTab = DolphinScreen.fieldFor(nodes, ipLabels) == null
        if (onHostTab != hosting) {
            val tab = DolphinScreen.tab(nodes, roleLabels)
            if (tab == null) {
                giveUp(plan, R.string.netplay_automation_stopped)
                return true
            }
            Log.d(TAG, "switching to the ${if (hosting) "Host" else "Connect"} tab")
            NetplayAutomation.report(NetplayProgress.ChoosingMode)
            return tab.live.click()
        }

        // Set connection type before typing: switching it rebuilds the form.
        val typeField = DolphinScreen.fieldFor(
            nodes,
            labelsFor(pkg, DolphinTarget.LABEL_CONNECTION_TYPE)
        )
        if (typeField != null && !typeField.text.matches(direct)) {
            Log.d(TAG, "connection type is '${typeField.text}', opening the dropdown")
            NetplayAutomation.report(NetplayProgress.ChoosingMode)
            return typeField.live.click()
        }

        NetplayAutomation.report(NetplayProgress.FillingForm)

        // The guest points at the host; the host has no address to be given.
        var wrote = true
        if (!hosting) {
            val ip = DolphinScreen.fieldFor(nodes, ipLabels)
            if (ip == null) {
                giveUp(plan, R.string.netplay_automation_stopped)
                return true
            }
            wrote = ip.live.fillText(plan.ip)
        }

        DolphinScreen.fieldFor(nodes, labelsFor(pkg, DolphinTarget.LABEL_PORT))
            ?.live?.fillText(plan.port.toString())

        plan.username?.let { name ->
            DolphinScreen.fieldFor(nodes, labelsFor(pkg, DolphinTarget.LABEL_NICKNAME))
                ?.live?.fillText(name)
        }

        if (!wrote) {
            Log.w(TAG, "the address field refused ACTION_SET_TEXT")
            giveUp(plan, R.string.netplay_automation_stopped)
            return true
        }

        val commit = DolphinScreen.actionButton(nodes, roleLabels)
        if (commit == null) {
            giveUp(plan, R.string.netplay_fields_filled)
            return true
        }
        NetplayAutomation.report(NetplayProgress.Confirming)
        commit.live.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        // Eden's OK reports clickable and still does nothing, so check the result.
        if (!commit.live.click()) {
            giveUp(plan, R.string.netplay_fields_filled)
            return true
        }
        // No `Done` here: it clears the plan before the lobby is handled.
        Log.d(TAG, "form submitted, waiting for the room")
        return true
    }

    private fun giveUp(plan: NetplayPlan, message: Int) {
        NetplayAutomation.report(
            NetplayProgress.Failed(context.getString(message, EMULATOR, "${plan.ip}:${plan.port}"))
        )
        onFinished(false)
    }

    private fun labelsFor(pkg: String, name: String): List<String> =
        labels.getOrPut("$pkg/$name") { NetplayLabels.of(context, pkg, name) }

    private fun flatten(root: AccessibilityNodeInfo): List<Node> =
        flattenRaw(root).map { node ->
            Node(
                text = node.text?.toString().orEmpty(),
                className = node.className?.toString().orEmpty(),
                bounds = node.boundsOnScreen(),
                ancestorClasses = ancestorsOf(node),
                viewId = node.viewIdResourceName?.substringAfter(":id/").orEmpty(),
                description = node.contentDescription?.toString().orEmpty(),
                clickable = node.isClickable,
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

    private fun AccessibilityNodeInfo.boundsOnScreen(): Bounds {
        val r = android.graphics.Rect().also { getBoundsInScreen(it) }
        return Bounds(r.left, r.top, r.right, r.bottom)
    }

    private fun ancestorsOf(node: AccessibilityNodeInfo): List<String> {
        val out = ArrayList<String>()
        var parent = node.parent
        var hops = 0
        while (parent != null && hops < MAX_ANCESTOR_HOPS) {
            out += parent.className?.toString().orEmpty()
            parent = parent.parent
            hops++
        }
        return out
    }

    private val Node.live: AccessibilityNodeInfo
        get() = handle as AccessibilityNodeInfo

    private fun String.matches(labels: List<String>): Boolean =
        trim().lowercase() in labels.map { it.trim().lowercase() }

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

    /** Not named `setText`: a member function would shadow this extension. */
    private fun AccessibilityNodeInfo.fillText(value: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private companion object {
        const val TAG = "DolphinNetplay"

        const val EMULATOR = "Dolphin"
        const val MAX_ANCESTOR_HOPS = 5
        const val MAX_NAV_CLICKS = 4
        const val MAX_LOBBY_CLICKS = 3

        const val MAX_NODES = 600
    }
}

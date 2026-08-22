package eu.emufii.app.azahar

import eu.emufii.app.R
import eu.emufii.app.dolphin.DolphinNetplayDriver
import eu.emufii.app.ps2.Ps2NetplayDriver
import eu.emufii.app.ps2.Ps2Target
import eu.emufii.app.dolphin.DolphinTarget
import eu.emufii.app.netplay.NetplayLabels
import eu.emufii.app.netplay.NetplayTarget
import eu.emufii.app.netplay.NetplayUi
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Drives the netplay dialog of Azahar *and* Eden so the user doesn't have to
 * retype an IP that Emufii already knows.
 *
 * The class keeps its Azahar name even though it now serves both. Renaming it
 * would change the ComponentName, and the ComponentName is what Android stores
 * when the user switches the service on: a rename would silently turn the
 * automation off for everyone who had already enabled it. An inaccurate name is
 * the cheaper of the two costs. Which emulators it accepts is [NetplayTarget].
 *
 * Why an accessibility service at all: neither emulator exposes IPC for netplay.
 * Azahar's manifest exports only MainActivity and EmulationActivity, and netplay
 * lives behind JNI (`netPlayCreateRoom` / `netPlayJoinRoom`) with no intent
 * extras. Writing SharedPreferences would need root or `run-as`. Driving the UI
 * is the only path that works on an unmodified, sideloaded build.
 *
 * The service is inert unless [NetplayAutomation] holds a plan: it does nothing
 * on its own, and only ever touches the Azahar package.
 *
 * It is best-effort by design. Azahar is a moving target, when a resource id
 * moves, we stop rather than click something arbitrary, and the UI falls back
 * to showing the IP for manual entry.
 */
class AzaharNetplayService : AccessibilityService() {

    private var lastStepAt = 0L

    /**
     * How many times this plan has been walked towards the multiplayer screen.
     *
     * The navigation steps, the in-game drawer entry, the settings tab, the
     * Multiplayer card, are the only ones that fire on a screen the *player*
     * opened for their own reasons. An armed plan that never reaches a room form
     * therefore re-clicked Multiplayer every single time the in-game drawer
     * appeared, which is exactly when the player is trying to reach Quit: the
     * drawer became unusable. Reported from the Thor.
     *
     * A cap turns "forever" into "a couple of tries, then get out of the way".
     * Filling a form that is already on screen stays unlimited: that one only
     * ever fires where the player is doing what we asked.
     */
    private var navClicks = 0
    private var navPlan: NetplayPlan? = null

    private val handler = Handler(Looper.getMainLooper())

    /** The pending re-read, so a new burst of events replaces it instead of piling on. */
    private var pendingLook: Runnable? = null

    private val store by lazy { PlanStore(this) }

    /**
     * The Dolphin side of the automation.
     *
     * Held here rather than folded into [step] because Dolphin's netplay screen
     * is Compose and exposes no resource ids, nothing about it can be
     * expressed in the id-based walk the other two share. Keeping it behind its
     * own object means the 3DS and Switch paths cannot be reached, let alone
     * changed, by anything Dolphin does. It also caches the emulator's labels,
     * so it has to outlive a single event.
     */
    private val dolphinDriver by lazy {
        DolphinNetplayDriver(this) { success ->
            store.clear()
            if (success) comeBackToEmufii()
        }
    }

    /**
     * The PS2 side, third driver and third shape of screen.
     *
     * It is given the means to re-read the tree: entering a value in ARMSX2 means
     * a dozen clicks in a row on its own keyboard, and the screen redraws at every
     * key. The other two drivers do not need it, writing their fields with a
     * single `ACTION_SET_TEXT`.
     */
    private val ps2Driver by lazy {
        Ps2NetplayDriver(
            this,
            { rootInActiveWindow },
            { performGlobalAction(GLOBAL_ACTION_BACK) }
        ) { success ->
            store.clear()
            if (success) comeBackToEmufii()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // We may be starting because Android killed Emufii mid-flow and brought
        // the service back on its own.
        NetplayAutomation.restore(store)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        // Both emulators emit bursts of events per screen; one step per burst
        // is enough and keeps us from racing our own clicks.
        if (System.currentTimeMillis() - lastStepAt < STEP_DEBOUNCE_MS) return
        stepNow(pkg)
    }

    /**
     * Runs one step against whatever is on screen, and looks again shortly after.
     *
     * The second look is the point. Acting only on events assumes the last event
     * of a screen arrives after that screen is usable, and Azahar's multiplayer
     * sheet disproves it: the sheet slides in, the events all fire while its
     * buttons are still off-screen, and then nothing else happens, so a flow
     * that had just opened the sheet correctly sat there staring at it. Seen on
     * the Thor, and indistinguishable from "the automation never ran".
     *
     * A few spaced re-reads cost nothing when there is nothing to do, [step]
     * returns false and the chain stops, and they are the only thing that
     * catches a view arriving late.
     */
    private fun stepNow(pkg: String, looksLeft: Int = RECHECKS) {
        val plan = NetplayAutomation.plan.value ?: return
        // Dolphin's screen is Compose and carries no resource ids, so it cannot
        // be walked by [step] at all, it gets its own driver, and the two never
        // meet. See DolphinTarget. Everything above and below this line, and the
        // whole plan/store machinery, is shared unchanged.
        // Three families of screen, three drivers, and a single switchboard. A
        // package none of the three knows leaves without having touched
        // anything.
        val dolphin = DolphinTarget.owns(pkg)
        val ps2 = Ps2Target.owns(pkg)
        val target = if (dolphin || ps2) null else NetplayTarget.forPackage(pkg) ?: return
        val root = rootInActiveWindow ?: return
        val advanced = try {
            when {
                dolphin -> dolphinDriver.step(root, pkg, plan)
                ps2 -> ps2Driver.step(root, pkg, plan)
                else -> step(root, pkg, target!!, plan)
            }
        } catch (t: Throwable) {
            // Never let a malformed tree take down the service, the user would
            // lose accessibility until they toggled it back on by hand.
            Log.w(TAG, "netplay step failed", t)
            NetplayAutomation.report(
                NetplayProgress.Failed(getString(R.string.azahar_automation_stopped, "${plan.ip}:${plan.port}"))
            )
            false
        } finally {
            root.recycle()
        }
        if (advanced) lastStepAt = System.currentTimeMillis()
        pendingLook?.let { handler.removeCallbacks(it) }
        pendingLook = null
        // A pass that made progress earns its re-read budget back.
        //
        // Without that, the number of looks following an event is a ceiling on
        // the length of the route, and the PS2 goes well past it: menu, settings,
        // tab, mode, two scrolls, then a field opened, cleared, typed letter by
        // letter, confirmed. Azahar and Dolphin fit in three or four screens and
        // had never run into it.
        //
        // The driver then stopped halfway, after a scroll, without a word: from
        // the outside, "the automatic setup does not work". Renewing the budget
        // does not open an endless loop, each driver having its own ceilings, and
        // the plan is cleared as soon as it concludes, which the condition below
        // re-reads on every round.
        val looksNext = if (advanced) RECHECKS else looksLeft - 1
        if (looksNext > 0 && NetplayAutomation.plan.value != null) {
            val again = Runnable { stepNow(pkg, looksNext) }
            pendingLook = again
            handler.postDelayed(again, RECHECK_MS)
        }
    }

    /** Returns true if this event advanced the flow. */
    private fun step(
        root: AccessibilityNodeInfo,
        pkg: String,
        target: NetplayTarget,
        plan: NetplayPlan
    ): Boolean {
        // Work backwards: the furthest-along screen wins, so we never re-open a
        // menu we already left.

        // 3. Room form is up → fill it and confirm.
        val ipField = root.findById(pkg, NetplayUi.IP_ADDRESS)
        if (ipField != null) {
            Log.d(TAG, "filling room form as ${plan.role}: ${plan.ip}:${plan.port} room=${plan.roomName} user=${plan.username}")
            NetplayAutomation.report(NetplayProgress.FillingForm)
            val wrote = ipField.fillText(plan.ip)
            root.findAnywhere(pkg, NetplayUi.IP_PORT)?.fillText(plan.port.toString())
            // The nickname is only written when the plan carries one, that is,
            // on Eden, and for both roles. Two players with the same nickname
            // cannot share a room, and Eden ships the same one to everybody by
            // default: without this, two Emufii players turn up there as the same
            // person and the second is refused.
            //
            // On Azahar the plan leaves it null, and that is not an oversight:
            // Emufii used to write the profile name in, which replaced a valid
            // nickname with a two-letter one the form refused, "Invalid address or
            // name is too short!", a fault blamed on a perfectly good address. The
            // help card says where to change it.
            plan.username?.let { root.findAnywhere(pkg, NetplayUi.USERNAME)?.fillText(it) }
            // The password, when the room has one, that is, when it runs on the
            // VPS. It listens there on a public port: with no password, a stranger
            // walks into the game. It is the session code, which both players
            // already know, so there is nothing to transmit.
            plan.password?.let { root.findAnywhere(pkg, NetplayUi.PASSWORD)?.fillText(it) }
            if (plan.role == NetplayPlan.Role.Host) {
                plan.roomName?.let { root.findAnywhere(pkg, NetplayUi.ROOM_NAME)?.fillText(it) }
                // Eden refuses to create a room without one: its dropdown shows
                // "Required" in red and keeps OK disabled. Azahar has no such
                // field, so this is simply absent there and costs nothing.
                plan.preferredGame?.let { game ->
                    NetplayUi.PREFERRED_GAME_IDS
                        .firstNotNullOfOrNull { root.findAnywhere(pkg, it) }
                        ?.fillText(game)
                }
            }

            // A field that refused the write is worth saying out loud: it is the
            // one failure that used to look like a success. ACTION_SET_TEXT
            // returns false on a node that won't take it, and the guest's form
            // is the case nobody had watched.
            if (!wrote) {
                Log.w(TAG, "ip_address refused ACTION_SET_TEXT (role=${plan.role})")
                NetplayAutomation.report(
                    NetplayProgress.Failed(getString(R.string.azahar_automation_stopped, "${plan.ip}:${plan.port}"))
                )
                store.clear()
                return true
            }

            // Not [findById]: the confirm button is usually *below the fold*.
            // The form is a bottom sheet that scrolls, and on Azahar's create
            // dialog, one field taller than join, on a landscape screen, OK
            // starts off-screen. A visible-only lookup found nothing and Emufii
            // fell back to "fields are filled, press OK yourself", which is the
            // "the click doesn't take" this project had been chasing: there was
            // never a click. Bring it into view, then press it.
            val confirm = root.findAnywhere(pkg, NetplayUi.BTN_CONFIRM)
            confirm?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            if (confirm == null) {
                NetplayAutomation.report(
                    NetplayProgress.Failed(getString(R.string.azahar_fields_filled))
                )
                store.clear()
                return true
            }
            NetplayAutomation.report(NetplayProgress.Confirming)
            // A click that doesn't take is not a success. Eden's OK button
            // reports itself enabled and clickable and still ignores the action
            // - seen on a device, dialog left open, so believing our own
            // request would tell the player everything was done while the room
            // was never created. Say what actually happened instead: the fields
            // are filled, the last tap is theirs.
            if (!confirm.performClick()) {
                NetplayAutomation.report(
                    NetplayProgress.Failed(getString(R.string.azahar_fields_filled))
                )
                store.clear()
                return true
            }
            NetplayAutomation.report(NetplayProgress.Done)
            store.clear()
            comeBackToEmufii()
            return true
        }

        // 2. Multiplayer sheet is up → pick create or join.
        //
        // The host creates the room and everyone else joins it: a guest that
        // creates its own room joins nothing, and a host that joins looks for a
        // room nobody has opened yet. Logged because the two failures look
        // identical from the outside, a dialog that fills in and then refuses,
        // and the only way to tell them apart is to know which button was taken.
        //
        // The sheet is *up* when any one of its three buttons is on screen,
        // that is what tells us where we are. Which button to press is then
        // looked up without the visibility filter, because Azahar stacks them
        // Lobby / Join / Create, and a bottom sheet on a landscape screen
        // cuts off the bottom one. So the host's button was the one search
        // could never find, while the guest's sat comfortably in view: the
        // automation looked like it had a broken idea of who hosts, when it
        // simply had the same visible-only lookup that already hid btn_confirm
        // and room_name on this device. Bring it into view, then press it.
        val sheetUp = SHEET_BUTTONS.any { root.findById(pkg, it) != null }
        if (sheetUp) {
            val modeId =
                if (plan.role == NetplayPlan.Role.Host) NetplayUi.BTN_CREATE else NetplayUi.BTN_JOIN
            val modeNode = root.findAnywhere(pkg, modeId)
            if (modeNode != null) {
                Log.d(TAG, "role=${plan.role} → clicking $modeId")
                NetplayAutomation.report(NetplayProgress.ChoosingMode)
                modeNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                if (modeNode.performClick()) return true
                Log.w(TAG, "$modeId refused the click (role=${plan.role})")
            } else {
                // Not in the tree at all: an upstream rename, not a layout that
                // scrolled. Say so rather than walking back to the settings hub,
                // which would re-open the sheet we are already looking at.
                Log.w(TAG, "multiplayer sheet up but $modeId absent (role=${plan.role})")
            }
            NetplayAutomation.report(
                NetplayProgress.Failed(
                    getString(R.string.azahar_automation_stopped, "${plan.ip}:${plan.port}")
                )
            )
            store.clear()
            return true
        }

        // Everything below walks the player towards the sheet rather than acting
        // on a screen they asked for, so it is the part that must know when to
        // give up, see [navClicks].
        if (navPlan !== plan) {
            navPlan = plan
            navClicks = 0
        }
        if (navClicks >= MAX_NAV_CLICKS) return false

        // 1. In-game menu is up → open Multiplayer. Only Azahar has one; on
        // Eden the sheet is reached from the app's settings by the player, so
        // there is nothing to click here and the flow simply starts at step 2.
        target.inGameMenuId?.let { menuId ->
            root.findById(pkg, menuId)?.let {
                NetplayAutomation.report(NetplayProgress.OpeningMenu)
                navClicks++
                it.performClick()
                return true
            }
        }

        // 0b. Emulator's settings hub is up → click its Multiplayer card.
        //
        // The rows of this list all carry the same ids, so the row is found by
        // its text, the emulator's own, read from the emulator's resources, so
        // it matches whatever language it runs in.
        //
        // Two things this has to survive, both seen on the Thor with Eden:
        // the row can be scrolled past the bottom of the list, in which case it
        // is in the tree but not visible; and the label can be one of two
        // strings, because the settings hub shows a title *and* a description
        // and only one of them is what upstream calls "multiplayer" in a given
        // build. Failing either way looked identical from outside, the
        // emulator opened on its game grid and nothing else happened.
        val listId = (listOfNotNull(target.homeListId) + target.extraListIds)
            .firstOrNull { root.findById(pkg, it) != null }
        if (listId != null) {
            val labels = NetplayLabels.MULTIPLAYER_STRINGS
                .flatMap { NetplayLabels.of(this, pkg, it) }
                .map { it.trim().lowercase() }
            if (labels.isEmpty()) {
                Log.w(TAG, "no multiplayer label in $pkg's resources to match a card on")
                return false
            }
            // Not [findAllById]: a row below the fold is a real row, and the
            // list scrolls to it happily once asked. Two id families, because
            // the gear and the tab do not land on the same kind of list.
            val titles = NetplayUi.ROW_TITLE_IDS.flatMap { id ->
                root.findAccessibilityNodeInfosByViewId(NetplayUi.id(pkg, id)).orEmpty()
            }
            val card = titles.firstOrNull { node ->
                node.text?.toString()?.trim()?.lowercase() in labels
            }
            if (card != null) {
                Log.d(TAG, "opening the '${card.text}' card in $pkg")
                NetplayAutomation.report(NetplayProgress.OpeningMenu)
                navClicks++
                card.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                card.performClick()
                return true
            }
            // Nothing matched. The row may simply not be in the tree: a
            // recycling list only holds what it has drawn, so a Multiplayer
            // entry far enough down does not exist to be found until the
            // list has been scrolled. One scroll per pass, counted like a
            // click so it cannot loop.
            val list = root.findById(pkg, listId)
            if (list != null && list.isScrollable) {
                navClicks++
                if (list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                    Log.d(TAG, "multiplayer card not in view, scrolling the settings list")
                    return true
                }
            }
            // Says what was on screen instead of failing mute: the next
            // mismatch then names itself rather than needing a dump.
            Log.w(
                TAG,
                "no card matching $labels; saw " + titles.map { it.text }
            )
            return false
        }

        // 0a. Emulator is on its game grid, so open the settings that hold
        // Multiplayer, a tab at the bottom on one emulator, a gear in the top
        // bar on the other. Whichever is on screen is the right one; the other
        // is simply absent.
        val settingsEntries = listOfNotNull(target.homeNavId) + target.homeSettingsButtonIds
        for (entryId in settingsEntries) {
            root.findById(pkg, entryId)?.let {
                Log.d(TAG, "opening $pkg's settings via $entryId")
                NetplayAutomation.report(NetplayProgress.OpeningMenu)
                navClicks++
                it.performClick()
                return true
            }
        }

        // Nothing we recognise on screen. Not an error: the player is probably
        // still in the game, or hasn't opened Multiplayer yet.
        return false
    }

    /**
     * A node the player can currently see.
     *
     * Visibility is what tells "this screen is up" from "this screen exists
     * somewhere in the hierarchy", so it gates every decision about *where we
     * are*. It is the wrong filter for acting on a form we have already decided
     * is up, see [findAnywhere].
     */
    /**
     * Brings Emufii back to the front, the room having been joined.
     *
     * The player asked for a setup step, not for a trip into another app: they
     * tapped a button in Emufii and the next thing they need is the button below
     * it. Leaving them inside the emulator's settings meant finding their own
     * way home before they could start the game.
     *
     * Delayed, because the emulator is still acting on the click we just made,
     * coming back instantly would race its own "joined" toast. Best-effort: if
     * the platform refuses the launch, the flow is finished either way and the
     * player simply switches back by hand.
     */
    private fun comeBackToEmufii() {
        val home = packageManager.getLaunchIntentForPackage(packageName) ?: return
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        handler.postDelayed({
            runCatching { startActivity(home) }
                .onFailure { Log.w(TAG, "could not bring Emufii back", it) }
        }, COME_BACK_MS)
    }

    private fun AccessibilityNodeInfo.findById(pkg: String, id: String): AccessibilityNodeInfo? =
        findAllById(pkg, id).firstOrNull()

    /** A node in the tree, on screen or scrolled past the edge of it. */
    private fun AccessibilityNodeInfo.findAnywhere(pkg: String, id: String): AccessibilityNodeInfo? =
        findAccessibilityNodeInfosByViewId(NetplayUi.id(pkg, id))?.firstOrNull()

    private fun AccessibilityNodeInfo.findAllById(pkg: String, id: String): List<AccessibilityNodeInfo> =
        findAccessibilityNodeInfosByViewId(NetplayUi.id(pkg, id))
            ?.filter { it.isVisibleToUser }
            .orEmpty()

    /** Clicks the node, or the nearest ancestor that will take a click. */
    private fun AccessibilityNodeInfo.performClick(): Boolean {
        var node: AccessibilityNodeInfo? = this
        var hops = 0
        while (node != null && hops < MAX_ANCESTOR_HOPS) {
            if (node.isClickable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            node = node.parent
            hops++
        }
        return false
    }

    /**
     * Types [value] into the field.
     *
     * Deliberately not called `setText`: `AccessibilityNodeInfo` already has
     * a member of that name, and in Kotlin a member always beats an extension.
     * The extension was therefore never called, every fill went to the platform
     * setter, which throws `Cannot perform this action on a sealed instance` on
     * any node that came out of a query, i.e. all of them. The automation had
     * been failing on its very first field since it was written; nobody saw it
     * because Azahar's in-game menu has never run on a device (M16), and because
     * the emulator pre-fills its own address, which for a host happens to be the
     * right answer. A green-looking test proving nothing.
     */
    private fun AccessibilityNodeInfo.fillText(value: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        // Only the in-memory copy: the service is destroyed every time the
        // process is recycled, which is exactly when the stored plan has to
        // survive.
        NetplayAutomation.clear()
    }

    private companion object {
        const val TAG = "AzaharNetplay"
        const val STEP_DEBOUNCE_MS = 250L
        const val MAX_ANCESTOR_HOPS = 5

        /**
         * Any one of these being on screen means the multiplayer sheet is up.
         *
         * Three rather than one because only the topmost is reliably in view,
         * and which one that is belongs to the emulator's layout, not to us.
         */
        val SHEET_BUTTONS = listOf(
            NetplayUi.BTN_LOBBY_BROWSER,
            NetplayUi.BTN_JOIN,
            NetplayUi.BTN_CREATE
        )

        /**
         * Tabs, cards and drawer entries we will click before concluding that
         * the player is doing something else. Four covers the longest real path
         * - settings tab, Multiplayer card, twice over.
         */
        const val MAX_NAV_CLICKS = 4

        /** How long, and how many times, to keep looking after something moved. */
        const val RECHECK_MS = 500L
        const val RECHECKS = 6

        /** Long enough for the emulator to act on the confirm we just clicked. */
        const val COME_BACK_MS = 1500L
    }
}

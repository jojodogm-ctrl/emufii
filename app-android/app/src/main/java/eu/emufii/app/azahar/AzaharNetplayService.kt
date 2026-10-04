package eu.emufii.app.azahar

import eu.emufii.app.netplay.NetplayNames
import eu.emufii.app.R
import eu.emufii.app.dolphin.DolphinNetplayDriver
import eu.emufii.app.ps2.Ps2NetplayDriver
import eu.emufii.app.ps2.Ps2MemoryCardProvisioningDriver
import eu.emufii.app.ps2.Ps2ProvisioningAutomation
import eu.emufii.app.ps2.Ps2ProvisioningProgress
import eu.emufii.app.ps2.Ps2ProvisioningStore
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

/** Drives Azahar and Eden netplay dialogs. Do not rename: Android stores the ComponentName of an enabled service. */
class AzaharNetplayService : AccessibilityService() {

    private var lastStepAt = 0L

    private var navClicks = 0
    private var navPlan: NetplayPlan? = null

    private val handler = Handler(Looper.getMainLooper())

    /** The pending re-read, so a new burst of events replaces it instead of piling on. */
    private var pendingLook: Runnable? = null
    private var refusedPlan: NetplayPlan? = null
    private var refusedClicks = 0

    private val store by lazy { PlanStore(this) }
    private val ps2ProvisioningStore by lazy { Ps2ProvisioningStore(this) }

    private val ps2ProvisioningDriver by lazy {
        Ps2MemoryCardProvisioningDriver(
            this,
            { performGlobalAction(GLOBAL_ACTION_BACK) },
        ) { success, reason ->
            val plan = Ps2ProvisioningAutomation.plan.value
            if (success && plan != null) {
                Ps2ProvisioningAutomation.complete(this, plan, ps2ProvisioningStore)
            } else {
                Ps2ProvisioningAutomation.fail(
                    reason ?: "The global ARMSX2 configuration could not be verified.",
                    ps2ProvisioningStore,
                )
            }
            comeBackToEmufii()
        }
    }

    private val dolphinDriver by lazy {
        DolphinNetplayDriver(this) { success ->
            store.clear()
            if (success) comeBackToEmufii()
        }
    }

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
        NetplayAutomation.restore(store)
        Ps2ProvisioningAutomation.restore(ps2ProvisioningStore)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (System.currentTimeMillis() - lastStepAt < STEP_DEBOUNCE_MS) return
        stepNow(pkg)
    }

    private fun stepNow(pkg: String, looksLeft: Int = RECHECKS) {
        if (Ps2Target.owns(pkg) && Ps2ProvisioningAutomation.plan.value != null) {
            stepProvisioningNow(pkg, looksLeft)
            return
        }
        val plan = NetplayAutomation.plan.value ?: return
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
            // A crash here would disable the accessibility service until the user re-enables it.
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
        // Progress refunds the budget, or long routes (PS2) stop halfway.
        val looksNext = if (advanced) RECHECKS else looksLeft - 1
        if (looksNext > 0 && NetplayAutomation.plan.value != null) {
            val again = Runnable { stepNow(pkg, looksNext) }
            pendingLook = again
            handler.postDelayed(again, RECHECK_MS)
        }
    }

    private fun stepProvisioningNow(pkg: String, looksLeft: Int) {
        if (Ps2ProvisioningAutomation.expireIfNeeded(ps2ProvisioningStore)) {
            comeBackToEmufii()
            return
        }
        val plan = Ps2ProvisioningAutomation.plan.value ?: return
        if (!Ps2Target.owns(pkg)) return
        val root = rootInActiveWindow
        if (root == null) {
            if (looksLeft > 1) {
                val again = Runnable { stepProvisioningNow(pkg, looksLeft - 1) }
                pendingLook = again
                handler.postDelayed(again, RECHECK_MS)
            }
            return
        }
        val advanced = try {
            ps2ProvisioningDriver.step(root, plan)
        } catch (t: Throwable) {
            Log.w(TAG, "PS2 provisioning step failed", t)
            Ps2ProvisioningAutomation.fail(
                t.message ?: "The ARMSX2 screen could not be read.",
                ps2ProvisioningStore,
            )
            comeBackToEmufii()
            false
        } finally {
            root.recycle()
        }
        if (advanced) lastStepAt = System.currentTimeMillis()
        pendingLook?.let(handler::removeCallbacks)
        pendingLook = null
        val looksNext = if (advanced) RECHECKS else looksLeft - 1
        if (looksNext > 0 && Ps2ProvisioningAutomation.plan.value != null) {
            val again = Runnable { stepProvisioningNow(pkg, looksNext) }
            pendingLook = again
            handler.postDelayed(again, RECHECK_MS)
        }
    }

    private fun step(
        root: AccessibilityNodeInfo,
        pkg: String,
        target: NetplayTarget,
        plan: NetplayPlan
    ): Boolean {

        // 3. Room form is up.
        val ipField = root.findById(pkg, NetplayUi.IP_ADDRESS)
        if (ipField != null) {
            Log.d(TAG, "filling room form as ${plan.role}: ${plan.ip}:${plan.port} room=${plan.roomName} user=${plan.username}")
            NetplayAutomation.report(NetplayProgress.FillingForm)
            val wrote = ipField.fillText(plan.ip)
            root.findAnywhere(pkg, NetplayUi.IP_PORT)?.fillText(plan.port.toString())
            // Both roles. On Azahar only over its default: a nickname the player chose stays.
            plan.username?.let { name ->
                root.findAnywhere(pkg, NetplayUi.USERNAME)?.let { field ->
                    val current = if (field.isShowingHintText) null else field.text?.toString()
                    if (!plan.usernameOverDefaultOnly || NetplayNames.isDefaultUsername(current)) {
                        field.fillText(name)
                    }
                }
            }
            plan.password?.let { root.findAnywhere(pkg, NetplayUi.PASSWORD)?.fillText(it) }
            if (plan.role == NetplayPlan.Role.Host) {
                plan.roomName?.let { root.findAnywhere(pkg, NetplayUi.ROOM_NAME)?.fillText(it) }
                plan.preferredGame?.let { game ->
                    NetplayUi.PREFERRED_GAME_IDS
                        .firstNotNullOfOrNull { root.findAnywhere(pkg, it) }
                        ?.fillText(game)
                }
            }

            // ACTION_SET_TEXT returns false on a node that won't take it.
            if (!wrote) {
                Log.w(TAG, "ip_address refused ACTION_SET_TEXT (role=${plan.role})")
                NetplayAutomation.report(
                    NetplayProgress.Failed(getString(R.string.azahar_automation_stopped, "${plan.ip}:${plan.port}"))
                )
                store.clear()
                return true
            }

            // Not findById: OK is often below the fold and the visible-only lookup misses it.
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
            // Eden's OK reports enabled and clickable and still ignores the action.
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

        // Azahar's update prompt sits over every screen until answered.
        if (dismissUpdatePrompt(root, pkg)) return true

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
                // A sheet still sliding up refuses the click; retry rather than give up.
                if (refusedPlan !== plan) {
                    refusedPlan = plan
                    refusedClicks = 0
                }
                if (++refusedClicks < MAX_REFUSED_CLICKS) {
                    Log.d(TAG, "$modeId refused the click, looking again ($refusedClicks)")
                    return false
                }
                Log.w(TAG, "$modeId refused the click (role=${plan.role})")
            } else {
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

        if (navPlan !== plan) {
            navPlan = plan
            navClicks = 0
        }
        if (navClicks >= MAX_NAV_CLICKS) return false

        target.inGameMenuId?.let { menuId ->
            root.findById(pkg, menuId)?.let {
                NetplayAutomation.report(NetplayProgress.OpeningMenu)
                navClicks++
                it.performClick()
                return true
            }
        }

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
            val list = root.findById(pkg, listId)
            if (list != null && list.isScrollable) {
                navClicks++
                if (list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                    Log.d(TAG, "multiplayer card not in view, scrolling the settings list")
                    return true
                }
            }
            Log.w(
                TAG,
                "no card matching $labels; saw " + titles.map { it.text }
            )
            return false
        }

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

        return false
    }

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

    private fun AccessibilityNodeInfo.findAnywhere(pkg: String, id: String): AccessibilityNodeInfo? =
        findAccessibilityNodeInfosByViewId(NetplayUi.id(pkg, id))?.firstOrNull()

    private fun AccessibilityNodeInfo.findAllById(pkg: String, id: String): List<AccessibilityNodeInfo> =
        findAccessibilityNodeInfosByViewId(NetplayUi.id(pkg, id))
            ?.filter { it.isVisibleToUser }
            .orEmpty()

    private val updateTitles = HashMap<String, List<String>>()

    /** The dialog is recognised by Azahar's own title, in every language it ships. */
    private fun dismissUpdatePrompt(root: AccessibilityNodeInfo, pkg: String): Boolean {
        val titles = updateTitles.getOrPut(pkg) {
            NetplayLabels.of(this, pkg, NetplayLabels.UPDATE_AVAILABLE)
        }
        if (titles.none { root.findAccessibilityNodeInfosByText(it).isNotEmpty() }) return false
        val cancel = root.findAccessibilityNodeInfosByViewId(DIALOG_NEGATIVE).firstOrNull()
            ?: return false
        Log.d(TAG, "dismissing $pkg's update prompt")
        return cancel.performClick()
    }

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

    /** Not named setText: a platform member of that name would win over the extension. */
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
        // In-memory only: the service dies on process recycle, the stored plan must survive.
        NetplayAutomation.clear()
        Ps2ProvisioningAutomation.clear()
    }

    private companion object {
        const val TAG = "AzaharNetplay"
        const val STEP_DEBOUNCE_MS = 250L
        const val MAX_ANCESTOR_HOPS = 5

        val SHEET_BUTTONS = listOf(
            NetplayUi.BTN_LOBBY_BROWSER,
            NetplayUi.BTN_JOIN,
            NetplayUi.BTN_CREATE
        )

        const val MAX_NAV_CLICKS = 4

        const val RECHECK_MS = 500L

        /** An AlertDialog's negative button, "Cancel" on Azahar's update prompt. */
        const val DIALOG_NEGATIVE = "android:id/button2"

        /** Six looks at [RECHECK_MS]: three seconds for a sheet to finish rising. */
        const val MAX_REFUSED_CLICKS = 6
        const val RECHECKS = 6

        const val COME_BACK_MS = 1500L
    }
}

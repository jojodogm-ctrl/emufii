package eu.emufii.app.ps2

/** Compose keeps the previous screen in the a11y tree while animating; this latch stops double clicks. */
internal class Ps2ProvisioningRoute {
    enum class Screen { HOME, DRAWER, MEMORY_CARDS, UNKNOWN }
    enum class Action { OPEN_DRAWER, OPEN_MEMORY_CARDS, BACK_TO_HOME, USE_MEMORY_CARDS, WAIT }

    private var expected: Screen? = null
    private var stalePasses = 0
    private var enteredThroughGlobalDrawer = false

    fun reset() {
        expected = null
        stalePasses = 0
        enteredThroughGlobalDrawer = false
    }

    fun next(screen: Screen): Action {
        if (screen == expected) {
            expected = null
            stalePasses = 0
        } else if (expected != null) {
            stalePasses++
            if (stalePasses < RETRY_AFTER_PASSES) return Action.WAIT
            expected = null
            stalePasses = 0
        }
        return when (screen) {
            Screen.HOME -> Action.OPEN_DRAWER
            Screen.DRAWER -> Action.OPEN_MEMORY_CARDS
            Screen.MEMORY_CARDS -> if (enteredThroughGlobalDrawer) {
                Action.USE_MEMORY_CARDS
            } else {
                Action.BACK_TO_HOME
            }
            Screen.UNKNOWN -> Action.WAIT
        }
    }

    fun performed(action: Action) {
        expected = when (action) {
            Action.OPEN_DRAWER -> Screen.DRAWER
            Action.OPEN_MEMORY_CARDS -> {
                enteredThroughGlobalDrawer = true
                Screen.MEMORY_CARDS
            }
            Action.BACK_TO_HOME -> {
                enteredThroughGlobalDrawer = false
                Screen.HOME
            }
            Action.USE_MEMORY_CARDS, Action.WAIT -> null
        }
        stalePasses = 0
    }

    companion object {
        private const val RETRY_AFTER_PASSES = 4

        fun classify(onManager: Boolean, inDrawer: Boolean, onHome: Boolean): Screen = when {
            inDrawer -> Screen.DRAWER
            onManager -> Screen.MEMORY_CARDS
            onHome -> Screen.HOME
            else -> Screen.UNKNOWN
        }
    }
}

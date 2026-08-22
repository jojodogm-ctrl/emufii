package eu.emufii.app.ps2

import eu.emufii.app.dolphin.Bounds
import eu.emufii.app.dolphin.Node

/**
 * Reading ARMSX2's Settings -> Network screen.
 *
 * [Node] and [Bounds] are borrowed from the Dolphin side rather than
 * redeclared: they are inert data, with nothing emulator-specific about them,
 * and two copies would drift. Nothing else is shared, the rules below are
 * ARMSX2's own and are called only by [Ps2NetplayDriver].
 *
 * The shape of this screen, taken with `uiautomator` on the Thor, resembles
 * neither of the other two:
 *
 * - On Dolphin the label is inside the field (Compose), so we search by
 *   nesting.
 * - Here, label and value are two sibling `TextView`s on one line, the label on
 *   the left, the value on the right, and neither is clickable: the row is. No
 *   `EditText` is visible until the row has been opened.
 *
 * A measured example, host mode:
 *
 * ```
 * "Local Link port"  TextView  [69,809][306,867]
 * "19072"            TextView  [1761,809][1851,867]
 * ```
 *
 * Hence the pairing rule: the horizontal band, and not the order of the nodes.
 * A screen that gains a row, or reorders itself, does not break this; counting
 * nodes would have broken at the first upstream addition.
 */
object Ps2Screen {

    /** True when the two nodes sit on the same line, allowing for overlap. */
    fun sameRow(a: Bounds, b: Bounds): Boolean {
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        val shortest = minOf(a.bottom - a.top, b.bottom - b.top)
        // Half the height of the shorter one: two neighbouring lines graze each
        // other by a few pixels, whereas one line overlaps almost entirely.
        return shortest > 0 && overlap * 2 >= shortest
    }

    /** The node carrying exactly this label, up to case and whitespace. */
    fun label(nodes: List<Node>, label: String): Node? =
        nodes.firstOrNull { it.text.trim().equals(label, ignoreCase = true) }

    /**
     * The value displayed opposite a label.
     *
     * The first text to the right of the label, not the last, and that nuance
     * cost an infinite loop: the "Room code" row carries the code *then* a
     * "Generate" button, neither of them clickable in the tree.
     *
     * ```
     * "Room code" [69,183]   "DNW757" [1532,183]   "Generate" [1686,188]
     * ```
     *
     * Taking the rightmost, the driver read "Generate", concluded the code did
     * not match, rewrote it, read "Generate" again, and so on. Seen for real on
     * the Thor on 2026-08-17, seven times in five seconds.
     */
    fun valueFor(nodes: List<Node>, label: String): Node? {
        val anchor = label(nodes, label) ?: return null
        return nodes
            .filter { it !== anchor && it.text.isNotBlank() && sameRow(anchor.bounds, it.bounds) }
            .filter { it.bounds.left > anchor.bounds.right }
            .minByOrNull { it.bounds.left }
    }

    /**
     * The row to tap in order to open a setting.
     *
     * Neither the label nor the value is clickable: their shared container is.
     * So we take the smallest clickable node containing the label, the smallest
     * because the whole page contains it too.
     */
    fun row(nodes: List<Node>, label: String): Node? {
        val anchor = label(nodes, label) ?: return null
        return nodes
            .filter { it.clickable && it.bounds.contains(anchor.bounds) }
            .minByOrNull { it.bounds.area }
    }

    /**
     * One mode choice, among the three "Network mode" buttons.
     *
     * These are labels to tap directly, not a drop-down list: all three are
     * visible at once, which avoids the open-and-return trip Dolphin imposes for
     * its connection type.
     */
    fun modeButton(nodes: List<Node>, label: String): Node? {
        val anchor = label(nodes, label) ?: return null
        if (anchor.clickable) return anchor
        return nodes
            .filter { it.clickable && it.bounds.contains(anchor.bounds) }
            .minByOrNull { it.bounds.area }
    }

    /** A toggle row's switch, looked for on its line. */
    fun toggleFor(nodes: List<Node>, label: String): Node? {
        val anchor = label(nodes, label) ?: return null
        return nodes
            .filter { it !== anchor && it.clickable && sameRow(anchor.bounds, it.bounds) }
            .filter { it.bounds.left > anchor.bounds.right }
            .minByOrNull { it.bounds.area }
    }

    /**
     * There is no editable field anywhere in ARMSX2, and that is measured.
     *
     * Tapping a row does not open an `EditText`: ARMSX2 draws its own keyboard,
     * 42 keys, each a clickable view carrying its character as a `TextView`.
     * Recorded on the Thor on 2026-08-17: 44 views, 42 labels, and not one
     * `android.widget.EditText` in the entire tree.
     *
     * Two consequences, and the second one is a wall:
     *
     * 1. `ACTION_SET_TEXT` has nothing to aim at. Input goes in key by key, like
     *    a player's. `input text` over ADB does not get through either: this
     *    keyboard ignores injected key events, tried and verified.
     * 2. The keyboard has no dot key. Digits, letters, shift, Space, backspace,
     *    Clear, Done, and nothing else. The shift key only changes case, and the
     *    field does not add the dots by itself: typing `10671` displays `10671`.
     *    An IPv4 address is therefore impossible to enter, by us as much as by
     *    the player. This is an upstream flaw, see
     *    `docs/PHASE1_SCOUT_PS2_ARMSX2.md`.
     */
    const val KEY_CLEAR = "Clear"
    const val KEY_DONE = "Done"
    const val KEY_BACKSPACE = "⌫"
    const val KEY_SHIFT = "⇧"

    /** The characters this keyboard can produce. The dot is not one of them. */
    fun canType(text: String): Boolean = text.all { it.isLetterOrDigit() && it.code < 128 }

    /** The key carrying this character, whatever case it displays. */
    fun key(nodes: List<Node>, char: Char): Node? =
        nodes.firstOrNull { it.text.length == 1 && it.text[0].equals(char, ignoreCase = true) }

    /** One of the keyboard's command keys, named by its label. */
    fun commandKey(nodes: List<Node>, label: String): Node? = label(nodes, label)

    /** True when ARMSX2's keyboard is on screen. */
    fun keyboardIsOpen(nodes: List<Node>): Boolean =
        commandKey(nodes, KEY_DONE) != null && commandKey(nodes, KEY_CLEAR) != null
}

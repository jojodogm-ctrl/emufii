package eu.emufii.app.ps2

import eu.emufii.app.dolphin.Bounds
import eu.emufii.app.dolphin.Node

/** ARMSX2 network settings: label and value are sibling TextViews, paired by vertical band, not node order. */
object Ps2Screen {

    fun sameRow(a: Bounds, b: Bounds): Boolean {
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        val shortest = minOf(a.bottom - a.top, b.bottom - b.top)
        return shortest > 0 && overlap * 2 >= shortest
    }

    fun label(nodes: List<Node>, label: String): Node? =
        nodes.firstOrNull { it.text.trim().equals(label, ignoreCase = true) }

    /** First text right of the label, not the last: the room code row ends with a "Generate" button. */
    fun valueFor(nodes: List<Node>, label: String): Node? {
        val anchor = label(nodes, label) ?: return null
        return nodes
            .filter { it !== anchor && it.text.isNotBlank() && sameRow(anchor.bounds, it.bounds) }
            .filter { it.bounds.left > anchor.bounds.right }
            .minByOrNull { it.bounds.left }
    }

    fun row(nodes: List<Node>, label: String): Node? {
        val anchor = label(nodes, label) ?: return null
        return nodes
            .filter { it.clickable && it.bounds.contains(anchor.bounds) }
            .minByOrNull { it.bounds.area }
    }

    fun modeButton(nodes: List<Node>, label: String): Node? {
        val anchor = label(nodes, label) ?: return null
        if (anchor.clickable) return anchor
        return nodes
            .filter { it.clickable && it.bounds.contains(anchor.bounds) }
            .minByOrNull { it.bounds.area }
    }

    fun toggleFor(nodes: List<Node>, label: String): Node? {
        val anchor = label(nodes, label) ?: return null
        return nodes
            .filter { it !== anchor && it.clickable && sameRow(anchor.bounds, it.bounds) }
            .filter { it.bounds.left > anchor.bounds.right }
            .minByOrNull { it.bounds.area }
    }

    /** ARMSX2 has no EditText; its keypad has no dot, so an IPv4 can't be typed. */
    const val KEY_CLEAR = "Clear"
    const val KEY_DONE = "Done"
    const val KEY_BACKSPACE = "⌫"
    const val KEY_SHIFT = "⇧"

    fun canType(text: String): Boolean = text.all { it.isLetterOrDigit() && it.code < 128 }

    fun key(nodes: List<Node>, char: Char): Node? =
        nodes.firstOrNull { it.text.length == 1 && it.text[0].equals(char, ignoreCase = true) }

    fun commandKey(nodes: List<Node>, label: String): Node? = label(nodes, label)

    fun keyboardIsOpen(nodes: List<Node>): Boolean =
        commandKey(nodes, KEY_DONE) != null && commandKey(nodes, KEY_CLEAR) != null
}

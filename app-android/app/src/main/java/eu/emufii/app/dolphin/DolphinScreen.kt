package eu.emufii.app.dolphin

// Plain types, not `Rect`: in JVM tests the stubbed android.jar returns zero everywhere.
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val area: Long get() = (right - left).toLong() * (bottom - top)

    fun contains(other: Bounds): Boolean =
        other.left >= left && other.top >= top &&
            other.right <= right && other.bottom <= bottom
}

data class Node(
    val text: String,
    val className: String,
    val bounds: Bounds,
    val ancestorClasses: List<String> = emptyList(),
    val viewId: String = "",
    val description: String = "",
    val clickable: Boolean = false,
    val checked: Boolean = false,
    val handle: Any? = null
) {
    val isField: Boolean get() = className == EDIT_TEXT

    val hasButtonAncestor: Boolean get() = BUTTON_CLASSES.any { it in ancestorClasses }

    val isButton: Boolean get() = className in BUTTON_CLASSES

    companion object {
        const val EDIT_TEXT = "android.widget.EditText"
        const val TEXT_VIEW = "android.widget.TextView"
        val BUTTON_CLASSES = listOf("android.widget.Button", "android.widget.ImageButton")
    }
}

object DolphinScreen {

    /** The `EditText` whose bounds contain the label: Compose draws the label inside the field. */
    fun fieldFor(nodes: List<Node>, labels: Collection<String>): Node? {
        val wanted = labels.map { it.trim().lowercase() }.toSet()
        if (wanted.isEmpty()) return null
        val label = nodes.firstOrNull {
            !it.isField && it.text.trim().lowercase() in wanted
        } ?: return null
        return nodes
            .filter { it.isField && it.bounds.contains(label.bounds) }
            // Nested boxes are possible; the tightest one is the field itself.
            .minByOrNull { it.bounds.area }
    }

    /** The tab and the commit button share the same text. */
    fun tab(nodes: List<Node>, labels: Collection<String>): Node? =
        matching(nodes, labels).firstOrNull { !it.isField && !inButton(nodes, it) }

    fun actionButton(nodes: List<Node>, labels: Collection<String>): Node? =
        matching(nodes, labels).firstOrNull { inButton(nodes, it) }

    private fun inButton(nodes: List<Node>, node: Node): Boolean =
        node.hasButtonAncestor ||
            nodes.any { it.isButton && it !== node && it.bounds.contains(node.bounds) }

    fun option(nodes: List<Node>, labels: Collection<String>): Node? =
        matching(nodes, labels).firstOrNull { !it.isField }

    fun isDropdownOpen(
        nodes: List<Node>,
        directLabels: Collection<String>,
        traversalLabels: Collection<String>
    ): Boolean =
        nodes.none { it.isField } &&
            matching(nodes, directLabels).isNotEmpty() &&
            matching(nodes, traversalLabels).isNotEmpty()

    /** Dolphin's buttons all have an id; the framework overflow has none. */
    fun overflow(nodes: List<Node>, window: Bounds): Node? {
        val strip = window.top + (window.bottom - window.top) / 4
        return nodes
            .filter {
                it.clickable && it.viewId.isEmpty() && it.description.isNotBlank() &&
                    it.bounds.bottom <= strip
            }
            .maxByOrNull { it.bounds.left }
    }

    /** Longest containment match wins; a tie returns null rather than start the wrong game. */
    fun looseOption(nodes: List<Node>, target: String): Node? {
        val wanted = normalize(target)
        if (wanted.isEmpty()) return null
        val hits = nodes
            .filter { !it.isField && it.text.isNotBlank() }
            .mapNotNull { node ->
                val text = normalize(node.text)
                if (text.isEmpty()) return@mapNotNull null
                if (text in wanted || wanted in text) node to text.length else null
            }
        val best = hits.maxByOrNull { it.second } ?: return null
        if (hits.count { it.second == best.second } > 1) return null
        return best.first
    }

    fun looselyMatches(text: String, target: String): Boolean {
        val a = normalize(text)
        val b = normalize(target)
        return a.isNotEmpty() && b.isNotEmpty() && (a in b || b in a)
    }

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun matching(nodes: List<Node>, labels: Collection<String>): List<Node> {
        val wanted = labels.map { it.trim().lowercase() }.toSet()
        if (wanted.isEmpty()) return emptyList()
        return nodes.filter { it.text.trim().lowercase() in wanted }
    }
}

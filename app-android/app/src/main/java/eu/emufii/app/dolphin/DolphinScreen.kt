package eu.emufii.app.dolphin

/**
 * Reading Dolphin's Compose netplay form without a single resource id.
 *
 * Kept apart from the accessibility service, and expressed over a plain [Node]
 * rather than `AccessibilityNodeInfo`, because the rules below are the whole
 * risk of this backend and the platform type cannot be constructed in a unit
 * test. Everything here is geometry and text; `DolphinScreenTest` pins it
 * against trees dumped off the Thor.
 *
 * [Bounds] rather than `android.graphics.Rect` for the same reason: a JVM test
 * gets the stubbed `android.jar`, where every `Rect` method quietly returns
 * zero. The containment rule below would have read as false everywhere and the
 * test would have been green while proving nothing, a shape this project has
 * paid for once already, on the accessibility setter that never ran.
 */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val area: Long get() = (right - left).toLong() * (bottom - top)

    /** True when [other] lies entirely within this box, edges included. */
    fun contains(other: Bounds): Boolean =
        other.left >= left && other.top >= top &&
            other.right <= right && other.bottom <= bottom
}

/** One node of the flattened accessibility tree, as inert data. */
data class Node(
    val text: String,
    val className: String,
    val bounds: Bounds,
    /** Class names of this node's ancestors, outermost first. */
    val ancestorClasses: List<String> = emptyList(),
    /** `resource-id` without its package, or empty, Compose never sets one. */
    val viewId: String = "",
    /** `content-desc`, which is all an icon-only button ever carries. */
    val description: String = "",
    val clickable: Boolean = false,
    /**
     * A switch's state, for the screens that have one.
     *
     * Added for ARMSX2, whose "Enable DEV9 Ethernet" toggle can only be read
     * here; Dolphin has no checkbox anywhere on its route and does not use it.
     * Defaults to false: a test tree that ignores it stays valid.
     */
    val checked: Boolean = false,
    /**
     * The live `AccessibilityNodeInfo` this was read from, when there is one.
     *
     * Untyped so that this file stays free of the platform class and can be
     * exercised in a plain unit test. The driver is the only thing that opens
     * the envelope, and a test never puts anything in it.
     */
    val handle: Any? = null
) {
    val isField: Boolean get() = className == EDIT_TEXT

    /** True when a button is one of this node's ancestors. */
    val hasButtonAncestor: Boolean get() = BUTTON_CLASSES.any { it in ancestorClasses }

    val isButton: Boolean get() = className in BUTTON_CLASSES

    companion object {
        const val EDIT_TEXT = "android.widget.EditText"
        const val TEXT_VIEW = "android.widget.TextView"
        val BUTTON_CLASSES = listOf("android.widget.Button", "android.widget.ImageButton")
    }
}

object DolphinScreen {

    /**
     * The field a label belongs to.
     *
     * Compose's `OutlinedTextField` draws its label *inside* the field's own
     * border, the "Port" caption sits at the top-left corner of the box that
     * holds "2626". So the two are not siblings to be counted off in order,
     * they are nested in space: the field is the `EditText` whose bounds
     * contain the label's.
     *
     * That is deliberately the anchor. Matching by position in the form would
     * break the day upstream adds a field, and upstream is still moving, three
     * netplay PRs were open on the day this was written. Containment survives a
     * reorder, an inserted row and a screen rotation, and it is the same test
     * whichever locale the label is written in.
     */
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

    /**
     * The tab that switches role, as opposed to the button that commits it.
     *
     * Dolphin gives both the same text. The button is wrapped in an
     * `android.widget.Button`; the tab is a bare row at the top of the screen.
     * Clicking the wrong one is not a no-op, pressing "Host" while the Connect
     * tab is showing would start hosting when Emufii meant to join.
     */
    fun tab(nodes: List<Node>, labels: Collection<String>): Node? =
        matching(nodes, labels).firstOrNull { !it.isField && !inButton(nodes, it) }

    /** The commit button, "Connect" or "Host", at the bottom right. */
    fun actionButton(nodes: List<Node>, labels: Collection<String>): Node? =
        matching(nodes, labels).firstOrNull { inButton(nodes, it) }

    /**
     * Whether [node] is the label of a button, by containment rather than by
     * ancestry.
     *
     * Ancestry was the first rule, and it is wrong on the real tree. Read off
     * the Thor: the commit button and its own caption come out as siblings
     * at the same depth, a `Button` at [1698,859][1883,988] with no text, next
     * to a `TextView` "Host" at [1756,900][1826,947]. Walking up from the text
     * therefore finds no button, `actionButton` returned null, and the driver
     * filled the whole form and then stopped one tap short of opening the room.
     *
     * The box still contains the caption, so containment answers what ancestry
     * could not, and it keeps working if a future build does nest them, which
     * is why the ancestor case is kept as well.
     */
    private fun inButton(nodes: List<Node>, node: Node): Boolean =
        node.hasButtonAncestor ||
            nodes.any { it.isButton && it !== node && it.bounds.contains(node.bounds) }

    /**
     * An entry of the connection-type dropdown, once it is open.
     *
     * The dropdown opens in its own window, so the tree it lands in holds the
     * two options and nothing else of the form, which is how [isDropdownOpen]
     * tells the two screens apart.
     */
    fun option(nodes: List<Node>, labels: Collection<String>): Node? =
        matching(nodes, labels).firstOrNull { !it.isField }

    /**
     * True when the connection-type dropdown is showing rather than the form.
     *
     * Both options are present and no field is: a popup window carries no
     * `EditText`. Checking for the options alone would also match the form
     * itself, whose closed dropdown displays the selected option as text.
     */
    fun isDropdownOpen(
        nodes: List<Node>,
        directLabels: Collection<String>,
        traversalLabels: Collection<String>
    ): Boolean =
        nodes.none { it.isField } &&
            matching(nodes, directLabels).isNotEmpty() &&
            matching(nodes, traversalLabels).isNotEmpty()

    /**
     * The overflow button of the game grid's toolbar, by shape, not by name.
     *
     * The first attempt asked appcompat for its own content description
     * (`abc_action_menu_overflow_description`) and matched on that. It resolves
     * nowhere: not in Dolphin's resources, and not in ours either, since
     * Emufii is Compose-only and ships no appcompat. Measured twice on the
     * Thor; the driver sat on the game grid saying `desc=0`.
     *
     * What the node does have is a shape nothing else in that toolbar shares.
     * Dolphin's own buttons all carry a resource id, `menu_settings`,
     * `menu_refresh`, `menu_open_file`, because they come from its menu
     * resource. The overflow is added by the framework and carries none,
     * while still being clickable and still describing itself for screen
     * readers. So: in the top strip of the window, the clickable node with no
     * id but a description, furthest to the right.
     *
     * Independent of every language, which is the point, and of Dolphin's menu
     * gaining or losing entries.
     */
    fun overflow(nodes: List<Node>, window: Bounds): Node? {
        val strip = window.top + (window.bottom - window.top) / 4
        return nodes
            .filter {
                it.clickable && it.viewId.isEmpty() && it.description.isNotBlank() &&
                    it.bounds.bottom <= strip
            }
            .maxByOrNull { it.bounds.left }
    }

    /**
     * The list entry that means [target], within tolerance.
     *
     * Strict equality cannot work here: the two sides name the same disc
     * differently. Emufii starts from the filename and cuts at the first bracket
     * (`displayNameFromFilename`), which gives "Super Smash Bros. Brawl"; Dolphin
     * reads the title stamped in the disc header and shows "Smash Bros. Brawl".
     * Neither is wrong, and neither can be changed, the first being our library
     * and the second being the disc.
     *
     * The rule is therefore containment, on normalised strings, both ways round:
     * the disc's title is often shorter than ours, sometimes the reverse when our
     * filename is abbreviated.
     *
     * The longest wins, and a tie cancels everything. Two entries that match
     * equally well means a library holding "Mario Kart Wii" and "Mario Kart Wii
     * (disc 2)": picking at random would start the wrong game, which is worse
     * than doing nothing. We return null, and the player chooses themselves.
     */
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

    /** True when [text] means [target] in [looseOption]'s sense. */
    fun looselyMatches(text: String, target: String): Boolean {
        val a = normalize(text)
        val b = normalize(target)
        return a.isNotEmpty() && b.isNotEmpty() && (a in b || b in a)
    }

    /**
     * Lowercased, punctuation removed, whitespace collapsed.
     *
     * The punctuation goes because it is precisely where the two names diverge:
     * "Smash Bros. Brawl" against "Smash Bros Brawl".
     */
    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun matching(nodes: List<Node>, labels: Collection<String>): List<Node> {
        val wanted = labels.map { it.trim().lowercase() }.toSet()
        if (wanted.isEmpty()) return emptyList()
        return nodes.filter { it.text.trim().lowercase() in wanted }
    }
}

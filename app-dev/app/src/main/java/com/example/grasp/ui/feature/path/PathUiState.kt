package com.example.grasp.ui.feature.path

/**
 * The Presenter → View data contract for the gamified roadmap.
 *
 * These are PLAIN Kotlin types with NO Compose/Android imports, on purpose: [PathPresenter]
 * builds a [PathUiState] and hands it to the View, so all of the interesting logic
 * (which node is current, XP → level, region grouping) is unit-testable with a fake View and
 * plain JUnit. The Composable only reads these fields and paints them.
 */

/**
 * Visual state of one node on the journey (README §"Node states & styling").
 *
 * Derived per render from the completed-set and the shape of the tree — never stored.
 *
 * The board is GATED: a lesson opens once everything feeding into it is finished. The roadmap is a
 * prerequisite graph, so a tree where every node was tappable from the start was drawing an order
 * it did not mean — the connectors said "this builds on that" while the board said "start
 * anywhere". Locking is what makes the picture true, and it is what makes progress legible: at any
 * moment there is a visible edge between what you have done and what you have not.
 */
enum class PathNodeState {
    /** Finished — green with a white check. */
    DONE,

    /** The single "you are here" node — indigo, pulsing ring, star. */
    CURRENT,

    /** Unlocked and not the current one — white w/ indigo border. Free to open. */
    OPEN,

    /** Something feeding into it isn't finished — grey with a padlock, and not tappable. */
    LOCKED,

    /** The amber "grow your path" affordance — white with a dashed amber border + plus. */
    BRANCH,
}

/**
 * One node, fully resolved for rendering.
 *
 * Both coordinates are in GRID UNITS, not dp: the View multiplies them by a slot width and a row
 * spacing. Everything is derived from the shape of the tree on every frame, so a node the user just
 * added slots into place and its neighbours shift to make room for it.
 *
 * @property column horizontal position, in slot widths from the left edge of the board.
 *           Fractional — a parent sits at the midpoint of the children below it.
 * @property row vertical position, in rows (graph depth).
 * @property parentIds ids of this node's parents, used to draw the incoming connectors.
 * @property estMinutes shown as the "12 min" sub-label (0 → hidden, e.g. for the branch node).
 * @property pickable whether this node is a legal target for the current [PathUiState.boardMode] —
 *           false for the root while deleting or moving, and, while choosing where a section
 *           should go, for the section itself and the parent it already has. Always true while
 *           [BoardMode.picking] is false. Showing those nodes as untappable is the honest version
 *           of refusing the tap: the alternative is a board that looks uniformly ready and then
 *           rejects part of it.
 */
data class PathNodeUi(
    val id: String,
    val title: String,
    val estMinutes: Int,
    val state: PathNodeState,
    val column: Float,
    val row: Int,
    val parentIds: List<String>,
    val pickable: Boolean = true,
)

/** A centered region pill ("FOUNDATIONS", "CORE ML · PICK A TRACK", …) anchored to a row. */
data class RegionUi(val label: String, val row: Int)

/**
 * What the board is being used for right now.
 *
 * Reshaping the roadmap is a mode the user asks for, the same way editing a lesson is (NFR 2.2,
 * the proposal's Conflict 3): the resting board is a set of lessons to read, and only inside one
 * of the picking modes does it become a set of targets to act on. Which is also why the structural
 * operations are NOT buttons inside an open lesson — you pick the section by tapping the section,
 * on the board where its shape is visible.
 */
enum class BoardMode {
    /** Resting. Tapping a node opens its lesson. */
    BROWSING,

    /** The "Edit roadmap" menu is open. The board underneath still behaves normally. */
    MENU,

    /** Tap the section a new branch should grow from. */
    PICK_ADD_PARENT,

    /** Tap the section to delete. */
    PICK_DELETE,

    /** Tap the section to move. */
    PICK_MOVE,

    /** Tap the section the one being moved should hang from instead. */
    PICK_MOVE_PARENT;

    /** True while the board is a picker: every tap acts on a section instead of opening it. */
    val picking: Boolean get() = this != BROWSING && this != MENU
}

/**
 * Everything the journey screen needs for one frame: the HUD numbers and the laid-out nodes.
 *
 * @property xpInLevel XP earned within the current level, 0..[xpPerLevel].
 * @property xpFraction [xpInLevel] / [xpPerLevel], clamped 0f..1f, drives the XP bar width.
 * @property rowCount total rows, used to size the board.
 * @property columnSpan columns from the leftmost node to the rightmost, used to size the board.
 *           The board is centered in the viewport at this width, which is what puts a narrow
 *           roadmap down the middle of the screen.
 * @property boardMode whether the board is resting, showing its edit menu, or being used to pick a
 *           section to add under / delete / move. Only the banner and the nodes' badges change —
 *           the roadmap itself is untouched until the user actually picks something.
 * @property movingTitle the section being moved, while [BoardMode.PICK_MOVE_PARENT] is asking
 *           where to put it. Null in every other mode.
 */
data class PathUiState(
    val title: String,
    val nodes: List<PathNodeUi>,
    val regions: List<RegionUi>,
    val boardMode: BoardMode = BoardMode.BROWSING,
    val movingTitle: String? = null,
    val masteredCount: Int,
    val totalLessons: Int,
    val streak: Int,
    val level: Int,
    val xpInLevel: Int,
    val xpPerLevel: Int,
    val xpFraction: Float,
    val rowCount: Int,
    val columnSpan: Float,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val isGenerating: Boolean = false,
)

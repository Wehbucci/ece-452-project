package com.example.grasp.ui.feature.path

/**
 * The Presenter → View data contract for the gamified roadmap.
 *
 * These are PLAIN Kotlin types with NO Compose/Android imports, on purpose: [PathPresenter]
 * builds a [PathUiState] and hands it to the View, so all of the interesting logic
 * (which node is current, what's locked, XP → level, region grouping) is unit-testable with
 * a fake View and plain JUnit. The Composable only reads these fields and paints them.
 */

/**
 * Visual state of one node on the journey (README §"Node states & styling").
 *
 * Derived per render from the completed-set + the parent rule — never stored.
 */
enum class PathNodeState {
    /** Finished — green with a white check. */
    DONE,

    /** The single "you are here" node — indigo, pulsing ring, star. */
    CURRENT,

    /** Available to start (all parents done) but not the current one — white w/ indigo border. */
    OPEN,

    /** Blocked because something upstream is unfinished — grey with a lock. */
    LOCKED,

    /** The amber "grow your path" affordance — white with a dashed amber border + plus. */
    BRANCH,
}

/**
 * One node, fully resolved for rendering.
 *
 * @property lane horizontal position in the design's 340-wide canvas (see [TreeNode.lane]).
 * @property row vertical row index (graph depth); the View multiplies this by a fixed row
 *           spacing to get a y — so inserted branch nodes place themselves automatically.
 * @property parentIds ids of this node's parents, used to draw the incoming connectors.
 * @property estMinutes shown as the "12 min" sub-label (0 → hidden, e.g. for the branch node).
 */
data class PathNodeUi(
    val id: String,
    val title: String,
    val estMinutes: Int,
    val state: PathNodeState,
    val lane: Int,
    val row: Int,
    val parentIds: List<String>,
)

/** A centered region pill ("FOUNDATIONS", "CORE ML · PICK A TRACK", …) anchored to a row. */
data class RegionUi(val label: String, val row: Int)

/**
 * Everything the journey screen needs for one frame: the HUD numbers and the laid-out nodes.
 *
 * @property xpInLevel XP earned within the current level, 0..[xpPerLevel].
 * @property xpFraction [xpInLevel] / [xpPerLevel], clamped 0f..1f, drives the XP bar width.
 * @property rowCount total rows (used to size the scroll canvas height).
 */
data class PathUiState(
    val title: String,
    val nodes: List<PathNodeUi>,
    val regions: List<RegionUi>,
    val masteredCount: Int,
    val totalLessons: Int,
    val streak: Int,
    val level: Int,
    val xpInLevel: Int,
    val xpPerLevel: Int,
    val xpFraction: Float,
    val rowCount: Int,
)

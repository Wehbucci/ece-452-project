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
 * Derived per render from the completed-set — never stored. Nothing on the board is ever gated:
 * every lesson is open from the start, so a node is only ever done, the one you're on, or waiting.
 */
enum class PathNodeState {
    /** Finished — green with a white check. */
    DONE,

    /** The single "you are here" node — indigo, pulsing ring, star. */
    CURRENT,

    /** Not started and not the current one — white w/ indigo border. */
    OPEN,

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
 */
data class PathNodeUi(
    val id: String,
    val title: String,
    val estMinutes: Int,
    val state: PathNodeState,
    val column: Float,
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
 * @property rowCount total rows, used to size the board.
 * @property columnSpan columns from the leftmost node to the rightmost, used to size the board.
 *           The board is centered in the viewport at this width, which is what puts a narrow
 *           roadmap down the middle of the screen.
 * @property pickingBranchAnchor true while the user is being asked to tap the section a new branch
 *           should grow from — the View shows its banner and nothing else changes.
 */
data class PathUiState(
    val title: String,
    val nodes: List<PathNodeUi>,
    val regions: List<RegionUi>,
    val pickingBranchAnchor: Boolean = false,
    val masteredCount: Int,
    val totalLessons: Int,
    val streak: Int,
    val level: Int,
    val xpInLevel: Int,
    val xpPerLevel: Int,
    val xpFraction: Float,
    val rowCount: Int,
    val columnSpan: Float,
)

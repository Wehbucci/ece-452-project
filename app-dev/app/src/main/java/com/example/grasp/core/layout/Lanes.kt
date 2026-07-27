package com.example.grasp.core.layout

import com.example.grasp.data.model.TreeNode
import kotlin.math.abs

/**
 * Horizontal layout for the journey board.
 *
 * Shared by the roadmap generator, the branch grower and the presenter's "where can I add a node"
 * markers, so all three agree on where a node can sit. It lives in `core` rather than in either
 * caller because it belongs to neither.
 *
 * A "lane" is a node's center X in `PathLayout`'s 340dp-wide canvas. Vertical position is NOT
 * decided here — the presenter derives a node's row from graph depth — so all this has to
 * guarantee is that two nodes landing on the SAME row are far enough apart to not overlap.
 */

/** Node centers stay half a slot clear of either canvas edge, so no label is cut off. */
internal const val LANE_MIN = 56
internal const val LANE_MAX = 284

/**
 * Minimum center-to-center distance between two nodes on the same row. Equal to
 * `PathLayout.SlotWidth`; below it their circles and labels collide. Keep in sync with that
 * constant.
 */
internal const val LANE_SEPARATION = 112

/** Step used when searching outward for a free lane. Slightly over [LANE_SEPARATION]. */
internal const val LANE_STEP = 114

/** How far out [freeLane] will look; two steps already spans the whole canvas. */
private const val MAX_LANE_PROBES = 3

/**
 * The lane closest to [wanted] that is on-canvas and at least [LANE_SEPARATION] from every lane in
 * [occupied]. Searches alternately outward, preferring the side with more room.
 *
 * The canvas only fits three nodes across, so a crowded row can leave no fully clear lane at all.
 * Rather than stacking the new node on a neighbour, that case falls back to whichever candidate
 * leaves the most room.
 */
internal fun freeLane(wanted: Int, occupied: List<Int>): Int {
    val start = wanted.coerceIn(LANE_MIN, LANE_MAX)
    val outwardIsRight = start <= TreeNode.LANE_CENTER
    val probes = (1..MAX_LANE_PROBES).asSequence().flatMap { step ->
        val offset = step * LANE_STEP
        if (outwardIsRight) sequenceOf(start + offset, start - offset)
        else sequenceOf(start - offset, start + offset)
    }
    return pickLane(sequenceOf(start) + probes, occupied)
}

/**
 * As [freeLane], but exhausts every lane to the RIGHT of [wanted] before looking left.
 *
 * Used when placing a new child beside the ones a node already has: the roadmap reads left to
 * right, so a newly added branch belongs on the right of its siblings unless the canvas has run
 * out of room over there.
 */
internal fun freeLaneRightOf(wanted: Int, occupied: List<Int>): Int {
    val start = wanted.coerceIn(LANE_MIN, LANE_MAX)
    val rightward = (0..MAX_LANE_PROBES).asSequence().map { start + it * LANE_STEP }
    val leftward = (1..MAX_LANE_PROBES).asSequence().map { start - it * LANE_STEP }
    return pickLane(rightward + leftward, occupied)
}

/**
 * The first candidate that is on-canvas and clears every lane in [occupied].
 *
 * The canvas only fits three nodes across, so a crowded row can leave no fully clear lane at all.
 * Rather than stacking the new node on a neighbour, that case falls back to whichever candidate
 * leaves the most room — including the three canonical lanes, since on a crowded row the roomiest
 * spot left is usually an edge rather than anything near where the caller asked.
 */
private fun pickLane(preferred: Sequence<Int>, occupied: List<Int>): Int {
    val candidates = (preferred + sequenceOf(LANE_MIN, TreeNode.LANE_CENTER, LANE_MAX))
        .filter { it in LANE_MIN..LANE_MAX }
        .distinct()
        .toList()

    fun clearance(lane: Int) = occupied.minOfOrNull { abs(it - lane) } ?: Int.MAX_VALUE
    return candidates.firstOrNull { clearance(it) >= LANE_SEPARATION }
        ?: candidates.maxByOrNull(::clearance)
        ?: TreeNode.LANE_CENTER
}

/**
 * Row of every node, matching how the presenter lays the board out: the longest path from a root,
 * so a node always sits below all of its parents.
 */
internal fun rowsOf(nodes: List<TreeNode>): Map<String, Int> {
    val parents = HashMap<String, MutableList<String>>()
    nodes.forEach { node -> node.children.forEach { parents.getOrPut(it) { mutableListOf() } += node.id } }
    val depths = HashMap<String, Int>()

    fun depth(id: String): Int = depths.getOrPut(id) {
        val above = parents[id]
        if (above.isNullOrEmpty()) 0 else above.maxOf { depth(it) } + 1
    }

    return nodes.associate { it.id to depth(it.id) }
}

/**
 * Lane for a branch of [branchLength] nodes growing under [anchorId].
 *
 * Branching off a node that already has a child puts the new nodes alongside the existing path
 * rather than under it, so this clears every row the branch will occupy — not just the first —
 * before settling on a lane. It starts from the anchor's own lane, so a branch off the end of the
 * roadmap simply continues straight down.
 */
internal fun laneForBranch(nodes: List<TreeNode>, anchorId: String, branchLength: Int): Int {
    val rows = rowsOf(nodes)
    val wanted = nodes.firstOrNull { it.id == anchorId }?.lane ?: TreeNode.LANE_CENTER
    val anchorRow = rows[anchorId] ?: 0
    val span = (anchorRow + 1)..(anchorRow + branchLength.coerceAtLeast(1))
    val occupied = nodes.filter { (rows[it.id] ?: -1) in span }.map { it.lane }
    return freeLane(wanted, occupied)
}

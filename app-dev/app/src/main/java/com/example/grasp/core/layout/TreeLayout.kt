package com.example.grasp.core.layout

import com.example.grasp.data.model.TreeNode

/**
 * Where every node sits on the journey board.
 *
 * Positions are DERIVED from the graph, never stored: the presenter runs [layoutBoard] on every
 * render, so growing a branch re-lays the whole board out and it stays tidy without anyone having
 * to pick coordinates by hand.
 *
 * @property rows vertical position of each node, in rows (see [rowsOf]).
 * @property columns horizontal position of each node, in columns. Fractional, because a parent
 *           sits at the midpoint of its children — the View multiplies this by one slot width.
 * @property columnSpan distance from the leftmost to the rightmost node, in columns. The board is
 *           exactly this wide (plus one slot of margin) and is centered in the viewport, which is
 *           what makes a narrow roadmap sit down the middle of the screen and a wide one pan.
 */
data class BoardLayout(
    val rows: Map<String, Int>,
    val columns: Map<String, Float>,
    val columnSpan: Float,
)

/**
 * Lays the board out as a tidy tree: children spread across consecutive columns, every parent
 * centered over the children it owns.
 *
 * The classic bottom-up tidy-tree pass. Leaves are handed the next free column in board order;
 * an internal node takes the midpoint of its first and last child. That gives three properties
 * the board depends on:
 *  - an unbranched run of lessons is a straight vertical line, because a lone child inherits its
 *    parent's exact column;
 *  - a split is symmetric — the parent sits between its children rather than above one of them;
 *  - two nodes on the same row are never less than one column apart, so nothing can overlap.
 *
 * The last one holds because each node's column lies inside its own subtree's block of leaf
 * columns, sibling subtrees own disjoint blocks, and two nodes sharing a row can never be
 * ancestor and descendant of each other.
 *
 * A converge (two parents naming the same child) is laid out under whichever parent reaches it
 * first — [spanningChildren] hands each node to exactly one parent, so the shape being laid out is
 * always a tree even when the graph isn't. The second parent's edge simply draws across to it.
 *
 * Pure: same nodes in, same layout out, no I/O.
 */
fun layoutBoard(nodes: List<TreeNode>): BoardLayout {
    if (nodes.isEmpty()) return BoardLayout(emptyMap(), emptyMap(), 0f)

    val children = spanningChildren(nodes)
    val columns = HashMap<String, Float>()
    var nextLeaf = 0f

    fun place(id: String): Float {
        // Idempotent: a document whose nodes are stored child-before-parent would otherwise place a
        // node twice, burning a leaf column and leaving a gap in the board.
        columns[id]?.let { return it }
        val kids = children[id].orEmpty()
        val column = if (kids.isEmpty()) {
            nextLeaf.also { nextLeaf += 1f }
        } else {
            val kidColumns = kids.map(::place)
            (kidColumns.first() + kidColumns.last()) / 2f
        }
        columns[id] = column
        return column
    }

    // Board order, so the strand the model listed first is the leftmost one.
    nodes.forEach { if (it.id !in columns) place(it.id) }

    val leftmost = columns.values.min()
    return BoardLayout(
        rows = rowsOf(nodes),
        columns = columns.mapValues { (_, column) -> column - leftmost },
        columnSpan = columns.values.max() - leftmost,
    )
}

/**
 * Row of every node: the LONGEST path from a root, so a node always sits below all of its parents
 * — including a converge, which drops to below the deeper of the two lines feeding it.
 *
 * Walking up the parents is what makes "longest" fall out for free, and it means a cycle would
 * recurse forever, so re-entering a node stops the walk. A cycle can't be laid out meaningfully;
 * the point is only that a bad stored document can't hang the board.
 */
internal fun rowsOf(nodes: List<TreeNode>): Map<String, Int> {
    val parents = parentsOf(nodes)
    val depths = HashMap<String, Int>()
    val walking = mutableSetOf<String>()

    fun depth(id: String): Int {
        depths[id]?.let { return it }
        if (!walking.add(id)) return 0
        val above = parents[id]
        val row = if (above.isNullOrEmpty()) 0 else above.maxOf { depth(it) } + 1
        walking -= id
        depths[id] = row
        return row
    }

    return nodes.associate { it.id to depth(it.id) }
}

/** child id → ids of the nodes listing it as a child. */
private fun parentsOf(nodes: List<TreeNode>): Map<String, List<String>> {
    val known = nodes.mapTo(mutableSetOf()) { it.id }
    val parents = HashMap<String, MutableList<String>>()
    nodes.forEach { node ->
        node.children.forEach { child ->
            if (child in known) parents.getOrPut(child) { mutableListOf() } += node.id
        }
    }
    return parents
}

/**
 * The graph reduced to a spanning forest: each node is claimed as a child by exactly one parent,
 * the first to reach it depth-first from a root.
 *
 * [layoutBoard] needs a tree — a node can only be centered over children it alone owns — but the
 * board's graph may converge, and a roadmap loaded from an old document may even carry a cycle.
 * Both collapse to something layable here rather than being special-cased downstream.
 */
private fun spanningChildren(nodes: List<TreeNode>): Map<String, List<String>> {
    val byId = nodes.associateBy { it.id }
    val parents = parentsOf(nodes)
    val owned = HashMap<String, MutableList<String>>()
    val claimed = mutableSetOf<String>()

    fun visit(id: String) {
        byId[id]?.children?.forEach { childId ->
            if (childId in byId && claimed.add(childId)) {
                owned.getOrPut(id) { mutableListOf() } += childId
                visit(childId)
            }
        }
    }

    // Real roots first, so the forest hangs off the tops of the graph rather than off whichever
    // node happens to be listed first. Anything left over (a cycle, an orphan) becomes its own root.
    nodes.filter { parents[it.id].isNullOrEmpty() }.forEach { if (claimed.add(it.id)) visit(it.id) }
    nodes.forEach { if (claimed.add(it.id)) visit(it.id) }
    return owned
}

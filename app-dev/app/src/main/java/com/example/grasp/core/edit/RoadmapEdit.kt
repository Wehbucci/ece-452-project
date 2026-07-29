package com.example.grasp.core.edit

import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.TreeNode

/**
 * One change to a roadmap's shape, as a value.
 *
 * The structural half of the same vocabulary as [LessonEdit], and for the same reason: the user
 * rearranging their roadmap and the assistant proposing a rearrangement must go through one
 * implementation, or the two will disagree about what a move means (FR4.5, FR5.4).
 *
 * Every operation names nodes by [TreeNode.id], and the result is put back into the well-formed
 * shape the board and the presenter both assume — a single-parent tree rooted at the path's own
 * id, in depth-first order. That last part matters more than it looks: `core.layout.layoutBoard`
 * re-derives every position from [TreeNode.children] on each render, so an edit that leaves the
 * tree malformed doesn't produce a slightly-wrong picture, it produces a roadmap with sections
 * missing off the side of the board.
 */
sealed interface RoadmapEdit {

    /** Retitles a node. The lesson's own heading follows it — they are the same name. */
    data class RenameNode(val nodeId: String, val title: String) : RoadmapEdit

    /** Changes a node's estimated time. */
    data class RetimeNode(val nodeId: String, val estMinutes: Int) : RoadmapEdit

    /** Changes the region a node sits in, or takes it out of one entirely with null. */
    data class RetierNode(val nodeId: String, val tier: String?) : RoadmapEdit

    /** Hangs a new node off [parentId], at the end of its children. */
    data class AddNode(val parentId: String, val node: TreeNode) : RoadmapEdit

    /**
     * Removes a node.
     *
     * [withDescendants] is the difference between "I don't need this section" and "drop this whole
     * branch": by default the node's children are re-hung on its parent, because deleting one
     * section should not silently take everything the user grew beyond it. The caller is expected
     * to have said out loud which one it means.
     */
    data class DeleteNode(val nodeId: String, val withDescendants: Boolean = false) : RoadmapEdit

    /** Moves a node — and everything below it — to hang off [newParentId] instead. */
    data class ReparentNode(val nodeId: String, val newParentId: String) : RoadmapEdit
}

/**
 * The roadmap with [edit] applied, or null if the edit doesn't fit it.
 *
 * Null covers both "there's no such node" and "that would not be a tree any more" — reparenting a
 * node under its own descendant, or deleting the root the whole path hangs from. Refusing is the
 * point: these are the edits an assistant is most likely to propose by mistake.
 */
fun LearningPath.applyEdit(edit: RoadmapEdit): LearningPath? {
    val byId = nodes.associateBy { it.id }
    val edited = when (edit) {
        is RoadmapEdit.RenameNode -> {
            val title = edit.title.trim().ifEmpty { return null }
            nodes.replacing(edit.nodeId, byId) { it.copy(title = title) } ?: return null
        }

        is RoadmapEdit.RetimeNode ->
            nodes.replacing(edit.nodeId, byId) { it.copy(estMinutes = edit.estMinutes.coerceAtLeast(0)) }
                ?: return null

        is RoadmapEdit.RetierNode ->
            nodes.replacing(edit.nodeId, byId) { it.copy(tier = edit.tier?.trim()?.ifEmpty { null }) }
                ?: return null

        is RoadmapEdit.AddNode -> {
            if (edit.node.id in byId || edit.parentId !in byId) return null
            // The new node arrives childless and parented where it was asked for, whatever the
            // caller filled in — those two fields are this operation's to decide.
            val added = edit.node.copy(children = emptyList(), parentId = edit.parentId)
            nodes.map { if (it.id == edit.parentId) it.copy(children = it.children + added.id) else it } + added
        }

        is RoadmapEdit.DeleteNode -> {
            val going = byId[edit.nodeId] ?: return null
            if (edit.nodeId == id) return null // the root IS the path
            val removed =
                if (edit.withDescendants) descendants(edit.nodeId, byId) + edit.nodeId
                else setOf(edit.nodeId)
            nodes
                .filterNot { it.id in removed }
                .map { node ->
                    if (going.id !in node.children) node
                    // Keeping the orphans where their parent stood, rather than at the end, keeps
                    // the strand the user was reading in the order they left it.
                    else node.copy(
                        children = node.children.flatMap {
                            if (it != going.id) listOf(it)
                            else if (edit.withDescendants) emptyList()
                            else going.children
                        },
                    )
                }
        }

        is RoadmapEdit.ReparentNode -> {
            val moving = byId[edit.nodeId] ?: return null
            val newParent = byId[edit.newParentId] ?: return null
            if (edit.nodeId == id) return null // the root has nowhere to hang from
            if (edit.newParentId == edit.nodeId) return null
            if (edit.newParentId in descendants(edit.nodeId, byId)) return null // would cut a loop off the tree
            if (moving.id in newParent.children) return this
            nodes.map { node ->
                when (node.id) {
                    edit.newParentId -> node.copy(children = node.children + moving.id)
                    else -> node.copy(children = node.children - moving.id)
                }
            }
        }
    }
    return copy(nodes = edited.rebuiltFrom(id))
}

/** The roadmap with every edit applied in order, or null if any one of them doesn't fit. */
fun LearningPath.applyEdits(edits: List<RoadmapEdit>): LearningPath? =
    edits.fold(this as LearningPath?) { path, edit -> path?.applyEdit(edit) }

/** Every node below [nodeId], however deep. */
private fun descendants(nodeId: String, byId: Map<String, TreeNode>): Set<String> {
    val found = mutableSetOf<String>()
    val queue = ArrayDeque(byId[nodeId]?.children.orEmpty())
    while (queue.isNotEmpty()) {
        val next = queue.removeFirst()
        if (found.add(next)) queue += byId[next]?.children.orEmpty()
    }
    return found
}

/** The list with one node rewritten, or null if it isn't in there. */
private fun List<TreeNode>.replacing(
    nodeId: String,
    byId: Map<String, TreeNode>,
    change: (TreeNode) -> TreeNode,
): List<TreeNode>? {
    if (nodeId !in byId) return null
    return map { if (it.id == nodeId) change(it) else it }
}

/**
 * Puts an edited node list back into the shape the rest of the app relies on: depth-first from
 * [rootId], each strand contiguous, `parentId` derived from `children` rather than trusted, and
 * anything no longer reachable dropped.
 *
 * The same guarantees `normalizeTree` gives a freshly generated roadmap — re-applied here so an
 * edited roadmap is not a second, weaker kind of roadmap.
 */
private fun List<TreeNode>.rebuiltFrom(rootId: String): List<TreeNode> {
    val byId = associateBy { it.id }
    val parents = flatMap { node -> node.children.map { it to node.id } }.toMap()

    val ordered = mutableListOf<TreeNode>()
    val seen = mutableSetOf<String>()
    val stack = ArrayDeque(listOfNotNull(byId[rootId] ?: firstOrNull()))
    while (stack.isNotEmpty()) {
        val node = stack.removeFirst()
        if (!seen.add(node.id)) continue
        ordered += node.copy(parentId = parents[node.id])
        // Reversed so the first child is taken off the stack first and its strand stays contiguous.
        node.children.reversed().forEach { childId -> byId[childId]?.let(stack::addFirst) }
    }
    return ordered
}

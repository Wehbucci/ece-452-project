package com.example.grasp.data.model

/**
 * A single node in a Learner roadmap/tree.
 *
 * This mirrors the LIGHTWEIGHT node object in the tree JSON: structural
 * and state info only. The deep content of a node is NOT stored here — it lives in a
 * separate file referenced by [contentRef] and is fetched lazily when the node is opened.
 * Keep this class small; do not add content/body fields to it.
 *
 * @property id stable identifier, unique within a path (used in navigation args)
 * @property title short subtopic name shown on the node / list row
 * @property completed whether the user has marked this subtopic complete
 * @property estMinutes estimated time to complete (overview.md FR4.3)
 * @property children ids of the nodes that branch off this one (drives tree edges)
 * @property parentId id of the immediate parent node, if one exists in the roadmap tree
 * @property contentRef path/URL to the separate content file (resolved lazily); null if
 *           content has not been generated yet
 * @property isBranchOut true if this node is a "branch out" affordance — a spot where the
 *           user can expand a brand-new branch (rendered with a dashed outline)
 */
data class TreeNode(
    val id: String,
    val title: String,
    val completed: Boolean = false,
    val estMinutes: Int = 0,
    val children: List<String> = emptyList(),
    val parentId: String? = null,
    val contentRef: String? = null,
    val isBranchOut: Boolean = false,
)

/**
 * Visual state of a node in the tree view (overview.md §8). This is DERIVED for rendering,
 * not stored in the JSON — compute it from completion + the user's current position via
 * [deriveNodeStates].
 */
enum class NodeState {
    /** Finished — drawn green. */
    COMPLETED,

    /** The current / next recommended node — drawn in the indigo accent. */
    ACTIVE,

    /** Generated but not started — drawn grey. */
    UNEXPLORED,

    /** A "add a new branch here" affordance — drawn with a dashed outline. */
    BRANCH_OUT,
}

/**
 * Computes a [NodeState] for every node so the tree/list can color them consistently.
 *
 * Heuristic for the skeleton: completed nodes are COMPLETED; explicit branch-out nodes are
 * BRANCH_OUT; the first not-yet-completed node (in list order) is ACTIVE; everything else is
 * UNEXPLORED. Real logic will later use the user's actual current node.
 */
fun deriveNodeStates(nodes: List<TreeNode>): Map<String, NodeState> {
    var activeAssigned = false
    return nodes.associate { node ->
        val state = when {
            node.isBranchOut -> NodeState.BRANCH_OUT
            node.completed -> NodeState.COMPLETED
            !activeAssigned -> {
                activeAssigned = true
                NodeState.ACTIVE
            }
            else -> NodeState.UNEXPLORED
        }
        node.id to state
    }
}

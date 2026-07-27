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
 * @property children ids of the nodes that branch off this one (drives tree edges). Parents
 *           are the inverse of this relation and are derived where needed, so a converge
 *           (two parents → one child) is expressed by two nodes listing the same child.
 * @property contentRef path/URL to the separate content file (resolved lazily); null if
 *           content has not been generated yet
 * @property contentReady whether this node's lesson has already been written and stored. Opening a
 *           ready node is a fetch; opening one that isn't has to generate first, which is slow
 *           enough that the UI says so. Defaults to true because a node only reaches the board
 *           after the repository has generated the whole roadmap — the stored flag is what
 *           actually decides, and it only reads false for a node whose generation failed.
 * @property isBranchOut true if this node is a "branch out" affordance — a spot where the
 *           user can expand a brand-new branch (rendered with a dashed amber outline)
 * @property tier OPTIONAL region grouping ("FOUNDATIONS", "CORE ML · PICK A TRACK", "MASTERY",
 *           …) used to draw the small centered region pills on the journey. Null = no pill.
 *
 * No position is stored, in either axis. `core.layout.layoutBoard` derives the whole board from
 * [children] on every render, so a roadmap re-centers itself when the user grows it instead of
 * carrying coordinates that were only tidy at the moment they were written.
 */
data class TreeNode(
    val id: String,
    val title: String,
    val completed: Boolean = false,
    val estMinutes: Int = 0,
    val children: List<String> = emptyList(),
    val parentId: String? = null,
    val contentRef: String? = null,
    val contentReady: Boolean = true,
    val isBranchOut: Boolean = false,
    val tier: String? = null,
)

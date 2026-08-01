package com.example.grasp.data.repository

import com.example.grasp.data.model.UserPreferences
import com.example.grasp.data.model.TreeNode
import org.json.JSONObject

/**
 * One node exactly as the model returned it, before any validation or layout.
 *
 * Kept separate from [TreeNode] so that [normalizeTree] — the part actually worth unit-testing —
 * never touches JSON, Firebase or the network.
 */
internal data class GeneratedNode(
    val id: String,
    val title: String,
    val children: List<String>,
    val estMinutes: Int = 0,
)

private const val SYSTEM_INSTRUCTION = """
    You generate structured learning trees.
    Always output valid JSON only.
"""

/** Upper bound on a generated time estimate, so one bad number can't distort the HUD. */
private const val MAX_EST_MINUTES = 120

/**
 * Generates the Learner roadmap for [title] (FR2.1).
 *
 * Only the STRUCTURE is generated here — titles, ordering, and per-node time estimates (FR4.3).
 * The repository then fills in each node's lesson with [generateSubtopicContent] before the
 * roadmap opens.
 *
 * Never throws. If the call or the parse fails the user still gets a one-node starter roadmap
 * they can grow by hand (NFR 3.1).
 */
suspend fun buildLearnerTree(
    pathId: String,
    title: String,
    prefs: UserPreferences = UserPreferences(),
): List<TreeNode> {
    val generated = geminiJson(SYSTEM_INSTRUCTION, treePrompt(title, prefs))
        ?.let(::parseGeneratedNodes)
        .orEmpty()
    val tree = if (generated.isEmpty()) fallbackTree(pathId, title) else normalizeTree(pathId, generated)
    // The prompt already asks for short titles; this is what happens when it doesn't get them.
    // Titles are shortened HERE, before anything is stored, so the board and the saved roadmap
    // never disagree about what a section is called (see [withShortTitles]).
    return withShortTitles(title, tree)
}

/** Reads the `nodes` array of a tree response, dropping entries missing an id or a title. */
internal fun parseGeneratedNodes(json: JSONObject): List<GeneratedNode> =
    json.objectList("nodes").mapNotNull { node ->
        val id = node.optString("id").trim()
        val title = node.optString("title").trim()
        if (id.isEmpty() || title.isEmpty()) return@mapNotNull null
        GeneratedNode(
            id = id,
            title = title,
            children = node.stringList("children"),
            estMinutes = node.optInt("estMinutes", 0).coerceIn(0, MAX_EST_MINUTES),
        )
    }

/**
 * Turns whatever the model produced into a roadmap the rest of the app can trust.
 *
 * The presenter's ordering, row and connector logic all assume a well-formed single-parent tree
 * rooted at [pathId], so this is where that gets enforced rather than hoped for:
 *  1. duplicate ids collapse, the first node becomes the root (renamed to [pathId]);
 *  2. child references that dangle, point at self, repeat, or steal an already-claimed node are
 *     dropped, leaving every node with exactly one parent;
 *  3. nodes unreachable from the root are discarded;
 *  4. nodes are re-ordered depth-first, so each strand is contiguous and the first strand comes
 *     first — the presenter picks the "current" node by list order and the board lays strands out
 *     left to right in this order, so both follow the model's own ordering.
 *
 * Where each node ends up on screen is NOT decided here: `core.layout.layoutBoard` derives that
 * from the shape of the tree every time the board renders.
 *
 * The roadmap holds lessons only. Where a user can GROW it is decided by the user — they pick a
 * node to branch from — so the board is never cluttered with placeholders.
 *
 * Pure: same input, same output, no I/O.
 */
internal fun normalizeTree(pathId: String, generated: List<GeneratedNode>): List<TreeNode> {
    val usable = generated.filter { it.id.isNotBlank() && it.title.isNotBlank() }.distinctBy { it.id }
    if (usable.isEmpty()) return emptyList()

    val lessons = depthFirstOrder(pathId, singleParentTree(withRootId(pathId, usable)))
    if (lessons.isEmpty()) return emptyList()

    val parents = lessons.flatMap { node -> node.children.map { it to node.id } }.toMap()

    return lessons.map { node ->
        TreeNode(
            id = node.id,
            title = node.title,
            estMinutes = node.estMinutes,
            children = node.children,
            parentId = parents[node.id],
            contentRef = "content/$pathId/${node.id}.md",
        )
    }
}

// ── Structural clean-up steps (all pure) ───────────────────────────────────────────────────

/**
 * Renames the first node to [pathId] so the roadmap's root id matches its path id, rewriting
 * every reference to it. Any *other* node that already used [pathId] is dropped so ids stay unique
 * (its subtree then falls away as unreachable in [depthFirstOrder]).
 */
private fun withRootId(pathId: String, nodes: List<GeneratedNode>): List<GeneratedNode> {
    val oldRootId = nodes.first().id
    if (oldRootId == pathId) return nodes
    return nodes
        .filterIndexed { index, node -> index == 0 || node.id != pathId }
        .mapIndexed { index, node ->
            val children = node.children.map { if (it == oldRootId) pathId else it }
            if (index == 0) node.copy(id = pathId, children = children) else node.copy(children = children)
        }
}

/**
 * Enforces "every node has exactly one parent": a child id survives only if it names a known node,
 * isn't the node itself, and hasn't already been claimed by an earlier parent. Earlier nodes win,
 * which keeps the model's intended main line intact.
 */
private fun singleParentTree(nodes: List<GeneratedNode>): List<GeneratedNode> {
    val known = nodes.mapTo(mutableSetOf()) { it.id }
    val claimed = mutableSetOf(nodes.first().id) // the root is nobody's child
    return nodes.map { node ->
        val children = mutableListOf<String>()
        node.children.forEach { childId ->
            if (childId in known && childId != node.id && claimed.add(childId)) children += childId
        }
        node.copy(children = children)
    }
}

/** Depth-first from the root: each strand contiguous, in order. Unreachable nodes are dropped. */
private fun depthFirstOrder(rootId: String, nodes: List<GeneratedNode>): List<GeneratedNode> {
    val byId = nodes.associateBy { it.id }
    val ordered = mutableListOf<GeneratedNode>()
    val seen = mutableSetOf<String>()

    fun visit(id: String) {
        if (!seen.add(id)) return
        val node = byId[id] ?: return
        ordered += node
        node.children.forEach(::visit)
    }

    visit(rootId)
    return ordered
}

// ── Prompt & fallback ──────────────────────────────────────────────────────────────────────

private fun treePrompt(title: String, prefs: UserPreferences) = """
    Generate a learning roadmap as a JSON tree for the topic below.

    Topic: $title
    
    User Preferences:
    - Pace: ${prefs.pace.label} (${prefs.pace.prompt})
    - Style: ${prefs.style.label} (${prefs.style.prompt})
    - Tone: ${prefs.tone.label} (${prefs.tone.prompt})

    Size and Detail Guidance:
    ${
    when (prefs.pace) {
        com.example.grasp.data.model.Pace.OVERVIEW -> """
            - STRICT LIMIT: Total nodes MUST be between 4 and 6.
            - FOCUS: Absolute essentials only. Skip all history, minor rules, and non-critical details.
            - STRUCTURE: Usually just 2 strands with 1-2 nodes each.
        """.trimIndent()
        com.example.grasp.data.model.Pace.COMPREHENSIVE -> """
            - MINIMUM NODES: Total nodes MUST be at least 15. 
            - FOCUS: Exhaustive detail. Include history, advanced strategy, technical officiating, scouting, and cultural impact.
            - STRUCTURE: Use 3 full strands with 5-7 nodes each.
        """.trimIndent()
        else -> """
            - TARGET SIZE: 8-12 total nodes.
            - FOCUS: A standard, balanced foundational journey.
            - STRUCTURE: Moderate strands (3-4 nodes each).
        """.trimIndent()
    }
}

    Style and Tone Guidance:
    ${
    when (prefs.style) {
        com.example.grasp.data.model.Style.ACTIONABLE -> """
            - STYLE: ACTIONABLE. Prioritize nodes that involve "doing" rather than "knowing".
            - FOCUS: Include steps for setup, practice, and physical application.
        """.trimIndent()
        com.example.grasp.data.model.Style.THEORETICAL -> """
            - STYLE: THEORETICAL. Prioritize nodes that explain the "logic", "architecture", and "history".
            - FOCUS: Ensure foundations and conceptual relationships are mapped first.
        """.trimIndent()
        else -> "- STYLE: BALANCED."
    }
}
    ${
    when (prefs.tone) {
        com.example.grasp.data.model.Tone.ENCOURAGING -> "- TONE: ENCOURAGING. Use motivating and friendly language in node titles."
        com.example.grasp.data.model.Tone.MINIMALIST -> "- TONE: MINIMALIST. Use short, efficient node titles (2-3 words max)."
        else -> "- TONE: PROFESSIONAL."
    }
}

    Shape you are building:
    Wide at the top, then deep. The root is the entry point to the topic. It
    fans out into 2-3 STRANDS — the genuinely distinct areas someone has to
    cover to know this topic. Each strand then runs DEEP on its own: a
    mostly-straight chain of lessons, each building on the last, from
    foundational to advanced (or chronological, for historical topics).

    The breadth belongs at the root and almost nowhere else. Below the root a
    node normally has exactly one child, continuing its own strand. Strands
    stay separate for good: they never rejoin each other and never rejoin the
    root. Every node has exactly one parent.

    Step 1 — Think before structuring (do not output this step):
    Pick the 2-3 strands. They must partition the topic, not overlap: each one
    is a different aspect of it, and a lesson belongs in exactly one of them.
    Prefer strands that each genuinely support 3-5 lessons — if the topic only
    really splits two ways, use 2 strands and make them deeper rather than
    inventing a thin third. Then order the lessons inside each strand from
    first-principles to advanced.

    Optionally identify ONE point, on a strand rather than at the root, where
    the topic contains a short self-contained detour — something worth knowing
    but not needed by the lessons after it. A detour must be specifically
    motivated by the node it hangs off: ask "is this a defining, characteristic
    feature of THIS SPECIFIC lesson's content, not just something loosely
    adjacent to it?" If nothing fits that test, use no detour at all.

    Step 2 — Build the tree from that reasoning:
    - Create exactly one root node: the entry point / introduction to the
    topic. It must be the first node in the output, and its "children" list
    holds the first lesson of each strand (2-3 ids).
    - Every other node has exactly one child, continuing its strand, or an
    empty "children" list if it ends the strand.
    - The one exception is the optional detour: a single node somewhere on a
    strand may list 2 ids, where the FIRST continues its strand and the SECOND
    is the detour, which ends immediately (1 node, empty "children"). Title a
    detour starting with "Explore: " followed by its specific topic
    (e.g. "Explore: The Panhellenic Games").
    - Never more than one detour in the whole roadmap, and never one hanging
    off the root.
    - Every node id must appear in exactly one other node's "children"
    list (except the root, which appears in none). Never list the same
    node id as a child of two different nodes.
    - Order the strands themselves so the one a beginner should start with
    comes first.

    Each node must contain:
    - id: lowercase hyphen-separated string
    - title: the lesson's name in AT MOST $MAX_TITLE_WORDS WORDS. This is a label under a small
    circle on a map, not a sentence — "Labelled Data", not "Understanding the Role of
    Labelled Data". Drop filler openers ("Introduction to", "Understanding",
    "The Basics of") and keep the subject's own terms. Two nodes must never share a title.
    A detour still starts with "Explore: " and counts that as one of its $MAX_TITLE_WORDS words.
    - children: list of child node ids (usually exactly one id; 2 ids only
    at a deliberate detour point; empty list only for the final node in
    the main path, or the end of a side branch)
    - estMinutes: honest whole-minute estimate to read and absorb that one
    node, between 3 and 25

    Return ONLY valid JSON, no commentary, no markdown fences.

    Format:
    {
    "nodes": [
        {
        "id": "example-node",
        "title": "Example Node",
        "children": ["next-node"],
        "estMinutes": 10
        }
    ]
    }
""".trimIndent()

/**
 * What the user gets when generation fails: the topic itself, as a single node. Add mode can grow
 * it from there once the network comes back.
 */
private fun fallbackTree(pathId: String, title: String): List<TreeNode> = listOf(
    TreeNode(
        id = pathId,
        title = "Introduction to $title",
        contentRef = "content/$pathId/$pathId.md",
    ),
)

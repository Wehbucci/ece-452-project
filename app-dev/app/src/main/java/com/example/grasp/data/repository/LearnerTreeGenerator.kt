package com.example.grasp.data.repository

import com.example.grasp.core.layout.freeLane
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
 * with a branch-out affordance, so the path is grow-able by hand (NFR 3.1).
 */
suspend fun buildLearnerTree(pathId: String, title: String): List<TreeNode> {
    val generated = geminiJson(SYSTEM_INSTRUCTION, treePrompt(title))
        ?.let(::parseGeneratedNodes)
        .orEmpty()
    return if (generated.isEmpty()) fallbackTree(pathId, title) else normalizeTree(pathId, generated)
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
 * The presenter's locking, row and connector logic all assume a well-formed single-parent tree
 * rooted at [pathId], so this is where that gets enforced rather than hoped for:
 *  1. duplicate ids collapse, the first node becomes the root (renamed to [pathId]);
 *  2. child references that dangle, point at self, repeat, or steal an already-claimed node are
 *     dropped, leaving every node with exactly one parent;
 *  3. nodes unreachable from the root are discarded;
 *  4. nodes are re-ordered depth-first so the main line comes before any detour — the presenter
 *     picks the "current" node by list order, so the spine should win ties;
 *  5. one branch-out affordance is appended under the deepest leaf (FR: expand the tree);
 *  6. lanes are assigned so the spine runs straight down the middle and detours sit to one side.
 *
 * Pure: same input, same output, no I/O.
 */
internal fun normalizeTree(pathId: String, generated: List<GeneratedNode>): List<TreeNode> {
    val usable = generated.filter { it.id.isNotBlank() && it.title.isNotBlank() }.distinctBy { it.id }
    if (usable.isEmpty()) return emptyList()

    val ordered = depthFirstOrder(pathId, singleParentTree(withRootId(pathId, usable)))
    if (ordered.isEmpty()) return emptyList()

    // The affordance hangs off the end of the longest line — the natural "what's after this?" spot.
    val branchParent = deepestLeaf(pathId, ordered)
    val branchId = uniqueId(BRANCH_OUT_ID, ordered.map { it.id }.toSet())
    val lessons = ordered.map { node ->
        if (node.id == branchParent.id) node.copy(children = listOf(branchId)) else node
    }

    val lanes = laneByNode(lessons + GeneratedNode(branchId, BRANCH_OUT_TITLE, emptyList()))
    val parents = lessons.flatMap { node -> node.children.map { it to node.id } }.toMap()

    return lessons.map { node ->
        TreeNode(
            id = node.id,
            title = node.title,
            estMinutes = node.estMinutes,
            children = node.children,
            parentId = parents[node.id],
            contentRef = "content/$pathId/${node.id}.md",
            lane = lanes[node.id] ?: TreeNode.LANE_CENTER,
        )
    } + TreeNode(
        id = branchId,
        title = BRANCH_OUT_TITLE,
        parentId = branchParent.id,
        isBranchOut = true,
        lane = lanes[branchId] ?: TreeNode.LANE_CENTER,
    )
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

/** Depth-first from the root: main line first, detours after. Unreachable nodes are dropped. */
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

/** The leaf furthest from the root (ties → the later one in depth-first order). */
private fun deepestLeaf(rootId: String, nodes: List<GeneratedNode>): GeneratedNode {
    val byId = nodes.associateBy { it.id }
    val depths = mutableMapOf(rootId to 0)

    fun walk(id: String) {
        val depth = depths.getValue(id)
        byId[id]?.children?.forEach { childId ->
            if (childId !in depths) {
                depths[childId] = depth + 1
                walk(childId)
            }
        }
    }

    walk(rootId)
    return nodes.filter { it.children.isEmpty() }.maxByOrNull { depths[it.id] ?: 0 } ?: nodes.last()
}

/**
 * Horizontal position for every node.
 *
 * Every node simply WANTS its parent's lane, which makes an unbranched roadmap a straight spine
 * down the middle. Siblings therefore collide, and the collision is what pushes a detour aside:
 * [freeLane] searches outward from the wanted lane until it finds one that clears every node
 * already placed on that row, so two nodes on a row can never overlap.
 *
 * Rows here are tree depth, which for a single-parent tree is exactly the depth the presenter
 * derives — so these lanes describe the layout that actually gets drawn.
 */
internal fun laneByNode(nodes: List<GeneratedNode>): Map<String, Int> {
    val byId = nodes.associateBy { it.id }
    val lanes = mutableMapOf<String, Int>()
    val lanesByRow = mutableMapOf<Int, MutableList<Int>>()

    fun walk(id: String, wanted: Int, row: Int) {
        if (id in lanes) return
        val lane = freeLane(wanted, lanesByRow.getOrPut(row) { mutableListOf() })
        lanes[id] = lane
        lanesByRow.getValue(row) += lane
        byId[id]?.children?.forEach { childId -> walk(childId, lane, row + 1) }
    }

    walk(nodes.first().id, TreeNode.LANE_CENTER, row = 0)
    return lanes
}

// ── Prompt & fallback ──────────────────────────────────────────────────────────────────────

private fun treePrompt(title: String) = """
    Generate a learning roadmap as a JSON tree for the topic below.

    Topic: $title

    Shape you are building:
    This is a guided path through the topic, not a wide taxonomy of
    independent categories. Most of the roadmap should read as a single
    line of concepts, each building on the last, from foundational to
    advanced (or chronological order, for historical/narrative topics).
    At one or two points where the topic genuinely contains a short,
    self-contained detour — something worth knowing but not essential to
    the main line of progression — the path can branch off into a short
    side node. That side branch stays short (usually just 1 node) and
    ends there. It does NOT reconnect to the main path or to any other
    branch later. Every node has exactly one parent.

    Step 1 — Think before structuring (do not output this step):
    Lay out the topic as a rough sequence from first-principles to advanced,
    or chronologically for historical topics. Identify at most 1-2 points
    where a short, self-contained detour makes sense as a side node.
    Everywhere else, keep it a single path forward.

    A detour must be specifically motivated by the node it branches from —
    not just any independent topic that happens to fit somewhere in that
    general era or category. Ask: "is this detour a defining, characteristic
    feature of THIS SPECIFIC node's content, not just something loosely
    adjacent to it?" For example, off a node about a particular cultural
    golden age, a detour into that era's defining art form is well-motivated.
    A detour into something merely from the same rough time period, without
    a direct thematic tie to what that specific node is actually about, is
    NOT well-motivated — pick a different, more specifically-tied detour
    instead, or skip the detour for that point entirely if none fits.

    Step 2 — Build the tree from that reasoning:
    - Create exactly one root node: the entry point / introduction to the
    topic. It must be the first node in the output.
    - Most nodes should have exactly one child, continuing the roadmap
    forward as a single path.
    - At a detour point, a node's "children" list may contain 2 ids: the
    FIRST id continues the main path, the SECOND starts a short side branch
    that ends on its own (empty "children" list) within 1-2 nodes. Title that
    side node starting with "Explore: " followed by its specific topic
    (e.g. "Explore: The Panhellenic Games").
    - Do not use more than 2 detour points in the entire roadmap. Most
    topics should have 0 or 1.
    - Never split the root itself into more than 2 immediate children.
    - Every node id must appear in exactly one other node's "children"
    list (except the root, which appears in none). Never list the same
    node id as a child of two different nodes.
    - Order everything along the main path from foundational to advanced,
    or chronologically for historical topics.

    Size guidance (soft, not a target to hit exactly):
    - Total nodes, including root: roughly 6-12 depending on topic breadth.
    A focused topic might be 6-8 nodes in a near-straight line. A
    broader topic might be 9-12 with one short detour.

    Each node must contain:
    - id: lowercase hyphen-separated string
    - title: concise lesson title
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
 * What the user gets when generation fails: the topic itself plus a branch-out node, so the
 * roadmap is still usable and can be grown by hand once the network comes back.
 */
private fun fallbackTree(pathId: String, title: String): List<TreeNode> = listOf(
    TreeNode(
        id = pathId,
        title = "Introduction to $title",
        children = listOf(BRANCH_OUT_ID),
        contentRef = "content/$pathId/$pathId.md",
    ),
    TreeNode(
        id = BRANCH_OUT_ID,
        title = BRANCH_OUT_TITLE,
        parentId = pathId,
        isBranchOut = true,
    ),
)

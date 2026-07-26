package com.example.grasp.data.repository

import com.example.grasp.data.model.TreeNode

/** Id of the branch-out affordance a freshly generated roadmap ends with. */
internal const val BRANCH_OUT_ID = "branch-out"

/** Label drawn under the dashed amber node. */
internal const val BRANCH_OUT_TITLE = "Branch out"

/** Region pill carried by every user-grown node, so the journey groups them under one header. */
internal const val BRANCH_TIER = "YOUR BRANCHES"

/** A grown branch stays short — it's a detour, not a second roadmap. */
private const val MAX_BRANCH_NODES = 4


private const val SYSTEM_INSTRUCTION = """
    You extend structured learning trees.
    Always output valid JSON only.
"""

/**
 * Grows a short new branch out of the node the user tapped "branch out" from (use case
 * "expand any node of the learning tree to learn about it").
 *
 * @param pathTitle the roadmap the branch belongs to — keeps the new nodes on-topic.
 * @param fromTitle the node the branch hangs off, so the AI continues from what the user just
 *        finished instead of restarting the subject.
 * @param topic what the user asked for, typed or picked from [suggestBranchTopics].
 * @param takenIds ids already on the path; new ids are made unique against these.
 * @param lane horizontal position to grow in. Callers that need the branch to clear existing nodes
 *        leave this alone and re-lane the result once they know how long the branch turned out.
 *
 * Returns the chain of new lesson nodes FOLLOWED BY a fresh branch-out affordance, so the user
 * can keep growing from the end of what they just added. Never empty: if generation fails they
 * still get a single node named after what they asked for (NFR 3.1).
 */
suspend fun buildBranch(
    pathId: String,
    pathTitle: String,
    fromTitle: String,
    topic: String,
    takenIds: Set<String>,
    lane: Int = TreeNode.LANE_CENTER,
): List<TreeNode> {
    val wanted = topic.trim().ifEmpty { "New branch" }
    val generated = geminiJson(SYSTEM_INSTRUCTION, branchPrompt(pathTitle, fromTitle, wanted))
        ?.let(::parseGeneratedNodes)
        .orEmpty()
        .take(MAX_BRANCH_NODES)

    // The model's own ids are ignored: they collide with what's already on the path often enough
    // that assigning them here is simpler than reconciling them. Its `children` are ignored too —
    // a branch is deliberately a straight chain, which is what [chain] builds below.
    val titles = generated.map { it.title }.ifEmpty { listOf(wanted) }
    return chain(pathId, titles, generated.map { it.estMinutes }, takenIds, lane)
}

/**
 * Starter chips for the branch sheet: four directions this specific path could grow in.
 *
 * Empty on failure — the sheet just shows its text field, which is the real input anyway.
 */
suspend fun suggestBranchTopics(
    pathTitle: String,
    fromTitle: String,
    existingTitles: List<String>,
): List<String> = geminiJson(SYSTEM_INSTRUCTION, suggestionPrompt(pathTitle, fromTitle, existingTitles))
    ?.stringList("topics")
    ?.map { it.trim().trimEnd('.') }
    ?.filter { it.length in 3..40 }
    ?.take(4)
    .orEmpty()

// ── Node assembly (pure) ───────────────────────────────────────────────────────────────────

/** Links [titles] into a straight chain of nodes, capped by a fresh branch-out affordance. */
private fun chain(
    pathId: String,
    titles: List<String>,
    estMinutes: List<Int>,
    takenIds: Set<String>,
    lane: Int,
): List<TreeNode> {
    val taken = takenIds.toMutableSet()
    val ids = titles.map { title -> uniqueId(slugify(title), taken).also { taken += it } }
    val branchId = uniqueId(BRANCH_OUT_ID, taken)

    val lessons = titles.mapIndexed { index, title ->
        TreeNode(
            id = ids[index],
            title = title,
            estMinutes = estMinutes.getOrElse(index) { 0 },
            children = listOf(ids.getOrNull(index + 1) ?: branchId),
            parentId = ids.getOrNull(index - 1),
            contentRef = "content/$pathId/${ids[index]}.md",
            lane = lane,
            // Only the first node carries the pill; the presenter anchors one pill per tier.
            tier = BRANCH_TIER.takeIf { index == 0 },
        )
    }
    return lessons + TreeNode(
        id = branchId,
        title = BRANCH_OUT_TITLE,
        parentId = ids.last(),
        isBranchOut = true,
        lane = lane,
    )
}

/** "Deep Learning Basics!" → "deep-learning-basics". */
internal fun slugify(text: String): String =
    text.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48).ifEmpty { "node" }

/** [base] if free, otherwise "base-2", "base-3", … — node ids must be unique within a path. */
internal fun uniqueId(base: String, taken: Set<String>): String {
    if (base !in taken) return base
    var suffix = 2
    while ("$base-$suffix" in taken) suffix++
    return "$base-$suffix"
}

// ── Prompts ────────────────────────────────────────────────────────────────────────────────

private fun branchPrompt(pathTitle: String, fromTitle: String, topic: String) = """
    A learner is following a roadmap on "$pathTitle". They have reached "$fromTitle" and want to
    branch off into a new direction of their own: "$topic".

    Generate that branch as a short, ordered chain of lesson nodes.

    Rules:
    - 2 to $MAX_BRANCH_NODES nodes, ordered from foundational to advanced.
    - The first node must pick up naturally from "$fromTitle" — do not restart "$pathTitle" from
    the basics, and do not re-teach it.
    - Stay inside what the learner asked for ("$topic"). If that request is too small for several
    nodes, return fewer nodes rather than padding it with loosely related material.
    - Titles are concise lesson names, no numbering, no "Part 1".
    - estMinutes is an honest whole-minute estimate for that one node, between 3 and 25.
    - If "$topic" describes something dangerous or harmful to carry out, return an empty
    "nodes" list instead.

    Return ONLY valid JSON, no commentary, no markdown fences.

    Format:
    {
    "nodes": [
        { "id": "example-node", "title": "Example Node", "children": [], "estMinutes": 10 }
    ]
    }
""".trimIndent()

private fun suggestionPrompt(pathTitle: String, fromTitle: String, existingTitles: List<String>) = """
    A learner is following a roadmap on "$pathTitle" and has reached "$fromTitle". Suggest four
    directions they could branch off into next.

    Rules:
    - Each suggestion is a short noun phrase, 2 to 5 words, that could title a small side branch.
    - Each must be a genuine continuation of "$fromTitle" within "$pathTitle", not a restart.
    - Do not repeat anything already on the roadmap: ${existingTitles.joinToString("; ")}
    - No numbering, no punctuation at the end.

    Return ONLY valid JSON, no commentary, no markdown fences.

    Format:
    { "topics": ["First idea", "Second idea", "Third idea", "Fourth idea"] }
""".trimIndent()

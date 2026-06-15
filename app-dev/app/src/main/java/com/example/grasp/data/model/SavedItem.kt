package com.example.grasp.data.model

/**
 * Anything that can be saved, listed in the Library and resumed.
 *
 * Both Learner roadmaps and Tinkerer guides are saveable, so they share this interface —
 * that lets the Library screen render one uniform list. The two concrete types differ in
 * how their progress is computed (completed nodes vs. checked steps).
 */
sealed interface SavedItem {
    val id: String
    val title: String
    val mode: Mode

    /** Short descriptor shown under the title, e.g. "8 subtopics" or "6 steps". */
    val subtitle: String

    /** Completion in the range 0f..1f, for the progress bar. */
    val progress: Float
}

/**
 * A Learner roadmap: a topic broken into a tree of [TreeNode]s (overview.md §3, FR2).
 * This is the in-memory form of the tree JSON described in §5.
 */
data class LearningPath(
    override val id: String,
    override val title: String,
    val nodes: List<TreeNode>,
) : SavedItem {
    override val mode: Mode = Mode.LEARNER
    override val subtitle: String get() = "${nodes.size} subtopics"
    override val progress: Float
        get() = if (nodes.isEmpty()) 0f else nodes.count { it.completed }.toFloat() / nodes.size

    /** e.g. "3 of 8 complete" for the list-view header (overview.md §8, screen 2). */
    val progressLabel: String get() = "${nodes.count { it.completed }} of ${nodes.size} complete"
}

/**
 * A Tinkerer guide: a flat, ordered list of [TinkerStep]s for a concrete task
 * (overview.md §3, FR3).
 */
data class TinkerGuide(
    override val id: String,
    override val title: String,
    val steps: List<TinkerStep>,
) : SavedItem {
    override val mode: Mode = Mode.TINKERER
    override val subtitle: String get() = "${steps.size} steps"
    override val progress: Float
        get() = if (steps.isEmpty()) 0f else steps.count { it.done }.toFloat() / steps.size

    /** e.g. "2 of 6 done" for the checklist header. */
    val progressLabel: String get() = "${steps.count { it.done }} of ${steps.size} done"
}

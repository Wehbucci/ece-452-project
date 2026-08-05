package com.example.grasp.data.model

/** The possible states of an offline roadmap download. */
enum class DownloadState {
    /** Not downloaded and no download in progress. */
    NONE,
    /** Waiting for network or resources to start. */
    PENDING,
    /** Data is currently being fetched and cached. */
    DOWNLOADING,
    /** Fully cached and ready for offline use. */
    AVAILABLE,
    /** Last download attempt failed. */
    FAILED
}

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
    val downloadState: DownloadState

    /**
     * Whether this is one of the examples that ship with the app (`StarterLibrary`).
     *
     * Carried on the item because it changes what "downloaded" means for it. Every other item is
     * offline-ready only if its content happens to be in Firestore's local cache, which is why the
     * app makes the user ask for it; a starter's content is in the APK, on every device and every
     * install, so it is offline-ready the moment the account has it and there is nothing to fetch
     * and nothing to free by removing.
     */
    val isStarter: Boolean

    /** Short descriptor shown under the title, e.g. "8 subtopics" or "6 steps". */
    val subtitle: String

    /** Completion in the range 0f..1f, for the progress bar. */
    val progress: Float

    /**
     * How many lessons/steps of this item the user has finished — the unit XP is earned in
     * (`core.progress.Xp`).
     *
     * Declared HERE, on the type, rather than being counted at each call site: the Profile card,
     * the roadmap HUD and the repository's account total must agree on what "one lesson" is, and
     * three separate `count { }`s over the same list is exactly how they stopped agreeing.
     */
    val lessonsMastered: Int

    /** How many lessons/steps this item contains in total — the denominator of [progress]. */
    val lessonCount: Int

    /** e.g. "3 of 8 complete" for the list-view header (overview.md §8, screen 2). */
    val progressLabel: String
}

/**
 * A Learner roadmap: a topic broken into a tree of [TreeNode]s (overview.md §3, FR2).
 * This is the in-memory form of the tree JSON described in §5.
 */
data class LearningPath(
    override val id: String,
    override val title: String,
    val nodes: List<TreeNode>,
    override val downloadState: DownloadState = DownloadState.NONE,
    override val isStarter: Boolean = false,
) : SavedItem {
    override val mode: Mode = Mode.LEARNER

    /**
     * Lessons only. A "branch out" affordance is a spot to grow the roadmap from, not something
     * that can be finished, so counting it would leave a path stuck at 90% with nothing left to
     * do — and would put a lesson's worth of XP on the board that no lesson ever earned.
     */
    override val lessonCount: Int get() = nodes.count { !it.isBranchOut }
    override val lessonsMastered: Int get() = nodes.count { it.completed && !it.isBranchOut }

    override val subtitle: String get() = "$lessonCount subtopics"
    override val progress: Float
        get() = if (lessonCount == 0) 0f else lessonsMastered.toFloat() / lessonCount

    /** e.g. "3 of 8 complete" for the list-view header (overview.md §8, screen 2). */
    override val progressLabel: String get() = "$lessonsMastered of $lessonCount complete"
}

/**
 * A Tinkerer guide: a flat, ordered list of [TinkerStep]s for a concrete task
 * (overview.md §3, FR3).
 */
data class TinkerGuide(
    override val id: String,
    override val title: String,
    val steps: List<TinkerStep>,
    override val downloadState: DownloadState = DownloadState.NONE,
    override val isStarter: Boolean = false,
) : SavedItem {
    override val mode: Mode = Mode.TINKERER

    /** A finished step is worth exactly what a finished lesson is — same XP, same profile. */
    override val lessonCount: Int get() = steps.size
    override val lessonsMastered: Int get() = steps.count { it.done }

    override val subtitle: String get() = "$lessonCount steps"
    override val progress: Float
        get() = if (lessonCount == 0) 0f else lessonsMastered.toFloat() / lessonCount

    /** e.g. "2 of 6 done" for the checklist header. */
    override val progressLabel: String get() = "$lessonsMastered of $lessonCount done"
}

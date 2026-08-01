package com.example.grasp.data.repository

import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.model.TinkerStep
import com.example.grasp.data.model.TreeNode

/**
 * The example roadmaps and guides every new account starts with.
 *
 * Home used to advertise "popular topics" that were suggestions and nothing more: tapping one
 * navigated to a path id that had never been created, so it opened an empty roadmap. They are
 * replaced by these, which are REAL saved items — written here, seeded into the user's library
 * once, and from then on indistinguishable from something they generated themselves. They can be
 * opened, grown, edited and deleted like anything else, and deleting one is permanent.
 *
 * Only the STRUCTURE is authored here — titles, shape and time estimates. Each lesson is written
 * by the AI the first time it is opened, exactly like a node whose up-front generation failed
 * (see [FirebasePathRepository.subtopic]), so these stay a few dozen lines rather than a few
 * thousand, and they come out in the reader's own preferred pace, style and tone.
 *
 * Titles are all within `MAX_TITLE_WORDS`, since these skip the generator that would otherwise
 * enforce that.
 */
object StarterLibrary {

    /** Marks a topic document as one of these, so it is recognisable in Firestore. */
    const val STARTER_FIELD = "starter"

    /** The learner roadmaps a new account finds in its library. */
    fun learnerExamples(): List<LearningPath> = listOf(howLearningWorks(), spaceExploration())

    /** The tinkerer guide a new account finds in its library. */
    fun tinkerExamples(): List<TinkerGuide> = listOf(frenchPress())

    /**
     * A roadmap about learning itself — chosen because it is the one topic every user of a
     * learning app has a use for, and because it demonstrates the tree's shape (a root fanning
     * into strands that each run deep) on material nobody needs prior knowledge to follow.
     */
    private fun howLearningWorks() = roadmap(
        id = "example-how-learning-works",
        title = "How Learning Works",
        nodes = listOf(
            node("example-how-learning-works", "How Learning Works", 6,
                children = listOf("hlw-memory", "hlw-practice"), tier = "FOUNDATIONS"),

            node("hlw-memory", "How Memory Works", 8, children = listOf("hlw-forgetting")),
            node("hlw-forgetting", "The Forgetting Curve", 7, children = listOf("hlw-spacing")),
            node("hlw-spacing", "Spaced Repetition", 9, children = listOf("hlw-recall")),
            node("hlw-recall", "Active Recall", 8),

            node("hlw-practice", "Deliberate Practice", 9, children = listOf("hlw-feedback")),
            node("hlw-feedback", "Useful Feedback", 7, children = listOf("hlw-interleaving")),
            node("hlw-interleaving", "Mixing Topics Up", 8, children = listOf("hlw-plateaus")),
            node("hlw-plateaus", "Getting Past Plateaus", 9),
        ),
    )

    /**
     * A second roadmap with a genuinely different feel — concrete, chronological subject matter
     * rather than technique — so the two examples together show that Grasp is not only good for
     * one kind of topic.
     */
    private fun spaceExploration() = roadmap(
        id = "example-space-exploration",
        title = "Space Exploration",
        nodes = listOf(
            node("example-space-exploration", "Space Exploration", 5,
                children = listOf("se-orbit", "se-race"), tier = "FOUNDATIONS"),

            node("se-orbit", "What Orbit Is", 8, children = listOf("se-rockets")),
            node("se-rockets", "How Rockets Work", 10, children = listOf("se-reusable")),
            node("se-reusable", "Reusable Rockets", 9),

            node("se-race", "The Space Race", 9, children = listOf("se-apollo")),
            node("se-apollo", "Apollo Landings", 10, children = listOf("se-shuttle", "se-probes")),
            node("se-shuttle", "The Shuttle Era", 9, children = listOf("se-iss")),
            node("se-iss", "Living In Orbit", 8),
            node("se-probes", "Explore: Deep Space Probes", 8),
        ),
    )

    /**
     * The Tinkerer example. A short, safe, physical task with a real technique to it — enough
     * steps to show what the mode is for, nothing anyone can hurt themselves with.
     */
    private fun frenchPress() = TinkerGuide(
        id = "example-french-press",
        title = "Brew Coffee In A French Press",
        steps = listOf(
            step(1, "Boil water and let it sit for 30 seconds",
                "Just off the boil (~95°C). Fully boiling water scorches the grounds and turns " +
                    "the cup bitter.", 3),
            step(2, "Grind 55g of coffee coarsely",
                "Coarse, like raw sugar. Anything finer slips through the mesh and leaves silt " +
                    "in the cup.", 2),
            step(3, "Add the grounds and start a timer",
                "Roughly 1g of coffee per 15g of water — 55g of coffee to 800g of water.", 1),
            step(4, "Pour to twice the coffee's weight and wait 30 seconds",
                "This is the bloom: fresh coffee gives off CO₂, and letting it escape first is " +
                    "what lets the water actually reach the grounds.", 1),
            step(5, "Pour in the rest and stir once",
                "One gentle stir to wet every ground. Then put the lid on WITHOUT pressing down.", 1),
            step(6, "Wait until 4 minutes, then press slowly",
                "Slow and steady. Forcing the plunger pushes fines through the mesh and stirs up " +
                    "the bed you just brewed.", 4),
            step(7, "Decant it all straight away",
                "Coffee left sitting on the grounds keeps extracting and turns harsh — pour every " +
                    "last cup out of the press now.", 1),
        ),
    )

    // ── Assembly helpers ────────────────────────────────────────────────────────────────────

    /**
     * Builds a starter roadmap, filling in the derived fields the generator would normally set.
     *
     * `contentReady = false` is the important one: it is what tells the app these lessons have not
     * been written yet, so opening a node says "writing your lesson" and generates it, instead of
     * showing a blank one.
     */
    private fun roadmap(id: String, title: String, nodes: List<TreeNode>): LearningPath {
        val parents = nodes.flatMap { node -> node.children.map { it to node.id } }.toMap()
        return LearningPath(
            id = id,
            title = title,
            nodes = nodes.map { it.copy(parentId = parents[it.id], contentRef = "content/$id/${it.id}.md") },
        )
    }

    private fun node(
        id: String,
        title: String,
        estMinutes: Int,
        children: List<String> = emptyList(),
        tier: String? = null,
    ) = TreeNode(
        id = id,
        title = title,
        estMinutes = estMinutes,
        children = children,
        contentReady = false,
        tier = tier,
    )

    private fun step(order: Int, instruction: String, detail: String, estMinutes: Int) =
        TinkerStep(
            id = "step-$order",
            order = order,
            instruction = instruction,
            detail = detail,
            estMinutes = estMinutes,
        )
}

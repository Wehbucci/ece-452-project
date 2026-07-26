package com.example.grasp.data.repository

import com.example.grasp.core.layout.laneForBranch
import com.example.grasp.data.model.ChatMessage
import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.Mode
import com.example.grasp.data.model.ResourceKind
import com.example.grasp.data.model.ResourceLink
import com.example.grasp.data.model.SavedItem
import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.model.TinkerStep
import com.example.grasp.data.model.TopicSuggestion
import com.example.grasp.data.model.TreeNode

/**
 *
 * This is a singleton (`object`) purely so screens/presenters can grab it without any DI
 * wiring yet. When the real cloud + cache layer lands it will implement [PathRepository]
 * and be injected instead — nothing in the UI/presenter code should need to change.
 *
 * NONE of this is persisted and NONE of it calls a network. It exists only for the demo.
 */
object FakePathRepository : PathRepository {

    // ---- Home: popular topics -------------------------------------------------------
    override fun popularTopics(): List<TopicSuggestion> = listOf(
        TopicSuggestion("cooking-101", "Cooking for beginners", 6, Mode.LEARNER),
        TopicSuggestion("ml-101", "Machine learning", 8, Mode.LEARNER),
        TopicSuggestion("omelette", "Make a breakfast omelette", 6, Mode.TINKERER),
        TopicSuggestion("guitar-101", "Play your first song on guitar", 7, Mode.LEARNER),
    )

    // ---- Library: saved paths & guides ---------------------------------------------
    override fun savedItems(): List<SavedItem> = listOf(
        cookingPath(),
        machineLearningPath(),
        omeletteGuide(),
    )

    // ---- Learner roadmaps -----------------------------------------------------------
    override fun learningPath(id: String): LearningPath? = when (id) {
        "cooking-101" -> cookingPath()
        "ml-101" -> machineLearningPath()
        // Unknown ids resolve to the cooking sample so freshly-"generated" topics still demo.
        else -> cookingPath().copy(id = id, title = id.replace('-', ' ').replaceFirstChar { it.uppercase() })
    }

    private fun cookingPath() = LearningPath(
        id = "cooking-101",
        title = "Cooking for beginners",
        nodes = listOf(
            TreeNode("kitchen-basics", "Kitchen Basics", completed = true, estMinutes = 8,
                children = listOf("knife-skills"), contentRef = "content/cooking-101/kitchen-basics.md",
                lane = 170, tier = "FOUNDATIONS"),
            TreeNode("knife-skills", "Knife Skills", completed = true, estMinutes = 12,
                children = listOf("simple-techniques", "reading-recipe"),
                contentRef = "content/cooking-101/knife-skills.md", lane = 170),
            TreeNode("simple-techniques", "Simple Techniques", estMinutes = 15,
                children = listOf("building-flavor"), contentRef = "content/cooking-101/simple-techniques.md",
                lane = 100, tier = "TECHNIQUE · PICK A TRACK"),
            TreeNode("reading-recipe", "Reading a Recipe", estMinutes = 6,
                children = listOf("plating"), contentRef = "content/cooking-101/reading-recipe.md",
                lane = 240, tier = "TECHNIQUE · PICK A TRACK"),
            TreeNode("building-flavor", "Building Flavor", estMinutes = 18,
                children = listOf("plating"), lane = 100),
            TreeNode("plating", "Plating & Presentation", estMinutes = 10,
                children = listOf("add-branch"), lane = 170, tier = "MASTERY"),
            TreeNode("add-branch", "Add a branch", isBranchOut = true, lane = 170),
        ),
    )

    /**
     * The Machine Learning journey — the hero graph for the gamified roadmap. Its shape is the
     * whole point of the redesign: it SPLITS (data → supervised + unsupervised) and later
     * CONVERGES (regression + clustering → model evaluation), which the winding Duolingo-style
     * path draws as clear forks and merges. Lanes/tiers match the design handoff exactly.
     */
    private fun machineLearningPath() = LearningPath(
        id = "ml-101",
        title = "Machine Learning",
        nodes = listOf(
            TreeNode("what-is-ml", "What is ML?", completed = true, estMinutes = 5,
                children = listOf("types-of-learning"), contentRef = "content/ml-101/what-is-ml.md",
                lane = 170, tier = "FOUNDATIONS"),
            TreeNode("types-of-learning", "Types of Learning", completed = true, estMinutes = 8,
                children = listOf("data-basics"), contentRef = "content/ml-101/types-of-learning.md",
                lane = 108),
            TreeNode("data-basics", "Data Basics", completed = true, estMinutes = 10,
                children = listOf("supervised", "unsupervised"), contentRef = "content/ml-101/data-basics.md",
                lane = 230),
            TreeNode("supervised", "Supervised", estMinutes = 12, children = listOf("regression"),
                contentRef = "content/ml-101/supervised.md", lane = 96, tier = "CORE ML · PICK A TRACK"),
            TreeNode("unsupervised", "Unsupervised", estMinutes = 12, children = listOf("clustering"),
                contentRef = "content/ml-101/unsupervised.md", lane = 244),
            TreeNode("regression", "Regression", estMinutes = 10, children = listOf("model-evaluation"),
                contentRef = "content/ml-101/regression.md", lane = 96),
            TreeNode("clustering", "Clustering", estMinutes = 10, children = listOf("model-evaluation"),
                contentRef = "content/ml-101/clustering.md", lane = 244),
            TreeNode("model-evaluation", "Model Evaluation", estMinutes = 9,
                children = listOf("neural-networks"), contentRef = "content/ml-101/model-evaluation.md",
                lane = 170, tier = "MASTERY"),
            TreeNode("neural-networks", "Neural Networks", estMinutes = 15, children = listOf("branch-1"),
                contentRef = "content/ml-101/neural-networks.md", lane = 170),
            TreeNode("branch-1", "Branch out", isBranchOut = true, lane = 170),
        ),
    )

    // ---- Tinkerer guides ------------------------------------------------------------
    override fun tinkerGuide(id: String): TinkerGuide? = when (id) {
        "omelette" -> omeletteGuide()
        else -> omeletteGuide().copy(id = id, title = id.replace('-', ' ').replaceFirstChar { it.uppercase() })
    }

    private fun omeletteGuide() = TinkerGuide(
        id = "omelette",
        title = "Make a breakfast omelette",
        steps = listOf(
            TinkerStep("s1", 1, "Crack 3 eggs into a bowl", "Use a flat surface, not the rim, for fewer shells.", 1, done = true),
            TinkerStep("s2", 2, "Whisk with a pinch of salt", "Whisk ~30s until the color is even.", 1, done = true),
            TinkerStep("s3", 3, "Heat a non-stick pan on medium", "Too hot and the eggs brown; aim for gentle heat.", 2),
            TinkerStep("s4", 4, "Melt a knob of butter", "It should foam but not turn brown.", 1),
            TinkerStep("s5", 5, "Pour the eggs and stir gently", "Drag the set edges inward for 1–2 minutes.", 3),
            TinkerStep("s6", 6, "Fold and plate", "Fold in thirds and slide onto the plate.", 1),
        ),
    )

    // ---- Subtopic detail content (the lazy contentRef fetch) ------------------------
    override suspend fun subtopic(pathId: String, nodeId: String): Subtopic? {
        val path = learningPath(pathId) ?: return null
        val index = path.nodes.indexOfFirst { it.id == nodeId }
        // Nodes the user grew themselves aren't part of this canned path. Describe what they asked
        // for instead of handing back a different node's lesson under the wrong id — the caller
        // marks THAT id complete.
        val node = path.nodes.getOrNull(index) ?: TreeNode(
            id = nodeId,
            title = nodeId.replace('-', ' ').replaceFirstChar { it.uppercase() },
            estMinutes = 12,
        )
        return Subtopic(
            nodeId = node.id,
            title = node.title,
            stepLabel = "Step ${if (index >= 0) index + 1 else path.nodes.size + 1} of ${path.nodes.size}",
            summary = "A quick, beginner-friendly intro to ${node.title.lowercase()} — the essentials " +
                "you need before moving on.",
            whyItMatters = "Getting comfortable with ${node.title.lowercase()} makes every later " +
                "subtopic easier and builds the confidence to keep going.",
            body = listOf(
                "This is placeholder content for the skeleton. In the real app this text is " +
                    "generated by the AI and fetched from the node's contentRef.",
                "Each paragraph is a clickable block: highlight any part and open the inline AI " +
                    "chat to ask about it (see the chat screen).",
                "Generated content respects the user's preferences for difficulty, length and " +
                    "format (FR4.2).",
            ),
            resources = listOf(
                ResourceLink("Wikipedia: ${node.title}", "https://en.wikipedia.org", ResourceKind.ARTICLE),
                ResourceLink("A Beginner's Guide", "https://example.com/guide", ResourceKind.GUIDE),
                ResourceLink("Recommended book", "https://example.com/book", ResourceKind.BOOK),
            ),
            estMinutes = node.estMinutes,
            completed = node.completed,
        )
    }

    // ---- Chat history sample  --------------------------------------------------
    override fun sampleChat(): List<ChatMessage> = listOf(
        ChatMessage("m1", ChatMessage.Author.USER, "I don't get the part about dicing onions — what does 'dice' mean here?"),
        ChatMessage("m2", ChatMessage.Author.ASSISTANT,
            "Dicing means cutting into small, even cubes (about 1cm). Even sizes cook at the same " +
                "rate. Want a step-by-step for a safe dicing grip?"),
        ChatMessage("m3", ChatMessage.Author.USER, "Yes, and is this knife position safe?", imageUri = "sample://knife-grip"),
        ChatMessage("m4", ChatMessage.Author.ASSISTANT,
            "Good question — curl your fingertips under (the 'claw' grip) so the flat of the blade " +
                "rests against your knuckles. That keeps fingertips clear of the edge."),
    )

    override suspend fun createTopic(query: String, mode: Mode): LearningPath? {
        val id = query.trim().lowercase().replace(Regex("\\s+"), "-")
        return learningPath(id)
    }

    /**
     * Grows a branch WITHOUT calling the AI: one node named after what was asked for, capped by a
     * fresh affordance. Same shape as the real thing, so presenter tests exercise the real
     * splice/render path offline and deterministically.
     *
     * Unlike the real repository this keeps no state, so it can't see previously grown nodes: an
     * unknown [fromNodeId] is tolerated (it's an affordance this object never stored) and ids stay
     * unique only as far as the topic names differ.
     */
    override suspend fun growBranch(pathId: String, fromNodeId: String, topic: String): List<TreeNode> {
        val path = learningPath(pathId) ?: return emptyList()
        val target = path.nodes.firstOrNull { it.id == fromNodeId }
        val taken = path.nodes.mapTo(mutableSetOf()) { it.id }
        val title = topic.trim().ifEmpty { "New branch" }
        val topicId = uniqueId(slugify(title), taken)
        val branchId = uniqueId(BRANCH_OUT_ID, taken + topicId)
        // Consuming an affordance reuses its lane; a detour off a lesson has to find a free one.
        val lane = when {
            target == null -> TreeNode.LANE_CENTER
            target.isBranchOut -> target.lane
            // One lesson plus the affordance capping it, so two rows.
            else -> laneForBranch(path.nodes, target.id, branchLength = 2)
        }
        return listOf(
            TreeNode(
                id = topicId,
                title = title,
                estMinutes = 12,
                children = listOf(branchId),
                parentId = if (target?.isBranchOut == true) null else target?.id,
                contentRef = "content/$pathId/$topicId.md",
                lane = lane,
                tier = BRANCH_TIER,
            ),
            TreeNode(
                id = branchId,
                title = BRANCH_OUT_TITLE,
                parentId = topicId,
                isBranchOut = true,
                lane = lane,
            ),
        )
    }

    override suspend fun branchSuggestions(pathId: String, fromNodeId: String): List<String> =
        emptyList()

    override suspend fun updateNodeCompletion(pathId: String, nodeId: String, completed: Boolean) {
        // Demo implementation: no-op.
    }

    override suspend fun deleteTopic(pathId: String) {
    }
}

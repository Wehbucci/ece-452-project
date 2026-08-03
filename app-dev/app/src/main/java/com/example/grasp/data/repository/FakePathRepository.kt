package com.example.grasp.data.repository

import com.example.grasp.core.edit.EditAuthor
import com.example.grasp.core.edit.LessonEdit
import com.example.grasp.core.edit.RoadmapEdit
import com.example.grasp.core.edit.applyEdits
import com.example.grasp.core.edit.describeLessonEdits
import com.example.grasp.data.model.ChatMessage
import com.example.grasp.data.model.DownloadState
import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.LessonRevision
import com.example.grasp.data.model.asRevision
import com.example.grasp.data.model.restoredTo
import com.example.grasp.data.model.LessonBlock
import com.example.grasp.data.model.Mode
import com.example.grasp.data.model.ResourceKind
import com.example.grasp.data.model.ResourceLink
import com.example.grasp.data.model.SavedItem
import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.model.TinkerStep
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

    // ---- Library: saved paths & guides ---------------------------------------------
    override suspend fun savedItems(forceCache: Boolean): List<SavedItem> = listOf(
        cookingPath(),
        machineLearningPath(),
        omeletteGuide(),
    )

    /**
     * The same sum the interface's default computes, minus the thread hop.
     *
     * The default wraps its read in `withContext(Dispatchers.IO)`, which is right for a repository
     * that talks to Firestore and wrong for one that returns a constant: it turns an instant answer
     * into a suspension, so a presenter test driving its coroutines inline would race the assertion
     * that follows. Nothing here blocks, so nothing here needs to leave the caller's thread.
     */
    override suspend fun totalLessonsMastered(): Int = savedItems().sumOf { it.lessonsMastered }

    // ---- Learner roadmaps -----------------------------------------------------------
    /**
     * Edits made against the canned content, kept for as long as the app is running.
     *
     * The fake persists nothing and it stays that way — but an edit that vanished the instant the
     * sheet closed would make the editing UI impossible to try without signing in, and the demo
     * and the emulator both run signed out.
     */
    private val editedLessons = mutableMapOf<String, Subtopic>()
    private val editedPaths = mutableMapOf<String, LearningPath>()

    /** Undo history for those edits, newest first — same semantics as the real one, no storage. */
    private val revisions = mutableMapOf<String, MutableList<LessonRevision>>()
    private var revisionCount = 0

    /**
     * Forgets every edit, putting the canned content back.
     *
     * Exists for tests: this is an `object`, so an edit made in one of them is still there in the
     * next, and a shared singleton that quietly carries state between tests makes them pass or
     * fail depending on the order they ran in.
     */
    internal fun clearEdits() {
        editedLessons.clear()
        editedPaths.clear()
        revisions.clear()
    }

    override suspend fun learningPath(id: String): LearningPath? = editedPaths[id] ?: when (id) {
        "cooking-101" -> cookingPath()
        "ml-101" -> machineLearningPath()
        // Unknown ids resolve to the cooking sample so freshly-"generated" topics still demo.
        else -> cookingPath().copy(id = id, title = id.replace('-', ' ').replaceFirstChar { it.uppercase() }, downloadState = DownloadState.NONE)
    }

    private fun cookingPath() = LearningPath(
        id = "cooking-101",
        title = "Cooking for beginners",
        nodes = listOf(
            TreeNode("kitchen-basics", "Kitchen Basics", completed = true, estMinutes = 8,
                children = listOf("knife-skills"), contentRef = "content/cooking-101/kitchen-basics.md", tier = "FOUNDATIONS"),
            TreeNode("knife-skills", "Knife Skills", completed = true, estMinutes = 12,
                children = listOf("simple-techniques", "reading-recipe"),
                contentRef = "content/cooking-101/knife-skills.md"),
            TreeNode("simple-techniques", "Simple Techniques", estMinutes = 15,
                children = listOf("building-flavor"), contentRef = "content/cooking-101/simple-techniques.md", tier = "TECHNIQUE · PICK A TRACK"),
            TreeNode("reading-recipe", "Reading a Recipe", estMinutes = 6,
                children = listOf("plating"), contentRef = "content/cooking-101/reading-recipe.md", tier = "TECHNIQUE · PICK A TRACK"),
            TreeNode("building-flavor", "Building Flavor", estMinutes = 18,
                children = listOf("plating")),
            TreeNode("plating", "Plating & Presentation", estMinutes = 10,
                children = listOf("add-branch"), tier = "MASTERY"),
            TreeNode("add-branch", "Add a branch", isBranchOut = true),
        ),
        downloadState = DownloadState.AVAILABLE,
    )

    /**
     * The Machine Learning journey — the hero graph for the gamified roadmap. Its shape is the
     * whole point of the redesign: it SPLITS (data → supervised + unsupervised) and later
     * CONVERGES (regression + clustering → model evaluation), which the winding Duolingo-style
     * path draws as clear forks and merges.
     */
    private fun machineLearningPath() = LearningPath(
        id = "ml-101",
        title = "Machine Learning",
        nodes = listOf(
            TreeNode("what-is-ml", "What is ML?", completed = true, estMinutes = 5,
                children = listOf("types-of-learning"), contentRef = "content/ml-101/what-is-ml.md", tier = "FOUNDATIONS"),
            TreeNode("types-of-learning", "Types of Learning", completed = true, estMinutes = 8,
                children = listOf("data-basics"), contentRef = "content/ml-101/types-of-learning.md"),
            TreeNode("data-basics", "Data Basics", completed = true, estMinutes = 10,
                children = listOf("supervised", "unsupervised"), contentRef = "content/ml-101/data-basics.md"),
            TreeNode("supervised", "Supervised", estMinutes = 12, children = listOf("regression"),
                contentRef = "content/ml-101/supervised.md", tier = "CORE ML · PICK A TRACK"),
            TreeNode("unsupervised", "Unsupervised", estMinutes = 12, children = listOf("clustering"),
                contentRef = "content/ml-101/unsupervised.md"),
            TreeNode("regression", "Regression", estMinutes = 10, children = listOf("model-evaluation"),
                contentRef = "content/ml-101/regression.md"),
            TreeNode("clustering", "Clustering", estMinutes = 10, children = listOf("model-evaluation"),
                contentRef = "content/ml-101/clustering.md"),
            TreeNode("model-evaluation", "Model Evaluation", estMinutes = 9,
                children = listOf("neural-networks"), contentRef = "content/ml-101/model-evaluation.md", tier = "MASTERY"),
            TreeNode("neural-networks", "Neural Networks", estMinutes = 15, children = listOf("branch-1"),
                contentRef = "content/ml-101/neural-networks.md"),
            TreeNode("branch-1", "Branch out", isBranchOut = true),
        ),
    )

    // ---- Tinkerer guides ------------------------------------------------------------
    override suspend fun tinkerGuide(id: String): TinkerGuide? = when (id) {
        "omelette" -> omeletteGuide()
        else -> omeletteGuide().copy(id = id, title = id.replace('-', ' ').replaceFirstChar { it.uppercase() }, downloadState = DownloadState.NONE)
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
        downloadState = DownloadState.AVAILABLE,
    )

    // ---- Subtopic detail content (the lazy contentRef fetch) ------------------------
    override suspend fun subtopic(pathId: String, nodeId: String): Subtopic? {
        editedLessons[lessonKey(pathId, nodeId)]?.let { return it }
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
            sectionLabel = "Section ${if (index >= 0) index + 1 else path.nodes.size + 1} of ${path.nodes.size}",
            summary = "A quick, beginner-friendly intro to ${node.title.lowercase()} — the essentials " +
                "you need before moving on.",
            whyItMatters = "Getting comfortable with ${node.title.lowercase()} makes every later " +
                "subtopic easier and builds the confidence to keep going.",
            // Spelled-out block ids: this body is rebuilt on every call, so letting them default
            // to fresh ones would re-identify the same canned paragraphs each time it's read.
            body = listOf(
                LessonBlock.Heading("How this content works", id = "b0"),
                LessonBlock.Paragraph(
                    "This is placeholder content for the skeleton. In the real app this text is " +
                        "generated by the AI and fetched from the node's contentRef.",
                    id = "b1",
                ),
                LessonBlock.Paragraph(
                    "Each paragraph is a clickable block: highlight any part and open the inline " +
                        "AI chat to ask about it (see the chat screen).",
                    id = "b2",
                ),
                LessonBlock.Heading("What shapes it", id = "b3"),
                LessonBlock.Paragraph(
                    "Generated content respects the user's preferences for difficulty, length " +
                        "and format (FR4.2).",
                    id = "b4",
                ),
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
    override suspend fun sampleChat(): List<ChatMessage> = listOf(
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
     * Grows a branch WITHOUT calling the AI: a single node named after what was asked for. Same
     * shape as the real thing, so presenter tests exercise the real splice/render path offline and
     * deterministically.
     *
     * Unlike the real repository this keeps no state, so it can't see previously grown nodes: an
     * unknown [fromNodeId] is tolerated and ids stay unique only as far as the topic names differ.
     */
    override suspend fun growBranch(pathId: String, fromNodeId: String, topic: String): List<TreeNode> {
        val path = learningPath(pathId) ?: return emptyList()
        val from = path.nodes.firstOrNull { it.id == fromNodeId }
        val taken = path.nodes.mapTo(mutableSetOf()) { it.id }
        val title = topic.trim().ifEmpty { "New branch" }
        val topicId = uniqueId(slugify(title), taken)
        return listOf(
            TreeNode(
                id = topicId,
                title = title,
                estMinutes = 12,
                parentId = from?.id,
                contentRef = "content/$pathId/$topicId.md",
                // No position and no region pill: the board re-lays itself out around the new node.
            ),
        )
    }

    override suspend fun branchSuggestions(pathId: String, fromNodeId: String): List<String> =
        emptyList()

    override suspend fun editLesson(
        pathId: String,
        nodeId: String,
        edits: List<LessonEdit>,
        author: EditAuthor,
    ): Subtopic? {
        val before = subtopic(pathId, nodeId) ?: return null
        val after = before.applyEdits(edits, author) ?: return null
        revisions.getOrPut(lessonKey(pathId, nodeId)) { mutableListOf() }.add(
            0,
            before.asRevision(
                id = "rev-${revisionCount++}",
                savedAt = System.currentTimeMillis(),
                label = describeLessonEdits(edits),
                byAssistant = author == EditAuthor.ASSISTANT,
            ),
        )
        editedLessons[lessonKey(pathId, nodeId)] = after
        return after
    }

    override suspend fun lessonRevisions(pathId: String, nodeId: String): List<LessonRevision> =
        revisions[lessonKey(pathId, nodeId)].orEmpty().toList()

    override suspend fun undoLastLessonEdit(pathId: String, nodeId: String): Subtopic? {
        val stack = revisions[lessonKey(pathId, nodeId)] ?: return null
        val newest = stack.removeFirstOrNull() ?: return null
        val restored = (subtopic(pathId, nodeId) ?: return null).restoredTo(newest)
        editedLessons[lessonKey(pathId, nodeId)] = restored
        return restored
    }

    override suspend fun restoreLesson(pathId: String, nodeId: String, revisionId: String): Subtopic? {
        val stack = revisions[lessonKey(pathId, nodeId)] ?: return null
        val wanted = stack.firstOrNull { it.id == revisionId } ?: return null
        val current = subtopic(pathId, nodeId) ?: return null
        stack.add(
            0,
            current.asRevision(
                id = "rev-${revisionCount++}",
                savedAt = System.currentTimeMillis(),
                label = "Went back to an earlier version",
                byAssistant = false,
            ),
        )
        val restored = current.restoredTo(wanted)
        editedLessons[lessonKey(pathId, nodeId)] = restored
        return restored
    }

    override suspend fun editRoadmap(pathId: String, edits: List<RoadmapEdit>): LearningPath? {
        val after = (learningPath(pathId) ?: return null).applyEdits(edits) ?: return null
        editedPaths[pathId] = after
        return after
    }

    private fun lessonKey(pathId: String, nodeId: String) = "$pathId/$nodeId"

    override suspend fun updateNodeCompletion(pathId: String, nodeId: String, completed: Boolean) {
        // Demo implementation: no-op.
    }

    override suspend fun updateTinkerStepCompletion(guideId: String, stepId: String, completed: Boolean) {
        // Demo implementation: no-op.
    }


    override suspend fun deleteTopic(pathId: String) {
    }

    override suspend fun downloadTopic(pathId: String): Boolean = true
    override suspend fun cancelDownload(pathId: String) {}
    override suspend fun removeDownload(pathId: String) {}
    override suspend fun getStorageUsage(): Long = 45 * 1024 * 1024 // 45 MB dummy
    override suspend fun clearAllDownloads() {}
    override suspend fun setMobileDataAllowed(enabled: Boolean) {}
    override suspend fun isMobileDataAllowed(): Boolean = true
}

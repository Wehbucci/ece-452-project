package com.example.grasp.data.repository

import android.util.Log
import com.example.grasp.core.layout.laneForBranch
import com.example.grasp.data.model.Mode
import com.example.grasp.data.model.TreeNode
import com.example.grasp.data.model.ChatMessage
import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.paragraphs
import com.example.grasp.data.model.ResourceKind
import com.example.grasp.data.model.ResourceLink
import com.example.grasp.data.model.SavedItem
import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.model.TopicSuggestion
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebasePathRepository : PathRepository {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val uid: String? get() = auth.currentUser?.uid
    
    // Background scope for "Fire and Forget" cloud saves
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun userDocRef(uid: String) = db.collection("users").document(uid)

    private fun topicsRef(uid: String) = userDocRef(uid).collection("topics")

    private fun nodesRef(uid: String, topicId: String) =
        topicsRef(uid).document(topicId).collection("nodes")

    override fun popularTopics(): List<TopicSuggestion> = FakePathRepository.popularTopics()

    override fun savedItems(): List<SavedItem> {
        val uid = uid ?: return emptyList()
        return try {
            runBlocking {
                topicsRef(uid).get().await().documents.mapNotNull { doc ->
                    val id = doc.id
                    learningPath(id)
                }
            }
        } catch (e: Exception) {
            Log.e("FirebasePathRepo", "savedItems failed", e)
            emptyList()
        }
    }

    override fun learningPath(id: String): LearningPath? {
        val uid = uid ?: return FakePathRepository.learningPath(id)
        return try {
            runBlocking {
                val topicDoc = topicsRef(uid).document(id).get().await()
                val topicTitle = topicDoc.getString("title") ?: id.replace('-', ' ').replaceFirstChar { it.uppercase() }
                val nodesSnap = nodesRef(uid, id).get().await()
                val nodes = nodesSnap.documents
                    .mapNotNull { doc ->
                        val node = doc.toTreeNode() ?: return@mapNotNull null
                        val order = doc.getLong("order")?.toInt() ?: Int.MAX_VALUE
                        Pair(order, node)
                    }
                    .sortedBy { it.first }
                    .map { it.second }
                LearningPath(id = id, title = topicTitle, nodes = nodes)
            }
        } catch (e: Exception) {
            Log.e("FirebasePathRepo", "learningPath failed for $id", e)
            FakePathRepository.learningPath(id)
        }
    }

    override fun tinkerGuide(id: String): TinkerGuide? = null

    /**
     * Resolves a node's lesson, reading the copy [createTopic] cached on the node document.
     *
     * Falls back to generating it here for anything that copy is missing — a node whose up-front
     * generation failed, or one restored from an older topic. Every later open, including offline,
     * reads the cache.
     */
    override suspend fun subtopic(pathId: String, nodeId: String): Subtopic? =
        withContext(Dispatchers.IO) {
            val uid = uid ?: return@withContext FakePathRepository.subtopic(pathId, nodeId)
            try {
                val path = learningPath(pathId) ?: return@withContext null
                val node = path.nodes.firstOrNull { it.id == nodeId } ?: return@withContext null

                val cached = nodesRef(uid, pathId).document(nodeId).get().await().toGeneratedContent()
                val content = cached ?: generateAndCache(uid, pathId, path, node)

                // Lessons are what get numbered; the branch-out affordance isn't one.
                val lessons = path.nodes.filter { !it.isBranchOut }
                val position = lessons.indexOfFirst { it.id == nodeId }
                Subtopic(
                    nodeId = node.id,
                    title = node.title,
                    sectionLabel = "Section ${position + 1} of ${lessons.size}",
                    summary = content.summary,
                    whyItMatters = content.whyItMatters,
                    body = content.body,
                    resources = content.resources,
                    estMinutes = content.estMinutes,
                    completed = node.completed,
                )
            } catch (e: Exception) {
                Log.e("FirebasePathRepo", "subtopic failed for $pathId/$nodeId", e)
                FakePathRepository.subtopic(pathId, nodeId)
            }
        }

    /**
     * Writes the lesson for every node in [lessons], several at a time.
     *
     * Concurrent because a roadmap is 6-12 nodes and doing them one after another would take
     * minutes; [MAX_PARALLEL_GENERATIONS] keeps the burst polite to the AI service. Nodes whose
     * generation fails are simply absent from the result — they fall back to being written on
     * first open rather than sinking the whole roadmap.
     */
    private suspend fun generateContentFor(
        pathTitle: String,
        lessons: List<TreeNode>,
    ): Map<String, GeneratedContent> = coroutineScope {
        val gate = Semaphore(MAX_PARALLEL_GENERATIONS)
        lessons
            .mapIndexed { index, node ->
                async {
                    gate.withPermit {
                        node.id to generateSubtopicContent(
                            pathTitle = pathTitle,
                            nodeTitle = node.title,
                            previousTitles = lessons.take(index).takeLast(3).map { it.title },
                            upcomingTitles = lessons.drop(index + 1).take(2).map { it.title },
                            estMinutes = node.estMinutes,
                        )
                    }
                }
            }
            .awaitAll()
            .mapNotNull { (id, content) -> content?.let { id to it } }
            .toMap()
    }

    /**
     * Writes the lesson for [node] and stores it on the node document so it is only ever paid for
     * once. A failed generation is NOT cached — reopening the node retries.
     */
    private suspend fun generateAndCache(
        uid: String,
        pathId: String,
        path: LearningPath,
        node: TreeNode,
    ): GeneratedContent {
        val lessons = path.nodes.filter { !it.isBranchOut }
        val position = lessons.indexOfFirst { it.id == node.id }.coerceAtLeast(0)
        val generated = generateSubtopicContent(
            pathTitle = path.title,
            nodeTitle = node.title,
            // Only the immediate neighbours: enough to place the lesson without bloating the prompt.
            previousTitles = lessons.take(position).takeLast(3).map { it.title },
            upcomingTitles = lessons.drop(position + 1).take(2).map { it.title },
            estMinutes = node.estMinutes,
        ) ?: return placeholderContent(node.title, node.estMinutes)

        try {
            nodesRef(uid, pathId).document(node.id).set(
                contentFields(generated) + ("updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            ).await()
        } catch (e: Exception) {
            // The lesson is already written — failing to cache it only costs a re-generation.
            Log.e("FirebasePathRepo", "caching content failed for $pathId/${node.id}", e)
        }
        return generated
    }

    override suspend fun growBranch(
        pathId: String,
        fromNodeId: String,
        topic: String,
    ): List<TreeNode> = withContext(Dispatchers.IO) {
        val path = learningPath(pathId) ?: return@withContext emptyList()
        val target = path.nodes.firstOrNull { it.id == fromNodeId } ?: return@withContext emptyList()
        // Two ways in. Tapping the dashed affordance CONSUMES it, so the branch takes its place and
        // the node above it re-points at the branch. Branching from an ordinary lesson instead ADDS
        // a detour beside whatever that lesson already leads to, leaving the main line untouched.
        val consumed = target.takeIf { it.isBranchOut }
        val from = if (consumed != null) path.nodes.firstOrNull { fromNodeId in it.children } else target

        val generated = buildBranch(
            pathId = pathId,
            pathTitle = path.title,
            fromTitle = from?.title ?: path.title,
            topic = topic,
            takenIds = path.nodes.mapTo(mutableSetOf()) { it.id },
        )
        // A consumed affordance already holds a clear lane. A detour has to find its own, and it
        // can only do that once the branch exists, since the lane has to clear every row it spans.
        val lane = consumed?.lane ?: laneForBranch(path.nodes, target.id, generated.size)
        val branch = generated.mapIndexed { index, node ->
            node.copy(lane = lane, parentId = if (index == 0) from?.id else node.parentId)
        }

        // Same deal as a new topic: the branch's lessons are written now, not on first open.
        val content = generateContentFor(path.title, branch.filter { !it.isBranchOut })
        val grown = branch.map { node ->
            content[node.id]?.let { node.copy(estMinutes = it.estMinutes) } ?: node
        }

        val uid = uid ?: return@withContext grown // signed out: the branch lives in memory only
        try {
            // 1) The new nodes, appended after everything already on the path.
            grown.forEachIndexed { index, node ->
                nodesRef(uid, pathId).document(node.id)
                    .set(
                        nodeDoc(node, order = path.nodes.size + index, content = content[node.id]),
                        SetOptions.merge(),
                    )
                    .await()
            }
            // 2) Point the node above at the branch. Consuming an affordance swaps it out; a
            // detour is appended, so the node keeps the child it already had.
            if (from != null) {
                val children =
                    if (consumed != null) from.children.map { if (it == fromNodeId) grown.first().id else it }
                    else from.children + grown.first().id
                nodesRef(uid, pathId).document(from.id).set(
                    mapOf(
                        "children" to children,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                ).await()
            }
            // 3) Retire the consumed affordance — the one ending the new branch replaces it.
            if (consumed != null) nodesRef(uid, pathId).document(fromNodeId).delete().await()
            topicsRef(uid).document(pathId)
                .set(mapOf("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("FirebasePathRepo", "growBranch failed for $pathId/$fromNodeId", e)
        }
        grown
    }

    override suspend fun branchSuggestions(pathId: String, fromNodeId: String): List<String> =
        withContext(Dispatchers.IO) {
            val path = learningPath(pathId) ?: return@withContext emptyList()
            val target = path.nodes.firstOrNull { it.id == fromNodeId }
            // Ideas continue from the lesson the branch hangs off, which for the dashed affordance
            // is the node above it and otherwise is the tapped node itself.
            val from = if (target?.isBranchOut == true) {
                path.nodes.firstOrNull { fromNodeId in it.children }
            } else {
                target
            }
            suggestBranchTopics(
                pathTitle = path.title,
                fromTitle = from?.title ?: path.title,
                existingTitles = path.nodes.filter { !it.isBranchOut }.map { it.title },
            )
        }

    override fun sampleChat(): List<ChatMessage> = emptyList()

    override suspend fun createTopic(query: String, mode: Mode): LearningPath? =
        withContext(Dispatchers.IO) {
            val uid = uid ?: return@withContext FakePathRepository.createTopic(query, mode)
            val normalizedId = query.trim().lowercase()
                .replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "topic" }
            val title = query.trim().replaceFirstChar { it.uppercase() }

            val nodes = when (mode) {
                Mode.LEARNER -> buildLearnerTree(normalizedId, title)
                Mode.TINKERER -> buildTinkerTree(normalizedId, title)
            }
            // Every lesson is written NOW, so the roadmap is complete and readable (and offline)
            // the moment it opens. [subtopic] still generates on demand for anything missing here.
            val content = generateContentFor(title, nodes.filter { !it.isBranchOut })

            try {
                topicsRef(uid).document(normalizedId).set(
                    mapOf(
                        "title" to title,
                        "mode" to mode.name.lowercase(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "status" to "active",
                        "preferences" to mapOf(
                            "difficulty" to "beginner",
                            "length" to "standard",
                            "format" to "text",
                        ),
                    ),
                    SetOptions.merge(),
                ).await()

                val parentByNodeId = linkedMapOf<String, String?>()
                nodes.forEach { parentByNodeId[it.id] = null }
                nodes.forEach { node ->
                    node.children.forEach { childId -> parentByNodeId[childId] = node.id }
                }

                nodes.forEachIndexed { index, node ->
                    nodesRef(uid, normalizedId).document(node.id)
                        .set(
                            nodeDoc(node, index, parentByNodeId[node.id], content[node.id]),
                            SetOptions.merge(),
                        )
                        .await()
                }
                // Reading time comes from the generated lesson, so the board shows it immediately.
                val withTimes = nodes.map { node ->
                    content[node.id]?.let { node.copy(estMinutes = it.estMinutes) } ?: node
                }
                LearningPath(id = normalizedId, title = title, nodes = withTimes)
            } catch (e: Exception) {
                Log.e("FirebasePathRepo", "createTopic failed for $query", e)
                FakePathRepository.createTopic(query, mode)
            }
        }

    override suspend fun updateNodeCompletion(pathId: String, nodeId: String, completed: Boolean) {
        val uid = uid ?: return
        Log.d("FirebasePathRepo", "Cloud sync: Node $nodeId -> completed=$completed")
        try {
            // Use .set() with SetOptions.merge() instead of .update().
            // .update() fails if the document doesn't exist yet. 
            // .set() with merge will create it or update it if it exists.
            nodesRef(uid, pathId).document(nodeId)
                .set(
                    mapOf(
                        "completed" to completed, 
                        "state" to if (completed) "completed" else "active",
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()
            Log.d("FirebasePathRepo", "Cloud sync: Successfully updated node $nodeId")
        } catch (e: Exception) {
            Log.e("FirebasePathRepo", "Cloud sync: Failed to update node $nodeId", e)
        }
    }
    
    override suspend fun deleteTopic(pathId: String) {
        val uid = uid ?: return
        try {
            val nodeCollection = nodesRef(uid, pathId)
            val nodesSnapshot = nodeCollection.get().await()
            for (document in nodesSnapshot.documents) {
                document.reference.delete().await()
            }
            topicsRef(uid).document(pathId).delete().await()
            Log.d("FirebasePathRepo", "Deleted topic: $pathId")
        } catch (e: Exception) {
            Log.e("FirebasePathRepo", "deleteTopic failed for $pathId", e)
        }
    }

    /**
     * The Firestore shape of one node — one place, so every writer stays in sync.
     *
     * [content] is the node's generated lesson when it was written up front; it overrides the
     * "not generated yet" defaults below.
     */
    private fun nodeDoc(
        node: TreeNode,
        order: Int,
        parentId: String? = node.parentId,
        content: GeneratedContent? = null,
    ): Map<String, Any?> = mapOf(
        "id" to node.id,
        "title" to node.title,
        "updatedAt" to FieldValue.serverTimestamp(),
        "order" to order,
        "completed" to node.completed,
        "estMinutes" to node.estMinutes,
        "parentId" to parentId,
        "children" to node.children,
        "contentRef" to node.contentRef,
        "state" to when {
            node.isBranchOut -> "branch-out"
            node.completed -> "completed"
            else -> "active"
        },
        "contentStatus" to "not_generated",
        "tier" to node.tier,
        "lane" to node.lane,
    ) + (content?.let(::contentFields) ?: emptyMap())

    /** The generated-lesson half of a node document, shared by the up-front and lazy writers. */
    private fun contentFields(content: GeneratedContent): Map<String, Any?> = mapOf(
        "summary" to content.summary,
        "whyItMatters" to content.whyItMatters,
        "body" to content.body.map { it.toMap() },
        "resources" to content.resources.map {
            mapOf("title" to it.title, "url" to it.url, "kind" to it.kind.name)
        },
        "estMinutes" to content.estMinutes,
        "contentStatus" to CONTENT_GENERATED,
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toTreeNode(): TreeNode? {
        val nodeId = getString("id") ?: id
        val title = getString("title") ?: nodeId
        val children = (get("children") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        return TreeNode(
            id = nodeId,
            title = title,
            completed = getBoolean("completed") ?: false,
            estMinutes = getLong("estMinutes")?.toInt() ?: 0,
            children = children,
            parentId = getString("parentId"),
            contentRef = getString("contentRef"),
            isBranchOut = (getString("state") ?: "").equals("branch-out", ignoreCase = true),
            lane = getLong("lane")?.toInt() ?: TreeNode.LANE_CENTER,
            tier = getString("tier"),
        )
    }

    /** The cached lesson on a node document, or null if it hasn't been generated yet. */
    private fun com.google.firebase.firestore.DocumentSnapshot.toGeneratedContent(): GeneratedContent? {
        if (getString("contentStatus") != CONTENT_GENERATED) return null
        val summary = getString("summary").orEmpty()
        // Lessons saved before headings existed are plain strings; [lessonBlocks] reads both.
        val body = lessonBlocks((get("body") as? List<*>).orEmpty())
        if (summary.isBlank() || body.paragraphs().isEmpty()) return null
        val resources = (get("resources") as? List<*>).orEmpty()
            .filterIsInstance<Map<*, *>>()
            .mapNotNull { resource ->
                val title = resource["title"] as? String ?: return@mapNotNull null
                val url = resource["url"] as? String ?: return@mapNotNull null
                val kind = ResourceKind.entries
                    .firstOrNull { it.name.equals(resource["kind"] as? String, ignoreCase = true) }
                    ?: ResourceKind.ARTICLE
                ResourceLink(title, url, kind)
            }
        return GeneratedContent(
            summary = summary,
            whyItMatters = getString("whyItMatters").orEmpty(),
            body = body,
            resources = resources,
            estMinutes = getLong("estMinutes")?.toInt() ?: 0,
        )
    }

    private companion object {
        /** `contentStatus` marking a node whose lesson has been written and cached. */
        const val CONTENT_GENERATED = "generated"

        /** How many lessons to write at once when generating a whole roadmap or branch. */
        const val MAX_PARALLEL_GENERATIONS = 4
    }
}

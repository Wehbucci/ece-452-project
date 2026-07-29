package com.example.grasp.data.repository

import android.util.Log
import com.example.grasp.core.edit.EditAuthor
import com.example.grasp.core.edit.LessonEdit
import com.example.grasp.core.edit.RoadmapEdit
import com.example.grasp.core.edit.applyEdits
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
                    edited = content.edited,
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
        val from = path.nodes.firstOrNull { it.id == fromNodeId } ?: return@withContext emptyList()

        val generated = buildBranch(
            pathId = pathId,
            pathTitle = path.title,
            fromTitle = from.title,
            topic = topic,
            takenIds = path.nodes.mapTo(mutableSetOf()) { it.id },
        )
        val branch = generated.mapIndexed { index, node ->
            if (index == 0) node.copy(parentId = from.id) else node
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
            // 2) Hang the branch off the node the user picked, keeping everything it already led to.
            nodesRef(uid, pathId).document(from.id).set(
                mapOf(
                    "children" to from.children + grown.first().id,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
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
            val from = path.nodes.firstOrNull { it.id == fromNodeId }
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

                // Re-running a topic the user already has (same query, same normalised id) must
                // not write over lessons they have edited (FR4.5). Their nodes keep their content.
                val editedNodeIds = nodesRef(uid, normalizedId).get().await().documents
                    .filter { it.getBoolean("edited") == true }
                    .mapTo(mutableSetOf()) { it.id }

                val parentByNodeId = linkedMapOf<String, String?>()
                nodes.forEach { parentByNodeId[it.id] = null }
                nodes.forEach { node ->
                    node.children.forEach { childId -> parentByNodeId[childId] = node.id }
                }

                nodes.forEachIndexed { index, node ->
                    nodesRef(uid, normalizedId).document(node.id)
                        .set(
                            nodeDoc(
                                node,
                                index,
                                parentByNodeId[node.id],
                                content[node.id],
                                keepStoredContent = node.id in editedNodeIds,
                            ),
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

    /**
     * Applies the edits, then writes back ONLY the fields they actually changed.
     *
     * Rewriting the whole node document for a one-word fix would send the entire lesson — every
     * block, every picture credit — over the wire each keystroke-sized change, and would hand the
     * loser of two overlapping edits a document with the winner's work erased. A partial write
     * keeps a summary edit and a paragraph edit from treading on each other at all.
     */
    override suspend fun editLesson(
        pathId: String,
        nodeId: String,
        edits: List<LessonEdit>,
        author: EditAuthor,
    ): Subtopic? = withContext(Dispatchers.IO) {
        val before = subtopic(pathId, nodeId) ?: return@withContext null
        val after = before.applyEdits(edits, author) ?: return@withContext null
        // Signed out there is nowhere to write; the caller still gets the edited lesson.
        val uid = uid ?: return@withContext after

        val changed = changedLessonFields(before, after)
        try {
            nodesRef(uid, pathId).document(nodeId).set(
                changed + mapOf(
                    // Recorded even when nothing else changed, because it is what tells a later
                    // generation pass to keep its hands off this lesson.
                    "edited" to true,
                    "contentStatus" to CONTENT_GENERATED,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
            topicsRef(uid).document(pathId)
                .set(mapOf("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("FirebasePathRepo", "editLesson failed for $pathId/$nodeId", e)
        }
        after
    }

    override suspend fun editRoadmap(pathId: String, edits: List<RoadmapEdit>): LearningPath? =
        withContext(Dispatchers.IO) {
            val before = learningPath(pathId) ?: return@withContext null
            val after = before.applyEdits(edits) ?: return@withContext null
            val uid = uid ?: return@withContext after

            val wasById = before.nodes.associateBy { it.id }
            val wasOrder = before.nodes.withIndex().associate { (index, node) -> node.id to index }
            try {
                after.nodes.forEachIndexed { index, node ->
                    // Untouched nodes that also haven't shifted are left alone entirely.
                    if (wasById[node.id] == node && wasOrder[node.id] == index) return@forEachIndexed
                    nodesRef(uid, pathId).document(node.id)
                        .set(
                            // A structural edit never touches a lesson — not even the moved node's.
                            nodeDoc(node, index, keepStoredContent = true),
                            SetOptions.merge(),
                        )
                        .await()
                }
                (wasById.keys - after.nodes.mapTo(mutableSetOf()) { it.id }).forEach { goneId ->
                    nodesRef(uid, pathId).document(goneId).delete().await()
                }
                topicsRef(uid).document(pathId)
                    .set(mapOf("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.e("FirebasePathRepo", "editRoadmap failed for $pathId", e)
            }
            after
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
     * [content] is the node's generated lesson when it was written up front. [keepStoredContent]
     * leaves whatever lesson is already on the document untouched, which is how a re-created topic
     * avoids writing over one the user has edited — note that includes its `contentStatus`, since
     * marking an edited lesson "not generated" would invite the app to generate one over the top.
     */
    private fun nodeDoc(
        node: TreeNode,
        order: Int,
        parentId: String? = node.parentId,
        content: GeneratedContent? = null,
        keepStoredContent: Boolean = false,
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
        "tier" to node.tier,
    ) + when {
        keepStoredContent -> emptyMap()
        content != null -> contentFields(content)
        else -> mapOf("contentStatus" to "not_generated")
    }

    /** The generated-lesson half of a node document, shared by the up-front and lazy writers. */
    private fun contentFields(content: GeneratedContent): Map<String, Any?> = mapOf(
        "summary" to content.summary,
        "whyItMatters" to content.whyItMatters,
        "body" to content.body.map { it.toMap() },
        "resources" to content.resources.map(::resourceMap),
        "estMinutes" to content.estMinutes,
        "contentStatus" to CONTENT_GENERATED,
        "edited" to content.edited,
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
            contentReady = getString("contentStatus") == CONTENT_GENERATED,
            isBranchOut = (getString("state") ?: "").equals("branch-out", ignoreCase = true),
            // A stored "lane" on an older document is ignored: the board derives every position
            // from the shape of the tree now, so a saved coordinate would only fight it.
            tier = getString("tier"),
        )
    }

    /** The cached lesson on a node document, or null if it hasn't been generated yet. */
    private fun com.google.firebase.firestore.DocumentSnapshot.toGeneratedContent(): GeneratedContent? {
        val edited = getBoolean("edited") ?: false
        if (!edited && getString("contentStatus") != CONTENT_GENERATED) return null
        val summary = getString("summary").orEmpty()
        // Lessons saved before headings existed are plain strings; [lessonBlocks] reads both.
        val body = lessonBlocks((get("body") as? List<*>).orEmpty())
        // This bar judges GENERATION — a lesson that came back too thin to show is worth another
        // try. It must not be applied to a lesson the user has edited: they are allowed to cut it
        // down to a single heading if they like, and regenerating over that destroys their work
        // (FR4.5).
        if (!edited && (summary.isBlank() || body.paragraphs().isEmpty())) return null
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
            edited = edited,
        )
    }

    private companion object {
        /** `contentStatus` marking a node whose lesson has been written and cached. */
        const val CONTENT_GENERATED = "generated"

        /** How many lessons to write at once when generating a whole roadmap or branch. */
        const val MAX_PARALLEL_GENERATIONS = 4
    }
}

/** The stored shape of a "dive deeper" link, shared by every writer of one. */
private fun resourceMap(link: ResourceLink): Map<String, Any> =
    mapOf("title" to link.title, "url" to link.url, "kind" to link.kind.name)

/**
 * The stored lesson fields that differ between [before] and [after] — nothing else.
 *
 * Pure and separate from the write so the "only what changed" part is testable without Firestore:
 * it is the whole point of the partial write, and a version of this that quietly returns
 * everything would look identical from the outside until two people edited the same lesson.
 */
internal fun changedLessonFields(before: Subtopic, after: Subtopic): Map<String, Any?> = buildMap {
    if (after.summary != before.summary) put("summary", after.summary)
    if (after.whyItMatters != before.whyItMatters) put("whyItMatters", after.whyItMatters)
    if (after.body != before.body) put("body", after.body.map { it.toMap() })
    if (after.resources != before.resources) put("resources", after.resources.map(::resourceMap))
}

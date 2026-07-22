package com.example.grasp.data.repository

import android.util.Log
import com.example.grasp.data.model.Mode
import com.example.grasp.data.model.TreeNode
import com.example.grasp.data.model.ChatMessage
import com.example.grasp.data.model.LearningPath
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

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

    override fun subtopic(pathId: String, nodeId: String): Subtopic? {
        val uid = uid ?: return FakePathRepository.subtopic(pathId, nodeId)
        return try {
            val path = learningPath(pathId) ?: return null
            val node = path.nodes.firstOrNull { it.id == nodeId } ?: return null
            val index = path.nodes.indexOfFirst { it.id == nodeId }.coerceAtLeast(0)
            Subtopic(
                nodeId = node.id,
                title = node.title,
                stepLabel = "Step ${index + 1} of ${path.nodes.size}",
                summary = "A generated summary for ${node.title.lowercase()}.",
                whyItMatters = "This node helps the learner move from the overview to practical understanding.",
                body = listOf(
                    "This lesson content is generated lazily and stored per node so the roadmap stays lightweight.",
                    "A future version can replace this placeholder with richer markdown or rich media.",
                ),
                resources = listOf(
                    ResourceLink("Wikipedia", "https://en.wikipedia.org", ResourceKind.ARTICLE),
                    ResourceLink("Suggested guide", "https://example.com/guide", ResourceKind.GUIDE),
                ),
                estMinutes = node.estMinutes,
                completed = node.completed,
            )
        } catch (e: Exception) {
            Log.e("FirebasePathRepo", "subtopic failed for $pathId/$nodeId", e)
            FakePathRepository.subtopic(pathId, nodeId)
        }
    }

    override fun sampleChat(): List<ChatMessage> = emptyList()

    override fun createTopic(query: String, mode: Mode): LearningPath? {
        val uid = uid ?: return FakePathRepository.createTopic(query, mode)
        val normalizedId = query.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "topic" }
        val title = query.trim().replaceFirstChar { it.uppercase() }
        val nodes = when(mode) {
            Mode.LEARNER -> generateLearnerTree(normalizedId, title)
            Mode.TINKERER -> generateTinkerTree(normalizedId, title)
        }
                return try {
            runBlocking {
                val topicRef = topicsRef(uid).document(normalizedId)
                topicRef.set(
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
                    node.children.forEach { childId ->
                        parentByNodeId[childId] = node.id
                    }
                }

                nodes.forEachIndexed { index, node ->
                    val nodeDoc = nodesRef(uid, normalizedId).document(node.id)
                    nodeDoc.set(
                        mapOf(
                            "id" to node.id,
                            "title" to node.title,
                            "updatedAt" to FieldValue.serverTimestamp(),
                            "order" to index,
                            "completed" to node.completed,
                            "estMinutes" to node.estMinutes,
                            "parentId" to parentByNodeId[node.id],
                            "children" to node.children,
                            "contentRef" to node.contentRef,
                            "state" to if (node.isBranchOut) "branch-out" else if (node.completed) "completed" else "active",
                            "contentStatus" to "not_generated",
                            "lane" to node.lane,
                        ),
                        SetOptions.merge(),
                    ).await()
                }
            }
            LearningPath(id = normalizedId, title = title, nodes = nodes)
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

    private fun generateLearnerTree(pathId: String, title: String): List<TreeNode> {
        return buildLearnerTree(pathId, title)
    }

    private fun generateTinkerTree(pathId: String, title: String): List<TreeNode> {
        return buildTinkerTree(pathId, title)
    }

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
        )
    }
}

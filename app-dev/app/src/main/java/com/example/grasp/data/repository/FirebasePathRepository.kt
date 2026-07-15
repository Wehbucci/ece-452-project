package com.example.grasp.data.repository

import android.util.Log
import com.example.grasp.data.model.ChatMessage
import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.Mode
import com.example.grasp.data.model.ResourceKind
import com.example.grasp.data.model.ResourceLink
import com.example.grasp.data.model.SavedItem
import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.model.TreeNode
import com.example.grasp.data.model.TopicSuggestion
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

class FirebasePathRepository : PathRepository {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val uid: String? get() = auth.currentUser?.uid

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
                val nodes = nodesSnap.documents.mapNotNull { it.toTreeNode() }
                    .sortedBy { it.id.lowercase() }
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
        val nodes = buildGeneratedNodes(normalizedId, title, mode)
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
                            "parentId" to null,
                            "children" to node.children,
                            "contentRef" to node.contentRef,
                            "state" to if (node.isBranchOut) "branch-out" else if (node.completed) "completed" else "active",
                            "contentStatus" to "not_generated",
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

    override fun updateNodeCompletion(pathId: String, nodeId: String, completed: Boolean) {
        val uid = uid ?: return
        try {
            runBlocking {
                nodesRef(uid, pathId).document(nodeId)
                    .update("completed", completed, "state", if (completed) "completed" else "active")
                    .await()
            }
        } catch (e: Exception) {
            Log.e("FirebasePathRepo", "updateNodeCompletion failed for $pathId/$nodeId", e)
        }
    }

    private fun buildGeneratedNodes(pathId: String, title: String, mode: Mode): List<TreeNode> {
        val root = TreeNode(
            id = "overview",
            title = "Overview of $title",
            completed = false,
            estMinutes = 8,
            children = listOf("core-concepts", "practice", "next-steps"),
            contentRef = "content/$pathId/overview.md",
        )
        val concepts = TreeNode(
            id = "core-concepts",
            title = if (mode == Mode.TINKERER) "Gather materials" else "Core concepts",
            completed = false,
            estMinutes = 10,
            children = emptyList(),
            contentRef = "content/$pathId/core-concepts.md",
        )
        val practice = TreeNode(
            id = "practice",
            title = if (mode == Mode.TINKERER) "Try the first step" else "Practice",
            completed = false,
            estMinutes = 10,
            children = emptyList(),
            contentRef = "content/$pathId/practice.md",
        )
        val nextSteps = TreeNode(
            id = "next-steps",
            title = if (mode == Mode.TINKERER) "Wrap up" else "Next steps",
            completed = false,
            estMinutes = 6,
            children = emptyList(),
            contentRef = "content/$pathId/next-steps.md",
        )
        return listOf(root, concepts, practice, nextSteps)
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
            contentRef = getString("contentRef"),
            isBranchOut = (getString("state") ?: "").equals("branch-out", ignoreCase = true),
        )
    }
}

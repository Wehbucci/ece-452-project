package com.example.grasp.data.repository

import com.example.grasp.data.model.ChatMessage
import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.Mode
import com.example.grasp.data.model.SavedItem
import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.model.TopicSuggestion
import com.example.grasp.data.model.TreeNode

/**
 * The Model layer's boundary for paths, guides and content (overview.md §5).
 *
 * Presenters depend on THIS interface, never on a concrete implementation, so the data
 * source can change (in-memory fake → cloud API + local cache) without touching the UI or
 * presenters. The current implementation is [FakePathRepository]; the real one will sit
 * behind the same methods and add cloud sync + offline caching.
 *
 * NOTE: methods are synchronous for the skeleton. When wiring the real backend they will
 * become `suspend` functions (overview.md §4 — use coroutines for AI/network) and return
 * Result types so presenters can surface graceful errors (NFR 3.1).
 */
interface PathRepository {

    /** Popular topics for the Home screen carousel/list. */
    fun popularTopics(): List<TopicSuggestion>

    /** Everything the signed-in user has saved, for the Library (FR7.1/7.2). */
    fun savedItems(): List<SavedItem>

    /** A Learner roadmap by id, or null if not found. */
    fun learningPath(id: String): LearningPath?

    /** A Tinkerer guide by id, or null if not found. */
    fun tinkerGuide(id: String): TinkerGuide?

    /**
     * Resolved content for one node (the lazy `contentRef` fetch), or null if not found.
     *
     * Suspending because the first call for a node may have to GENERATE its lesson before it can
     * return; afterwards the cached copy comes back immediately (and offline).
     */
    suspend fun subtopic(pathId: String, nodeId: String): Subtopic?

    /** A sample conversation for the chat skeleton (FR5.5 history). */
    fun sampleChat(): List<ChatMessage>

    /**
     * Create or hydrate a topic path from a user prompt.
     *
     * Suspending because generating the roadmap is an AI round-trip — it must not run on the
     * main thread (NFR 1.2: no noticeable lag or UI glitch).
     */
    suspend fun createTopic(query: String, mode: Mode): LearningPath?

    /**
     * Grows a new branch where the branch-out affordance [fromNodeId] sits, and persists it.
     *
     * [fromNodeId] is consumed: it is replaced by the generated nodes, and a fresh affordance is
     * appended after them. Returns the new nodes in order (lessons, then the new affordance), or
     * an empty list if nothing could be grown.
     */
    suspend fun growBranch(pathId: String, fromNodeId: String, topic: String): List<TreeNode>

    /** Starter branch ideas for the "grow your path" sheet; empty when none could be produced. */
    suspend fun branchSuggestions(pathId: String, fromNodeId: String): List<String>

    /** Persist completion state for a node in Firestore or the backend. */
    suspend fun updateNodeCompletion(pathId: String, nodeId: String, completed: Boolean)

    suspend fun updateTinkerStepCompletion(guideId: String, stepId: String, completed: Boolean)

    suspend fun deleteTopic(pathId: String)
}

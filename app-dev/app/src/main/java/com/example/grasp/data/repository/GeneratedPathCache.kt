package com.example.grasp.data.repository

import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.model.TinkerGuide

/**
 * In-memory store for AI-generated paths, guides, and node content.
 *
 * [AiTreeGenerator] writes here after a successful generation; presenters read here before
 * falling back to [FakePathRepository]. Nothing is persisted — restarting the app clears all
 * entries (persistence comes later via Firestore).
 */
object GeneratedPathCache {
    val paths = mutableMapOf<String, LearningPath>()
    val guides = mutableMapOf<String, TinkerGuide>()
    /** Keyed by "${pathId}__${nodeId}" — generated lazily when a node is first opened. */
    val subtopics = mutableMapOf<String, Subtopic>()
}

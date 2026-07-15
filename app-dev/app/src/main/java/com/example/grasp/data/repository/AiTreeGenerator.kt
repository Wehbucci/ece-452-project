package com.example.grasp.data.repository

import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.ResourceKind
import com.example.grasp.data.model.ResourceLink
import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.model.TinkerStep
import com.example.grasp.data.model.TreeNode
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Generates a [LearningPath] or [TinkerGuide] from a user-supplied topic/task string by
 * calling Gemini and parsing the returned JSON.
 *
 * This is a stateless singleton — all state lives in [GeneratedPathCache] after generation.
 * The model is initialised lazily so Firebase can be set up before the first call.
 */
object AiTreeGenerator {

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(modelName = "gemini-3.1-flash-lite")
    }

    suspend fun generateLearningPath(topic: String): LearningPath = withContext(Dispatchers.IO) {
        val prompt = """
            Generate a learning roadmap for the topic: "$topic"

            Return ONLY a valid JSON object — no markdown fences, no extra text:
            {
              "title": "Concise topic title",
              "nodes": [
                {"id": "slug-id", "title": "Node Title", "estMinutes": 10, "children": ["next-id"]},
                {"id": "next-id", "title": "Next Node", "estMinutes": 8, "children": []}
              ]
            }

            Rules:
            - 5 to 8 nodes total
            - IDs: unique, lowercase-with-dashes
            - children: array of IDs of logically-following nodes (empty array for leaves)
            - estMinutes: realistic study time per node, 5–25
            - Order the nodes so the root (no incoming edges) appears first
            - Return ONLY the JSON object, nothing else
        """.trimIndent()

        val response = model.generateContent(prompt)
        val raw = response.text?.trim() ?: error("Empty response from AI")
        parseLearningPath(topic, raw)
    }

    suspend fun generateTinkerGuide(task: String): TinkerGuide = withContext(Dispatchers.IO) {
        val prompt = """
            Generate a step-by-step guide for the task: "$task"

            Return ONLY a valid JSON object — no markdown fences, no extra text:
            {
              "title": "Concise task title",
              "steps": [
                {"id": "s1", "order": 1, "instruction": "Do X", "detail": "Helpful tip", "estMinutes": 2},
                {"id": "s2", "order": 2, "instruction": "Do Y", "detail": "", "estMinutes": 3}
              ]
            }

            Rules:
            - 5 to 10 steps
            - Steps are flat and ordered (order starts at 1)
            - detail: optional extra tip, or empty string
            - estMinutes: realistic time per step, 1–15
            - Return ONLY the JSON object, nothing else
        """.trimIndent()

        val response = model.generateContent(prompt)
        val raw = response.text?.trim() ?: error("Empty response from AI")
        parseTinkerGuide(task, raw)
    }

    private fun parseLearningPath(topic: String, raw: String): LearningPath {
        val json = JSONObject(stripFences(raw))
        val title = json.optString("title", topic)
        val arr = json.getJSONArray("nodes")
        val nodes = (0 until arr.length()).map { i ->
            val n = arr.getJSONObject(i)
            TreeNode(
                id = n.getString("id"),
                title = n.getString("title"),
                estMinutes = n.optInt("estMinutes", 10),
                children = buildList {
                    val c = n.optJSONArray("children") ?: return@buildList
                    repeat(c.length()) { add(c.getString(it)) }
                },
            )
        }.toMutableList()
        nodes += TreeNode("add-branch", "Add a branch", isBranchOut = true)
        return LearningPath(
            id = topic.trim().lowercase().replace(Regex("\\s+"), "-"),
            title = title,
            nodes = nodes,
        )
    }

    private fun parseTinkerGuide(task: String, raw: String): TinkerGuide {
        val json = JSONObject(stripFences(raw))
        val title = json.optString("title", task)
        val arr = json.getJSONArray("steps")
        val steps = (0 until arr.length()).map { i ->
            val s = arr.getJSONObject(i)
            TinkerStep(
                id = s.optString("id", "s${i + 1}"),
                order = s.optInt("order", i + 1),
                instruction = s.getString("instruction"),
                detail = s.optString("detail", ""),
                estMinutes = s.optInt("estMinutes", 5),
            )
        }
        return TinkerGuide(
            id = task.trim().lowercase().replace(Regex("\\s+"), "-"),
            title = title,
            steps = steps,
        )
    }

    suspend fun generateSubtopic(
        pathTitle: String,
        nodeTitle: String,
        stepLabel: String,
        estMinutes: Int,
    ): Subtopic = withContext(Dispatchers.IO) {
        val prompt = """
            Generate educational content for a subtopic in a learning roadmap.

            Topic/Path: "$pathTitle"
            Subtopic: "$nodeTitle"
            Position: $stepLabel
            Estimated time: $estMinutes minutes

            Return ONLY a valid JSON object, no markdown fences, no extra text:
            {
              "summary": "2-3 sentence intro to this subtopic",
              "whyItMatters": "1-2 sentences on why this matters",
              "body": [
                "First paragraph of content...",
                "Second paragraph...",
                "Third paragraph..."
              ]
            }

            Rules:
            - summary: concise intro, 2-3 sentences
            - whyItMatters: practical relevance, 1-2 sentences
            - body: 3-5 paragraphs, educational and clear, matching the $estMinutes-minute depth
            - Return ONLY the JSON object, nothing else
        """.trimIndent()

        val response = model.generateContent(prompt)
        val raw = response.text?.trim() ?: error("Empty AI response")
        parseSubtopic(nodeTitle, stepLabel, estMinutes, raw)
    }

    private fun parseSubtopic(
        nodeTitle: String,
        stepLabel: String,
        estMinutes: Int,
        raw: String,
    ): Subtopic {
        val json = JSONObject(stripFences(raw))
        val body = buildList {
            val arr = json.getJSONArray("body")
            repeat(arr.length()) { add(arr.getString(it)) }
        }
        return Subtopic(
            nodeId = nodeTitle.lowercase().replace(Regex("\\s+"), "-"),
            title = nodeTitle,
            stepLabel = stepLabel,
            summary = json.getString("summary"),
            whyItMatters = json.getString("whyItMatters"),
            body = body,
            resources = listOf(
                ResourceLink("Wikipedia: $nodeTitle", "https://en.wikipedia.org", ResourceKind.ARTICLE),
                ResourceLink("Beginner's Guide to $nodeTitle", "https://example.com/guide", ResourceKind.GUIDE),
                ResourceLink("Recommended book", "https://example.com/book", ResourceKind.BOOK),
            ),
            estMinutes = estMinutes,
        )
    }

    // Strip markdown code fences in case Gemini adds them despite the instruction.
    private fun stripFences(raw: String) = raw
        .removePrefix("```json").removePrefix("```")
        .removeSuffix("```").trim()
}
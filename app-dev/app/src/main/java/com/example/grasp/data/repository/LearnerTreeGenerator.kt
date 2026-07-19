package com.example.grasp.data.repository

import com.example.grasp.data.model.TreeNode
import kotlinx.coroutines.runBlocking
import org.json.JSONObject


private data class GeneratedTree(
    val nodes: List<GeneratedNode>
)

private data class GeneratedNode(
    val id: String,
    val title: String,
    val children: List<String>
)


fun buildLearnerTree(
    pathId: String,
    title: String,
): List<TreeNode> {

    return try {

    val prompt = """
        Generate a learning roadmap as a JSON tree for the topic below.

        Topic: $title

        Shape you are building:
        This is a guided path through the topic, not a wide taxonomy of
        independent categories. Most of the roadmap should read as a single
        line of concepts, each building on the last, from foundational to
        advanced (or chronological order, for historical/narrative topics).
        At one or two points where the topic genuinely contains a short,
        self-contained detour — something worth knowing but not essential to
        the main line of progression — the path can branch off into a short
        side node. That side branch stays short (usually just 1 node) and
        ends there. It does NOT reconnect to the main path or to any other
        branch later. Every node has exactly one parent.

        Step 1 — Think before structuring (do not output this step):
        Lay out the topic as a rough sequence from first-principles to advanced,
        or chronologically for historical topics. Identify at most 1-2 points
        where a short, self-contained detour makes sense as a side node.
        Everywhere else, keep it a single path forward.

        A detour must be specifically motivated by the node it branches from —
        not just any independent topic that happens to fit somewhere in that
        general era or category. Ask: "is this detour a defining, characteristic
        feature of THIS SPECIFIC node's content, not just something loosely
        adjacent to it?" For example, off a node about a particular cultural
        golden age, a detour into that era's defining art form is well-motivated.
        A detour into something merely from the same rough time period, without
        a direct thematic tie to what that specific node is actually about, is
        NOT well-motivated — pick a different, more specifically-tied detour
        instead, or skip the detour for that point entirely if none fits.

        Step 2 — Build the tree from that reasoning:
        - Create exactly one root node: the entry point / introduction to the
        topic. It must be the first node in the output.
        - Most nodes should have exactly one child, continuing the roadmap
        forward as a single path.
        - At a detour point, a node's "children" list may contain 2 ids: one
        continues the main path, the other starts a short side branch that
        ends on its own (empty "children" list) within 1-2 nodes. Title that
        side node starting with "Explore: " followed by its specific topic
        (e.g. "Explore: The Panhellenic Games").
        - Do not use more than 2 detour points in the entire roadmap. Most
        topics should have 0 or 1.
        - Never split the root itself into more than 2 immediate children.
        - Every node id must appear in exactly one other node's "children"
        list (except the root, which appears in none). Never list the same
        node id as a child of two different nodes.
        - Order everything along the main path from foundational to advanced,
        or chronologically for historical topics.

        Size guidance (soft, not a target to hit exactly):
        - Total nodes, including root: roughly 6-12 depending on topic breadth.
        A focused topic might be 6-8 nodes in a near-straight line. A
        broader topic might be 9-12 with one short detour.

        Each node must contain:
        - id: lowercase hyphen-separated string
        - title: concise lesson title
        - children: list of child node ids (usually exactly one id; 2 ids only
        at a deliberate detour point; empty list only for the final node in
        the main path, or the end of a side branch)

        Return ONLY valid JSON, no commentary, no markdown fences.

        Format:
        {
        "nodes": [
            {
            "id": "example-node",
            "title": "Example Node",
            "children": ["next-node"]
            }
        ]
        }
    """.trimIndent()

        val response = runBlocking {

            val builder = StringBuilder()

            GeminiChatSession(
                """
                You generate structured learning trees.
                Always output valid JSON only.
                """
            )
                .sendMessageStream(prompt)
                .collect { text ->
                    builder.append(text)
                }

            builder.toString()
        }


        val cleanedJson = response
            .replace("```json", "")
            .replace("```", "")
            .trim()


        val jsonObject = JSONObject(cleanedJson)
        val jsonNodes = jsonObject.getJSONArray("nodes")

        val generatedNodes = mutableListOf<GeneratedNode>()

        for (i in 0 until jsonNodes.length()) {

            val node = jsonNodes.getJSONObject(i)

            val childrenArray = node.getJSONArray("children")
            val children = mutableListOf<String>()

            for (j in 0 until childrenArray.length()) {
                children.add(childrenArray.getString(j))
            }

            generatedNodes.add(
                GeneratedNode(
                    id = node.getString("id"),
                    title = node.getString("title"),
                    children = children
                )
            )
        }

        if (generatedNodes.isEmpty()) {
            throw Exception("Generated tree has no nodes")
        }

        generatedNodes[0] = generatedNodes[0].copy(id = pathId)

        // --- Lane assignment ---
        // Lanes are dp values on PathLayout's 340dp-wide canvas (centerX(lane) = lane.dp).
        // Each node's on-screen footprint is PathLayout.SlotWidth (112dp), so siblings need
        // to be at least ~112dp apart center-to-center to avoid overlapping circles/labels.
        // Keep `slotWidth` in sync with PathLayout.SlotWidth if that constant ever changes.
        val nodeById = generatedNodes.associateBy { it.id }
        val laneById = mutableMapOf<String, Int>()
        val slotWidth = 112

        fun assignLanes(nodeId: String, lane: Int, siblingGap: Int) {
            if (laneById.containsKey(nodeId)) return
            laneById[nodeId] = lane
            val node = nodeById[nodeId] ?: return
            val kids = node.children
            if (kids.isEmpty()) return
            if (kids.size == 1) {
                assignLanes(kids[0], TreeNode.LANE_CENTER, siblingGap)
                return
            }
            val totalWidth = siblingGap * (kids.size - 1)
            val start = lane - totalWidth / 2
            val nextGap = (siblingGap * 0.7).toInt().coerceAtLeast(slotWidth)
            kids.forEachIndexed { i, childId ->
                assignLanes(childId, start + i * siblingGap, nextGap)
            }
        }

        assignLanes(pathId, TreeNode.LANE_CENTER, siblingGap = slotWidth + 20)

        generatedNodes.map { node ->

            TreeNode(
                id = node.id,
                title = node.title,
                completed = false,
                estMinutes = 0,
                children = node.children,
                contentRef = "content/$pathId/${node.id}.md",
                lane = laneById[node.id] ?: TreeNode.LANE_CENTER,
            )
        }


    } catch (e: Exception) {

        // fallback if Gemini generation fails
        listOf(
            TreeNode(
                id = pathId,
                title = "Introduction to $title",
                completed = false,
                estMinutes = 0,
                children = emptyList(),
                contentRef = "content/$pathId/overview.md"
            )
        )
    }
}
package com.example.grasp.data.repository

import com.example.grasp.data.model.Mode
import com.example.grasp.data.model.TreeNode
import kotlinx.coroutines.runBlocking
import org.json.JSONObject


private data class GeneratedTree(
    val nodes: List<GeneratedNode>
)

private data class GeneratedNode(
    val id: String,
    val title: String,
    val estMinutes: Int,
    val children: List<String>
)


fun buildGeneratedNodesForTopic(
    pathId: String,
    title: String,
    mode: Mode
): List<TreeNode> {

    return try {

        val prompt = """
            Generate a learning roadmap as a JSON tree.

            Topic: $title

            Learning mode: $mode

            Requirements:
            - Generate a high-level roadmap containing only the most important concepts.
            - The number of top-level nodes should depend on the complexity of the topic.
            - Prefer approximately:
                - 5-8 nodes for focused topics
                - 8-12 nodes for broad topics
                - Up to 15 nodes for very broad domains when necessary
            - Each node should represent a distinct learning milestone or concept.
            - Organize concepts from foundational knowledge toward more advanced concepts.
            - Group related concepts together and place specialized topics after their prerequisites.
            - Use branching when the topic contains distinct areas, skills, or applications.
            - Do not force a balanced tree or fixed structure. The depth and number of children should depend on the topic.
            - Avoid creating a simple linear chain unless the topic naturally requires it.
            - Avoid including overly specific advanced topics too early.
            - Leave room for the learner to expand the roadmap with additional branches later.

            The roadmap should:
            - Begin with an introductory/foundational concept.
            - Group related concepts together.
            - Place specialized topics and applications after their prerequisites.

            Each node must contain:
            - id: lowercase hyphen-separated string
            - title: concise lesson title
            - estMinutes: estimated learning time
            - children: list of child node ids

            Return ONLY valid JSON.

            Format:
            {
              "nodes": [
                {
                  "id": "example-node",
                  "title": "Example Node",
                  "estMinutes": 10,
                  "children": []
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
                    estMinutes = node.getInt("estMinutes"),
                    children = children
                )
            )
        }


        val generated = GeneratedTree(generatedNodes)


        generated.nodes.map { node ->

            TreeNode(
                id = node.id,
                title = node.title,
                completed = false,
                estMinutes = node.estMinutes,
                children = node.children,
                contentRef = "content/$pathId/${node.id}.md"
            )
        }


    } catch (e: Exception) {

        // fallback if Gemini generation fails
        listOf(
            TreeNode(
                id = "overview-$pathId",
                title = "Introduction to $title",
                completed = false,
                estMinutes = 10,
                children = emptyList(),
                contentRef = "content/$pathId/overview.md"
            )
        )
    }
}
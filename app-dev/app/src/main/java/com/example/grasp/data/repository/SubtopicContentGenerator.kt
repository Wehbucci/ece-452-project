package com.example.grasp.data.repository

import com.example.grasp.data.model.ResourceKind
import com.example.grasp.data.model.ResourceLink
import java.net.URLEncoder

/**
 * The AI-written body of one roadmap node — everything behind a [com.example.grasp.data.model
 * .TreeNode.contentRef].
 *
 * Deliberately mirrors the persisted shape so the repository can cache it verbatim and rebuild a
 * [com.example.grasp.data.model.Subtopic] from Firestore without re-generating (NFR 1.2, 3.2).
 */
data class GeneratedContent(
    val summary: String,
    val whyItMatters: String,
    val body: List<String>,
    val resources: List<ResourceLink>,
    val estMinutes: Int,
)

private const val SYSTEM_INSTRUCTION = """
    You write one short lesson at a time for a structured learning app.
    You are concise, concrete and encouraging, and you never pad.
    Always output valid JSON only.
"""

private const val MIN_EST_MINUTES = 3
private const val MAX_EST_MINUTES = 60

/**
 * Writes the lesson for one node (FR4.1, FR4.3).
 *
 * Called lazily — the roadmap only generates structure up front, so this runs the first time a
 * user opens a node and its result is cached by the repository.
 *
 * @param pathTitle the roadmap this node belongs to.
 * @param nodeTitle the node being written.
 * @param previousTitles the few nodes already covered — the model may build on these instead of
 *        re-explaining them.
 * @param upcomingTitles what comes next, so this lesson doesn't wander into it.
 * @param estMinutes the roadmap's own estimate; 0 asks the model for one.
 *
 * Returns null if the call failed or came back unusable, letting the caller fall back rather than
 * showing a half-empty lesson.
 */
suspend fun generateSubtopicContent(
    pathTitle: String,
    nodeTitle: String,
    previousTitles: List<String>,
    upcomingTitles: List<String>,
    estMinutes: Int = 0,
): GeneratedContent? {
    val json = geminiJson(
        SYSTEM_INSTRUCTION,
        contentPrompt(pathTitle, nodeTitle, previousTitles, upcomingTitles),
    ) ?: return null

    val summary = json.optString("summary").trim()
    val body = json.stringList("body")
    // Summary + body ARE the lesson; without them there is nothing worth showing.
    if (summary.isEmpty() || body.isEmpty()) return null

    return GeneratedContent(
        summary = summary,
        whyItMatters = json.optString("whyItMatters").trim()
            .ifEmpty { "Understanding $nodeTitle is what makes the rest of $pathTitle click." },
        body = body,
        resources = json.objectList("resources")
            .mapNotNull { resource ->
                val title = resource.optString("title").trim()
                val url = resource.optString("url").trim()
                // Models invent plausible-looking links; anything that isn't a real absolute URL
                // is dropped rather than shown as a dead "dive deeper" row.
                if (title.isEmpty() || !url.startsWith("http")) return@mapNotNull null
                ResourceLink(title, url, resourceKind(resource.optString("kind")))
            }
            .take(4)
            .ifEmpty { listOf(wikipediaSearch(nodeTitle)) },
        estMinutes = estMinutes.takeIf { it > 0 }
            ?: json.optInt("estMinutes", 0).coerceIn(MIN_EST_MINUTES, MAX_EST_MINUTES),
    )
}

/**
 * The lesson shown when generation fails — honest about being unwritten rather than pretending to
 * be content, so the user knows to retry rather than assuming this is all there is (NFR 3.1).
 */
fun placeholderContent(nodeTitle: String, estMinutes: Int): GeneratedContent = GeneratedContent(
    summary = "We couldn't write this lesson just now.",
    whyItMatters = "$nodeTitle is still part of your roadmap — reopen it once you're back online " +
        "and it will be generated then.",
    body = listOf(
        "This node's content hasn't been generated yet. It needs a connection to the AI service " +
            "the first time you open it; after that it's saved and works offline.",
        "You can still ask the AI assistant about $nodeTitle, mark this node complete, or branch " +
            "off in a new direction from the roadmap.",
    ),
    resources = listOf(wikipediaSearch(nodeTitle)),
    estMinutes = estMinutes.coerceAtLeast(MIN_EST_MINUTES),
)

private fun resourceKind(raw: String): ResourceKind =
    ResourceKind.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
        ?: ResourceKind.ARTICLE

/** A link we can always build ourselves, so "dive deeper" is never empty. */
private fun wikipediaSearch(nodeTitle: String) = ResourceLink(
    title = "Wikipedia: $nodeTitle",
    url = "https://en.wikipedia.org/w/index.php?search=" +
        URLEncoder.encode(nodeTitle, Charsets.UTF_8.name()),
    kind = ResourceKind.ARTICLE,
)

private fun contentPrompt(
    pathTitle: String,
    nodeTitle: String,
    previousTitles: List<String>,
    upcomingTitles: List<String>,
) = """
    Write one lesson for a learner working through a roadmap on "$pathTitle".

    The lesson is: "$nodeTitle"
    ${context("Already covered (build on this, don't re-explain it)", previousTitles)}
    ${context("Coming up later (do NOT cover this here)", upcomingTitles)}

    Write these parts:
    - summary: 1-2 sentences a beginner could read in ten seconds — what this lesson is about.
    - whyItMatters: 1-2 sentences on what this unlocks for them, concretely.
    - body: 3 to 5 paragraphs that actually teach "$nodeTitle". Each paragraph must stand on its
    own and cover one idea, because the app shows them as separate blocks the learner can tap to
    ask questions about. Use concrete examples. Plain prose only — no markdown, no headings, no
    bullet points, no numbering. 2-4 sentences per paragraph.
    - resources: 2-4 places to go deeper. Use only URLs you are confident actually exist —
    Wikipedia articles, official documentation, well-known books or courses. If you are not sure a
    URL is real, leave that resource out. kind must be one of ARTICLE, BOOK, VIDEO, GUIDE.
    - estMinutes: honest whole-minute estimate to read and absorb this lesson.

    Return ONLY valid JSON, no commentary, no markdown fences.

    Format:
    {
    "summary": "...",
    "whyItMatters": "...",
    "body": ["First paragraph.", "Second paragraph.", "Third paragraph."],
    "resources": [{ "title": "...", "url": "https://...", "kind": "ARTICLE" }],
    "estMinutes": 10
    }
""".trimIndent()

private fun context(label: String, titles: List<String>): String =
    if (titles.isEmpty()) "" else "$label: ${titles.joinToString("; ")}"

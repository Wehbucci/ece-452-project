package com.example.grasp.data.repository

import com.example.grasp.data.model.LessonBlock
import com.example.grasp.data.model.ResourceKind
import com.example.grasp.data.model.ResourceLink
import com.example.grasp.data.model.paragraphs
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
    val body: List<LessonBlock>,
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
    val body = lessonBlocks(json.objectList("body").map { block ->
        mapOf(
            "type" to block.optString("type"),
            "text" to block.optString("text"),
            "level" to block.optInt("level", 1),
        )
    })
    // Summary + teaching prose ARE the lesson; headings alone are not worth showing.
    if (summary.isEmpty() || body.paragraphs().isEmpty()) return null

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
        LessonBlock.Paragraph(
            "This node's content hasn't been generated yet. It needs a connection to the AI " +
                "service the first time you open it; after that it's saved and works offline.",
        ),
        LessonBlock.Paragraph(
            "You can still ask the AI assistant about $nodeTitle, mark this node complete, or " +
                "branch off in a new direction from the roadmap.",
        ),
    ),
    resources = listOf(wikipediaSearch(nodeTitle)),
    estMinutes = estMinutes.coerceAtLeast(MIN_EST_MINUTES),
)

/**
 * Turns raw body entries into [LessonBlock]s, from either the model's answer or a stored lesson.
 *
 * Tolerates two shapes on purpose. The current one is a map per block with a `type` of "heading"
 * or "paragraph"; the other is a bare string, which is how lessons generated before headings
 * existed were saved. Those read back as plain paragraphs rather than breaking.
 *
 * Pure, so the tolerance is actually testable.
 */
internal fun lessonBlocks(raw: List<*>): List<LessonBlock> = raw.mapNotNull { entry ->
    when (entry) {
        is String -> entry.trim().ifBlank { null }?.let(LessonBlock::Paragraph)
        is Map<*, *> -> {
            val text = (entry["text"] as? String)?.trim().orEmpty()
            when {
                text.isEmpty() -> null
                (entry["type"] as? String).equals("heading", ignoreCase = true) -> LessonBlock.Heading(
                    text = text,
                    // Anything past a subheading is flattened; a phone lesson has no use for it.
                    level = ((entry["level"] as? Number)?.toInt() ?: 1).coerceIn(1, 2),
                )
                else -> LessonBlock.Paragraph(text)
            }
        }
        else -> null
    }
}

/** The Firestore/JSON shape of [LessonBlock], so the writer and reader can't drift apart. */
internal fun LessonBlock.toMap(): Map<String, Any> = when (this) {
    is LessonBlock.Heading -> mapOf("type" to "heading", "text" to text, "level" to level)
    is LessonBlock.Paragraph -> mapOf("type" to "paragraph", "text" to text)
}

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

    The body is THE LESSON ITSELF. It is the only place the learner is taught this material —
    there is no textbook behind it and no video to watch. Teach the content directly. Do not
    describe what the lesson will cover, do not tell the learner what they are about to learn, and
    do not tell them to go read about it somewhere else. Explain the actual ideas, define the
    actual terms, and work through actual examples, the way a good tutor would if this were the
    only explanation the learner ever got.

    Write these parts:
    - summary: 1-2 sentences a beginner could read in ten seconds, framing what this lesson covers.
    - whyItMatters: 1-2 sentences on what this unlocks for them, concretely.
    - body: the lesson, as an ordered list of heading and paragraph blocks.
      Structure it like a well-organised set of notes, not a wall of text:
      · Open with a heading (level 1), then its paragraphs. Use 3 to 5 level-1 headings in total,
      each naming the idea that section teaches — a real title like "Why labelled data matters",
      never a filler like "Introduction", "Overview", "Details" or "Conclusion".
      · Give a section a level-2 subheading only when it genuinely splits into named parts, such as
      two contrasting approaches or a worked example inside a longer explanation. Most sections
      need none, and a section must never have exactly one subheading.
      · Under the headings, write 5 to 8 substantial paragraphs in total. Introduce each term the
      first time you use it. Ground every idea in a concrete example, a worked case, or a number
      the learner can picture. Where something is commonly misunderstood, say so and correct it.
      Someone who reads only this and nothing else should come away actually understanding
      "$nodeTitle" and able to use it.
      · Each paragraph covers one idea and stands on its own, because the app shows paragraphs as
      separate blocks the learner can tap to ask questions about. 4-7 sentences per paragraph.
      · Headings are short label text, no numbering and no trailing punctuation. Paragraphs are
      plain prose — no markdown, no bullet points, no numbering.
    - resources: 2-4 OPTIONAL places to explore further, for a learner who finishes the lesson and
    wants more. These are extras, not where the teaching happens, so never rely on them to carry
    material you left out of the body. Use only URLs you are confident actually exist — Wikipedia
    articles, official documentation, well-known books or courses. If you are not sure a URL is
    real, leave that resource out. kind must be one of ARTICLE, BOOK, VIDEO, GUIDE.
    - estMinutes: honest whole-minute estimate to read and absorb this lesson.

    Return ONLY valid JSON, no commentary, no markdown fences.

    Format:
    {
    "summary": "...",
    "whyItMatters": "...",
    "body": [
        { "type": "heading", "text": "What a perceptron actually does", "level": 1 },
        { "type": "paragraph", "text": "First teaching paragraph." },
        { "type": "paragraph", "text": "Second teaching paragraph." },
        { "type": "heading", "text": "Where it breaks down", "level": 1 },
        { "type": "paragraph", "text": "Third teaching paragraph." }
    ],
    "resources": [{ "title": "...", "url": "https://...", "kind": "ARTICLE" }],
    "estMinutes": 10
    }
""".trimIndent()

private fun context(label: String, titles: List<String>): String =
    if (titles.isEmpty()) "" else "$label: ${titles.joinToString("; ")}"

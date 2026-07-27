package com.example.grasp.data.repository

import com.example.grasp.data.model.DiagramItem
import com.example.grasp.data.model.DiagramKind
import com.example.grasp.data.model.LessonBlock
import com.example.grasp.data.model.ResourceKind
import com.example.grasp.data.model.ResourceLink
import com.example.grasp.data.model.paragraphs
import org.json.JSONObject
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

/** Caps on visuals. A lesson is prose first; these are there to stop it becoming a slideshow. */
private const val MAX_DIAGRAMS = 2
private const val MAX_IMAGES = 2
private const val MAX_DIAGRAM_ITEMS = 5

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
    val body = resolveImages(json.objectList("body").mapNotNull(::parseBodyBlock))
    // Summary + teaching prose ARE the lesson; headings and pictures alone are not worth showing.
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
 * One block of the model's answer. Image blocks arrive as a SEARCH QUERY, not a URL — the model
 * says what picture the lesson wants and [resolveImages] goes and finds a real one.
 */
private sealed interface ParsedBlock {
    data class Ready(val block: LessonBlock) : ParsedBlock
    data class WantsImage(val query: String, val caption: String) : ParsedBlock
}

private fun parseBodyBlock(json: JSONObject): ParsedBlock? {
    val type = json.optString("type").lowercase()
    val text = json.optString("text").trim()
    return when (type) {
        "image" -> {
            val query = json.optString("query").trim()
            if (query.isEmpty()) null else ParsedBlock.WantsImage(query, text)
        }

        "code" -> lessonBlocks(
            listOf(
                mapOf(
                    "type" to "code",
                    "text" to json.optString("text"),
                    "language" to json.optString("language"),
                ),
            ),
        ).firstOrNull()?.let(ParsedBlock::Ready)

        "diagram" -> {
            val items = json.objectList("items").mapNotNull { item ->
                val label = item.optString("label").trim()
                if (label.isEmpty()) return@mapNotNull null
                DiagramItem(
                    label = label,
                    detail = item.optString("detail").trim(),
                    value = item.optDouble("value", 0.0).toFloat(),
                )
            }
            val kind = DiagramKind.entries
                .firstOrNull { it.name.equals(json.optString("kind"), ignoreCase = true) }
            // A diagram with one item isn't a diagram, and an unknown shape can't be drawn.
            if (kind == null || items.size < 2) null
            else ParsedBlock.Ready(LessonBlock.Diagram(text, kind, items.take(MAX_DIAGRAM_ITEMS)))
        }

        else -> lessonBlocks(
            listOf(mapOf("type" to type, "text" to text, "level" to json.optInt("level", 1))),
        ).firstOrNull()?.let(ParsedBlock::Ready)
    }
}

/**
 * Swaps each image request for a real picture, dropping the ones nothing was found for.
 *
 * Sequential rather than concurrent: a lesson asks for at most [MAX_IMAGES] pictures, and the
 * whole roadmap is already generating several lessons at once.
 */
private suspend fun resolveImages(parsed: List<ParsedBlock>): List<LessonBlock> {
    var remaining = MAX_IMAGES
    return parsed.mapNotNull { block ->
        when (block) {
            is ParsedBlock.Ready -> block.block
            is ParsedBlock.WantsImage -> {
                if (remaining <= 0) null
                else findLessonImage(block.query, block.caption)?.also { remaining-- }
            }
        }
    }
}

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
            val raw = (entry["text"] as? String).orEmpty()
            val text = raw.trim()
            when ((entry["type"] as? String)?.lowercase()) {
                "heading" -> text.ifEmpty { null }?.let {
                    LessonBlock.Heading(
                        text = it,
                        // Past a subheading it's flattened; a phone lesson has no use for more.
                        level = ((entry["level"] as? Number)?.toInt() ?: 1).coerceIn(1, 2),
                    )
                }

                "diagram" -> {
                    val kind = DiagramKind.entries
                        .firstOrNull { it.name.equals(entry["kind"] as? String, ignoreCase = true) }
                    val items = (entry["items"] as? List<*>).orEmpty()
                        .filterIsInstance<Map<*, *>>()
                        .mapNotNull { item ->
                            val label = (item["label"] as? String)?.trim().orEmpty()
                            if (label.isEmpty()) return@mapNotNull null
                            DiagramItem(
                                label = label,
                                detail = (item["detail"] as? String)?.trim().orEmpty(),
                                value = (item["value"] as? Number)?.toFloat() ?: 0f,
                            )
                        }
                    if (kind == null || items.size < 2) null
                    else LessonBlock.Diagram(text, kind, items)
                }

                // Blank lines around it go, but the inner layout is untouched — for a language
                // like Python the indentation IS the syntax.
                "code" -> raw.trim('\n', '\r').trimEnd().ifBlank { null }?.let {
                    LessonBlock.Code(it, (entry["language"] as? String)?.trim().orEmpty())
                }

                "image" -> {
                    val url = (entry["url"] as? String)?.trim().orEmpty()
                    if (!url.startsWith("http")) null
                    else LessonBlock.Image(
                        text = text,
                        url = url,
                        sourceUrl = (entry["sourceUrl"] as? String)?.trim().orEmpty(),
                        credit = (entry["credit"] as? String)?.trim().orEmpty(),
                    )
                }

                else -> text.ifEmpty { null }?.let(LessonBlock::Paragraph)
            }
        }

        else -> null
    }
}

/** The Firestore/JSON shape of [LessonBlock], so the writer and reader can't drift apart. */
internal fun LessonBlock.toMap(): Map<String, Any> = when (this) {
    is LessonBlock.Heading -> mapOf("type" to "heading", "text" to text, "level" to level)
    is LessonBlock.Paragraph -> mapOf("type" to "paragraph", "text" to text)
    is LessonBlock.Code -> mapOf("type" to "code", "text" to text, "language" to language)
    is LessonBlock.Diagram -> mapOf(
        "type" to "diagram",
        "text" to text,
        "kind" to kind.name,
        "items" to items.map {
            mapOf("label" to it.label, "detail" to it.detail, "value" to it.value)
        },
    )
    is LessonBlock.Image -> mapOf(
        "type" to "image",
        "text" to text,
        "url" to url,
        "sourceUrl" to sourceUrl,
        "credit" to credit,
    )
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
      first time you use it. Where something is commonly misunderstood, say so and correct it.
      Someone who reads only this and nothing else should come away actually understanding
      "$nodeTitle" and able to use it.
      · EXAMPLES ARE NOT OPTIONAL. Every idea you introduce gets something concrete attached to
      it, and the lesson as a whole must contain at least one fully worked example that runs from
      a specific starting point through to its result, showing the intermediate steps rather than
      asserting the answer. Use real specifics — actual numbers, actual values, an actual named
      case — never "for example, imagine a situation where...". A learner should be able to follow
      your example on their own and get the same result.
      · When the subject involves code, syntax, commands or configuration, show it as a "code"
      block right after the paragraph that sets it up: 3-12 lines, complete enough to run or type
      as-is, with realistic names rather than foo and bar. Set "language" to the language or shell.
      Use code blocks ONLY for real code — never for prose you want to look technical.
      · Each paragraph covers one idea and stands on its own, because the app shows paragraphs as
      separate blocks the learner can tap to ask questions about. 4-7 sentences per paragraph.
      · Headings are short label text, no numbering and no trailing punctuation. Paragraphs are
      plain prose — no markdown, no bullet points, no numbering.
      · You may place up to $MAX_DIAGRAMS diagram blocks and up to $MAX_IMAGES image blocks among
      the paragraphs, right after the paragraph each one illustrates. Both are OPTIONAL: include
      one only where seeing it genuinely helps, and never as decoration. A lesson with no visual
      is better than a lesson with a pointless one, and the prose must still stand alone without
      them.

    Diagram blocks are drawn by the app, so they carry only structure and short labels. Pick the
    kind that matches what you are showing, and use one only when the content really has that
    shape:
      · "flow" — a genuine ordered sequence where each stage follows from the last.
      · "compare" — two or three things set against each other on the same terms.
      · "bar" — quantities worth comparing. Give each item a "value"; they only need to be
      right relative to each other.
    Every diagram needs 2 to $MAX_DIAGRAM_ITEMS items, a short "label" each, an optional one-line
    "detail", and a "text" caption saying what the diagram shows.

    Image blocks are looked up in Wikimedia Commons, so give a "query" of 2-5 plain search words
    naming a concrete, photographable thing ("bell pepper julienne cut", "Hertzsprung-Russell
    diagram"), plus a "text" caption. Do NOT request an image for an abstract idea, and do not
    write a URL — searching is the app's job, and a request that finds nothing is simply dropped.
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
        { "type": "paragraph", "text": "Second teaching paragraph, setting up the example below." },
        { "type": "code", "language": "python", "text": "weights = [0.5, -0.2]\ntotal = sum(w * x for w, x in zip(weights, inputs))" },
        {
        "type": "diagram",
        "kind": "flow",
        "text": "How one input becomes one output",
        "items": [
            { "label": "Inputs", "detail": "The numbers fed in." },
            { "label": "Weighted sum", "detail": "Each input scaled and added up." },
            { "label": "Threshold", "detail": "Fires only past a cutoff." }
        ]
        },
        { "type": "heading", "text": "Where it breaks down", "level": 1 },
        { "type": "paragraph", "text": "Third teaching paragraph." },
        { "type": "image", "query": "perceptron diagram", "text": "The classic single-layer perceptron." }
    ],
    "resources": [{ "title": "...", "url": "https://...", "kind": "ARTICLE" }],
    "estMinutes": 10
    }
""".trimIndent()

private fun context(label: String, titles: List<String>): String =
    if (titles.isEmpty()) "" else "$label: ${titles.joinToString("; ")}"

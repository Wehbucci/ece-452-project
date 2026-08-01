package com.example.grasp.data.repository

import com.example.grasp.data.model.TreeNode

/**
 * Cuts roadmap section titles down to something the board can actually show.
 *
 * A node on the journey is one 112dp slot wide and its label is capped at two lines, so anything
 * past about four words arrives ellipsised — which turned the roadmap into a set of unreadable
 * stubs the user had to tap one by one to find out what they were. The fix is at the source: the
 * title a node is SAVED with is the short one, so every surface that shows it (board, sheet
 * header, chat context, Library) shows the same readable name.
 *
 * Gemini does the shortening rather than a `take(n)` because the goal is a title that still says
 * what the lesson is: "Understanding the Role of Labelled Data in Supervised Learning" has to
 * become "Labelled Data", not "Understanding the Role…".
 *
 * Never throws and never returns fewer titles than it was given: a failed call leaves every title
 * exactly as it was (NFR 3.1), which is merely the old behaviour rather than a broken roadmap.
 */

/** The board's budget. Titles at or under this are left alone and never cost an AI call. */
const val MAX_TITLE_WORDS = 4

private const val SYSTEM_INSTRUCTION = """
    You shorten learning-roadmap section titles.
    Always output valid JSON only.
"""

/** Words in [title], counting any run of non-space as one. */
internal fun titleWordCount(title: String): Int =
    title.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

/** Whether [title] is too long to sit under a node on the board. */
internal fun isTitleTooLong(title: String): Boolean = titleWordCount(title) > MAX_TITLE_WORDS

/**
 * Returns [titles] with every over-long entry replaced by a [MAX_TITLE_WORDS]-word version,
 * in the same order and the same length.
 *
 * Returns the input untouched when nothing is over-long — the common case for a freshly generated
 * roadmap, and the reason this is safe to call on every generation.
 */
suspend fun shortenTitles(pathTitle: String, titles: List<String>): List<String> {
    if (titles.none(::isTitleTooLong)) return titles

    val shortened = geminiJson(SYSTEM_INSTRUCTION, shortenPrompt(pathTitle, titles))
        ?.stringList("titles")
        .orEmpty()

    // Length is the contract the caller relies on to zip these back onto nodes, so an answer with
    // the wrong number of entries is discarded whole rather than silently misaligning titles.
    if (shortened.size != titles.size) return titles

    return titles.mapIndexed { index, original ->
        val candidate = cleanTitle(shortened[index])
        // Only ever an improvement: a "short" title that came back empty, or longer than what it
        // replaces, is not one.
        if (candidate.isEmpty() || titleWordCount(candidate) > MAX_TITLE_WORDS) original
        else candidate
    }
}

/** [shortenTitles] applied to a list of nodes, keeping everything else about them intact. */
suspend fun withShortTitles(pathTitle: String, nodes: List<TreeNode>): List<TreeNode> {
    if (nodes.none { isTitleTooLong(it.title) }) return nodes
    val shortened = shortenTitles(pathTitle, nodes.map { it.title })
    return nodes.mapIndexed { index, node -> node.copy(title = shortened[index]) }
}

/**
 * Strips what a model tends to wrap a title in — quotes, a trailing full stop, "1. " numbering —
 * so a good answer isn't rejected for its packaging.
 *
 * Repeated rather than done once, because the packaging NESTS and each layer only becomes
 * strippable after the one outside it is gone: `'Labelled Data':` is not surrounded by quotes
 * until the trailing colon has been removed, and `1. "Labelled Data"` is not quoted until the
 * numbering has. A single pass left the quotes on and the title looking mangled.
 */
internal fun cleanTitle(raw: String): String {
    var text = raw.trim()
    repeat(PACKAGING_LAYERS) {
        text = text
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .replace(Regex("^\\d+[.)]\\s*"), "")
            .trimEnd('.', ':', '—', '-')
            .trim()
    }
    return text
}

/** How deep the nesting above is worth unwrapping. Two is realistic; the third is slack. */
private const val PACKAGING_LAYERS = 3

private fun shortenPrompt(pathTitle: String, titles: List<String>) = """
    These are the section titles of a learning roadmap on "$pathTitle". They are being shown as
    labels under small circular nodes on a map, so each one must be very short.

    Rewrite EVERY title to be at most $MAX_TITLE_WORDS words.

    Rules:
    - Return exactly ${titles.size} titles, in the SAME ORDER as the input.
    - Each title must still name what that specific section is about. Shorten by dropping filler
    words ("Understanding", "An Introduction to", "The Basics of"), not by becoming generic:
    two different sections must not end up with the same title.
    - Keep the subject's own terminology, including proper nouns and acronyms.
    - Title Case. No numbering, no trailing punctuation, no quotes.
    - A title that is already $MAX_TITLE_WORDS words or fewer must be returned UNCHANGED.

    Titles, in order:
    ${titles.mapIndexed { index, title -> "${index + 1}. $title" }.joinToString("\n    ")}

    Return ONLY valid JSON, no commentary, no markdown fences.

    Format:
    { "titles": ["First Title", "Second Title"] }
""".trimIndent()

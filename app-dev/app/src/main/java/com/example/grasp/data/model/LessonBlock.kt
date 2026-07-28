package com.example.grasp.data.model

/**
 * One piece of a generated lesson body.
 *
 * The body is a FLAT list rather than nested sections on purpose: a block's position in that list
 * is its stable id, which is what scopes an inline AI chat to the part of the lesson the user
 * tapped (FR5.2, and `ChatPresenter`'s `pathId__nodeId__blockIndex` chat ids). Nesting would make
 * those ids depend on the shape of the content.
 */
sealed interface LessonBlock {

    /** The text of this block: heading label, paragraph prose, or a visual's caption. */
    val text: String

    /**
     * A section title inside the lesson.
     *
     * @property level 1 for a section heading, 2 for a subheading within one.
     */
    data class Heading(override val text: String, val level: Int = 1) : LessonBlock

    /** A paragraph of the lesson — the tappable unit the user can ask questions about. */
    data class Paragraph(override val text: String) : LessonBlock

    /**
     * A short code or command sample, shown monospaced.
     *
     * Its own kind rather than a paragraph because code has to keep its line breaks and spacing —
     * for a topic like Python, indentation IS the syntax, and prose styling destroys it.
     *
     * @property language what it's written in, for the little corner label; may be empty.
     */
    data class Code(override val text: String, val language: String = "") : LessonBlock

    /**
     * A diagram the app DRAWS from a spec the AI wrote (FR4.4).
     *
     * @property text the caption shown under the drawing.
     * @property unit optional unit label to append to values (e.g. "%", "kg").
     * @property maxValue optional fixed scale; bars are relative to this if set, else to the max item.
     * @property showValues if false, numeric labels are hidden (useful for purely relative sizes).
     */
    data class Diagram(
        override val text: String,
        val kind: DiagramKind,
        val items: List<DiagramItem>,
        val unit: String? = null,
        val maxValue: Float? = null,
        val showValues: Boolean = true,
    ) : LessonBlock

    /**
     * A real photo or illustration found online for this lesson.
     *
     * Sourced rather than generated, because a lesson's picture has to be ACCURATE — a generated
     * image of a knife grip or a circuit is confidently wrong in ways a learner can't catch.
     * [credit] and [sourceUrl] travel with it since the licences require attribution.
     */
    data class Image(
        override val text: String,
        val url: String,
        val sourceUrl: String,
        val credit: String,
    ) : LessonBlock
}

/** The diagram shapes the app knows how to draw. */
enum class DiagramKind {
    /** An ordered sequence: each item follows the one above it. */
    FLOW,

    /** Two or three things set against each other, side by side. */
    COMPARE,

    /** Labelled magnitudes drawn as proportional bars. */
    BAR,
}

/**
 * One entry in a [LessonBlock.Diagram].
 *
 * @property label the short name of this step / column / bar.
 * @property detail a sentence expanding on it; may be empty.
 * @property value magnitude for [DiagramKind.BAR], ignored by the other kinds.
 */
data class DiagramItem(
    val label: String,
    val detail: String = "",
    val value: Float = 0f,
)

/** Just the teaching prose, e.g. for checking a generated lesson actually taught something. */
fun List<LessonBlock>.paragraphs(): List<String> =
    filterIsInstance<LessonBlock.Paragraph>().map { it.text }

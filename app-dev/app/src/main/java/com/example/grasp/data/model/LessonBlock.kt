package com.example.grasp.data.model

import java.util.UUID

/**
 * One piece of a generated lesson body.
 *
 * The body is a FLAT list rather than nested sections on purpose: it keeps a block a thing that
 * can be pointed at, edited and replaced on its own, which is what scopes an inline AI chat to the
 * part of the lesson the user tapped (FR5.2) and what lets a user rewrite one paragraph without
 * touching the rest (FR4.5). Nesting would make every reference depend on the shape of the
 * content around it.
 */
sealed interface LessonBlock {

    /**
     * Opaque, stable handle for this block.
     *
     * Everything that points AT a block rather than copying it — the chat scoped to it, an edit
     * the user makes, an edit the AI proposes — travels by this id. So it is deliberately derived
     * from NEITHER the block's text nor its position: both change under editing, and an id that
     * moved with them would silently re-point at a different paragraph.
     */
    val id: String

    /** The text of this block: heading label, paragraph prose, or a visual's caption. */
    val text: String

    /**
     * A section title inside the lesson.
     *
     * @property level 1 for a section heading, 2 for a subheading within one.
     */
    data class Heading(
        override val text: String,
        val level: Int = 1,
        override val id: String = newBlockId(),
    ) : LessonBlock

    /** A paragraph of the lesson — the tappable unit the user can ask questions about. */
    data class Paragraph(
        override val text: String,
        override val id: String = newBlockId(),
    ) : LessonBlock

    /**
     * A short code or command sample, shown monospaced.
     *
     * Its own kind rather than a paragraph because code has to keep its line breaks and spacing —
     * for a topic like Python, indentation IS the syntax, and prose styling destroys it.
     *
     * @property language what it's written in, for the little corner label; may be empty.
     */
    data class Code(
        override val text: String,
        val language: String = "",
        override val id: String = newBlockId(),
    ) : LessonBlock

    /**
     * A diagram the app DRAWS from a spec the AI wrote (FR4.4).
     *
     * Deliberately a small vocabulary of shapes rather than free-form image generation: the app
     * renders it natively, so it matches the theme, stays sharp, costs no extra generation, and
     * — unlike a generated picture — its labels are guaranteed to say what the AI meant.
     *
     * @property text the caption shown under the drawing.
     */
    data class Diagram(
        override val text: String,
        val kind: DiagramKind,
        val items: List<DiagramItem>,
        override val id: String = newBlockId(),
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
        override val id: String = newBlockId(),
    ) : LessonBlock
}

/**
 * A fresh block id, for a block being created rather than read back.
 *
 * Long and random so it can never collide with the short positional ids handed to lessons that
 * were stored before blocks carried one.
 */
fun newBlockId(): String = "b" + UUID.randomUUID().toString().replace("-", "").take(12)

/**
 * The same block under [id].
 *
 * Only for stamping an id onto a block that arrived without a usable one — off the model, or out
 * of a lesson stored before ids existed. Re-identifying a block that already has one breaks every
 * reference to it.
 */
fun LessonBlock.withId(id: String): LessonBlock = when (this) {
    is LessonBlock.Heading -> copy(id = id)
    is LessonBlock.Paragraph -> copy(id = id)
    is LessonBlock.Code -> copy(id = id)
    is LessonBlock.Diagram -> copy(id = id)
    is LessonBlock.Image -> copy(id = id)
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

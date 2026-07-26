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

    /** The text of this block, whatever kind it is. */
    val text: String

    /**
     * A section title inside the lesson.
     *
     * @property level 1 for a section heading, 2 for a subheading within one.
     */
    data class Heading(override val text: String, val level: Int = 1) : LessonBlock

    /** A paragraph of the lesson — the tappable unit the user can ask questions about. */
    data class Paragraph(override val text: String) : LessonBlock
}

/** Just the teaching prose, e.g. for feeding the AI tutor the lesson without its scaffolding. */
fun List<LessonBlock>.paragraphs(): List<String> =
    filterIsInstance<LessonBlock.Paragraph>().map { it.text }

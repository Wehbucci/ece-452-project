package com.example.grasp.core.edit

import com.example.grasp.data.model.BlockSource
import com.example.grasp.data.model.LessonBlock
import com.example.grasp.data.model.ResourceLink
import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.model.withId
import com.example.grasp.data.model.withSource

/**
 * One change to a lesson, as a value.
 *
 * This is the single road every edit travels. The editing UI turns a user's gesture into one of
 * these (FR4.5), and the assistant proposes the very same objects for the user to accept or reject
 * (FR5.4) — so the two paths cannot drift apart, and the hard part (getting the change right) is
 * written once.
 *
 * They are DATA rather than method calls on purpose: an edit that is a value can be shown as a
 * before/after diff, held pending someone's approval, applied as a batch, rejected without a
 * trace, and undone. None of that is possible if making a change means calling a function.
 *
 * Every operation names the block it acts on by [LessonBlock.id], never by position, so a proposal
 * written against the lesson the assistant was shown still lands on the right paragraph after the
 * user has moved something else around.
 */
sealed interface LessonEdit {

    /**
     * Replaces the content of [blockId] with [block].
     *
     * [block] may be a different kind from the one it replaces — turning a paragraph into a code
     * sample is an ordinary edit — but it keeps the old block's id, because it is still the same
     * part of the lesson as far as anything pointing at it is concerned.
     */
    data class UpdateBlock(val blockId: String, val block: LessonBlock) : LessonEdit

    /** Puts [block] directly after [afterBlockId], or at the very top of the lesson when null. */
    data class InsertBlockAfter(val afterBlockId: String?, val block: LessonBlock) : LessonEdit

    /** Removes a block. */
    data class DeleteBlock(val blockId: String) : LessonEdit

    /** Moves [blockId] to sit directly after [afterBlockId], or to the top when null. */
    data class MoveBlock(val blockId: String, val afterBlockId: String?) : LessonEdit

    /**
     * Points an image block at a different picture.
     *
     * Its own operation rather than an [UpdateBlock] because the three fields travel together: a
     * licence requires the credit and the source page to match the file actually shown, and
     * letting them be set one at a time invites an image with someone else's attribution on it.
     */
    data class ReplaceImage(
        val blockId: String,
        val url: String,
        val sourceUrl: String,
        val credit: String,
    ) : LessonEdit

    /** Rewrites one of the lesson's own fields — the parts of it that aren't blocks. */
    data class UpdateField(val field: LessonField, val value: String) : LessonEdit

    /** Replaces the whole "dive deeper" list. */
    data class UpdateResources(val resources: List<ResourceLink>) : LessonEdit
}

/**
 * A lesson field that isn't a block.
 *
 * The title and the reading time are deliberately absent: both live on the roadmap node, so
 * changing them is a roadmap edit, not a lesson one.
 */
enum class LessonField { SUMMARY, WHY_IT_MATTERS }

/**
 * Who is making an edit.
 *
 * Only two things ever do, and each leaves a different mark on the blocks it touches — which is
 * why this is not just [BlockSource]: "generated, untouched" is a state a block can be in, never
 * something that can author a change.
 */
enum class EditAuthor(val credit: BlockSource) {
    USER(BlockSource.USER),
    ASSISTANT(BlockSource.AI_EDITED),
}

/**
 * The lesson with [edit] applied, or null if the edit doesn't fit this lesson.
 *
 * Null rather than silently returning the lesson unchanged: an edit naming a block that has since
 * been deleted, or an image change aimed at a paragraph, is a mistake somewhere upstream, and the
 * user who tapped Accept deserves to be told nothing happened rather than left believing it did.
 */
fun Subtopic.applyEdit(edit: LessonEdit, author: EditAuthor = EditAuthor.USER): Subtopic? {
    val edited = when (edit) {
        is LessonEdit.UpdateBlock -> {
            if (body.none { it.id == edit.blockId }) return null
            copy(
                body = body.map {
                    if (it.id == edit.blockId) edit.block.withId(edit.blockId).withSource(author.credit)
                    else it
                },
            )
        }

        is LessonEdit.InsertBlockAfter -> {
            // A duplicate id would leave two blocks that every later edit confuses for each other.
            if (body.any { it.id == edit.block.id }) return null
            val at = body.insertionIndex(edit.afterBlockId) ?: return null
            copy(
                body = body.toMutableList()
                    .apply { add(at, edit.block.withSource(author.credit)) },
            )
        }

        is LessonEdit.DeleteBlock -> {
            if (body.none { it.id == edit.blockId }) return null
            copy(body = body.filterNot { it.id == edit.blockId })
        }

        is LessonEdit.MoveBlock -> {
            val moving = body.firstOrNull { it.id == edit.blockId } ?: return null
            if (edit.afterBlockId == edit.blockId) return this.copy(edited = true)
            val without = body.filterNot { it.id == edit.blockId }
            val at = without.insertionIndex(edit.afterBlockId) ?: return null
            // Moving a block is not rewriting it, so its provenance is left as it was.
            copy(body = without.toMutableList().apply { add(at, moving) })
        }

        is LessonEdit.ReplaceImage -> {
            val image = body.firstOrNull { it.id == edit.blockId } as? LessonBlock.Image ?: return null
            copy(
                body = body.map {
                    if (it.id != edit.blockId) it
                    else image.copy(
                        url = edit.url,
                        sourceUrl = edit.sourceUrl,
                        credit = edit.credit,
                        source = author.credit,
                    )
                },
            )
        }

        is LessonEdit.UpdateField -> when (edit.field) {
            LessonField.SUMMARY -> copy(summary = edit.value)
            LessonField.WHY_IT_MATTERS -> copy(whyItMatters = edit.value)
        }

        is LessonEdit.UpdateResources -> copy(resources = edit.resources)
    }
    // Set once, here, so no operation can be added later that changes a lesson without marking it
    // — the mark is what stops the next generation pass writing over it.
    return edited.copy(edited = true)
}

/**
 * The lesson with every edit in [edits] applied in order, or null if any one of them doesn't fit.
 *
 * All or nothing, because a batch is how the assistant proposes a set of related changes: applying
 * three of five leaves the lesson in a state nobody asked for and nobody reviewed.
 */
fun Subtopic.applyEdits(
    edits: List<LessonEdit>,
    author: EditAuthor = EditAuthor.USER,
): Subtopic? = edits.fold(this as Subtopic?) { lesson, edit -> lesson?.applyEdit(edit, author) }

/** Where a block placed after [afterBlockId] goes; null if there is no such block to follow. */
private fun List<LessonBlock>.insertionIndex(afterBlockId: String?): Int? {
    if (afterBlockId == null) return 0
    val after = indexOfFirst { it.id == afterBlockId }
    return if (after < 0) null else after + 1
}

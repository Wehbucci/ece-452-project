package com.example.grasp.data.repository

import com.example.grasp.core.edit.EditAuthor
import com.example.grasp.core.edit.LessonEdit
import com.example.grasp.core.edit.LessonField
import com.example.grasp.data.model.LessonBlock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Undo and version history, exercised through [FakePathRepository] — which implements the same
 * semantics as the Firestore one without needing it.
 *
 * Every test uses its own path id: the fake is a singleton holding its edits in memory, so sharing
 * one would make these depend on the order they ran in.
 */
class LessonUndoTest {

    /** The fake is a singleton, so each test starts from the canned content, not the last one's. */
    @Before
    fun clearFakeEdits() = FakePathRepository.clearEdits()

    private val repo = FakePathRepository

    private fun rewrite(text: String) =
        LessonEdit.UpdateBlock("b1", LessonBlock.Paragraph(text))

    private fun textOf(pathId: String, nodeId: String) = runBlocking {
        repo.subtopic(pathId, nodeId)!!.body.first { it.id == "b1" }.text
    }

    @Test
    fun `undo puts back what was there before the edit`() = runBlocking {
        val path = "undo-one"
        val original = textOf(path, "n1")

        repo.editLesson(path, "n1", listOf(rewrite("My own words.")))
        assertEquals("My own words.", textOf(path, "n1"))

        val undone = repo.undoLastLessonEdit(path, "n1")!!
        assertEquals(original, undone.body.first { it.id == "b1" }.text)
        assertEquals(original, textOf(path, "n1"))
    }

    @Test
    fun `undo steps back further each time instead of bouncing`() = runBlocking {
        val path = "undo-twice"
        val original = textOf(path, "n1")

        repo.editLesson(path, "n1", listOf(rewrite("First go.")))
        repo.editLesson(path, "n1", listOf(rewrite("Second go.")))

        repo.undoLastLessonEdit(path, "n1")
        assertEquals("First go.", textOf(path, "n1"))
        repo.undoLastLessonEdit(path, "n1")
        assertEquals(original, textOf(path, "n1"))
        // Nothing left to take back.
        assertNull(repo.undoLastLessonEdit(path, "n1"))
    }

    @Test
    fun `history says what each change was and who made it`() = runBlocking {
        val path = "undo-labels"

        repo.editLesson(path, "n1", listOf(rewrite("Mine.")))
        repo.editLesson(
            path,
            "n1",
            listOf(LessonEdit.UpdateField(LessonField.SUMMARY, "Tighter.")),
            author = EditAuthor.ASSISTANT,
        )

        val history = repo.lessonRevisions(path, "n1")
        assertEquals(listOf("Rewrote the summary", "Rewrote a block"), history.map { it.label })
        // Newest first, and the AI's change is the one flagged.
        assertEquals(listOf(true, false), history.map { it.byAssistant })
    }

    @Test
    fun `going back to a named version keeps the work it replaced`() = runBlocking {
        val path = "undo-restore"
        val original = textOf(path, "n1")

        repo.editLesson(path, "n1", listOf(rewrite("First go.")))
        repo.editLesson(path, "n1", listOf(rewrite("Second go.")))
        val oldest = repo.lessonRevisions(path, "n1").last()

        repo.restoreLesson(path, "n1", oldest.id)

        assertEquals(original, textOf(path, "n1"))
        // "Second go." is still reachable — reverting isn't destroying.
        val history = repo.lessonRevisions(path, "n1")
        assertEquals(3, history.size)
        assertTrue(history.any { it.body.any { block -> block.text == "Second go." } })
    }

    @Test
    fun `a lesson nobody edited has no history and nothing to undo`() = runBlocking {
        assertEquals(emptyList<Any>(), repo.lessonRevisions("undo-clean", "n1"))
        assertNull(repo.undoLastLessonEdit("undo-clean", "n1"))
    }
}

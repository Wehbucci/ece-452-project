package com.example.grasp.core.edit

import com.example.grasp.data.model.LessonBlock
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The label a stored version carries.
 *
 * Tested because it is the ONLY thing a user scanning their history has to go on when deciding
 * which version to bring back — a wrong or vague label is a lost edit.
 */
class DescribeLessonEditsTest {

    private fun rewrite(id: String) = LessonEdit.UpdateBlock(id, LessonBlock.Paragraph("New words."))

    @Test
    fun `counts one change and several of the same kind`() {
        assertEquals("Rewrote a block", describeLessonEdits(listOf(rewrite("p1"))))
        assertEquals("Rewrote 3 blocks", describeLessonEdits(listOf(rewrite("p1"), rewrite("p2"), rewrite("p3"))))
        assertEquals(
            "Deleted 2 blocks",
            describeLessonEdits(listOf(LessonEdit.DeleteBlock("p1"), LessonEdit.DeleteBlock("p2"))),
        )
    }

    @Test
    fun `names the lesson field that changed`() {
        assertEquals(
            "Rewrote the summary",
            describeLessonEdits(listOf(LessonEdit.UpdateField(LessonField.SUMMARY, "Shorter."))),
        )
        assertEquals(
            "Rewrote why it matters",
            describeLessonEdits(listOf(LessonEdit.UpdateField(LessonField.WHY_IT_MATTERS, "Because."))),
        )
        assertEquals(
            "Changed the further reading",
            describeLessonEdits(listOf(LessonEdit.UpdateResources(emptyList()))),
        )
    }

    @Test
    fun `falls back to a count when the changes are mixed`() {
        // Naming two of three reads as if the third didn't happen.
        assertEquals(
            "3 changes",
            describeLessonEdits(
                listOf(
                    rewrite("p1"),
                    LessonEdit.DeleteBlock("p2"),
                    LessonEdit.UpdateField(LessonField.SUMMARY, "Shorter."),
                ),
            ),
        )
    }

    @Test
    fun `says so when nothing changed`() {
        assertEquals("No changes", describeLessonEdits(emptyList()))
    }
}

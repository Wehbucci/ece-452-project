package com.example.grasp.data.repository

import com.example.grasp.core.edit.LessonEdit
import com.example.grasp.core.edit.LessonField
import com.example.grasp.core.edit.applyEdit
import com.example.grasp.data.model.LessonBlock
import com.example.grasp.data.model.ResourceKind
import com.example.grasp.data.model.ResourceLink
import com.example.grasp.data.model.Subtopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a saved edit actually sends.
 *
 * Worth its own tests because the failure is invisible from the outside: a version of this that
 * returned every field would save correctly every time, and only show itself as one user's work
 * disappearing when two of them edited the same lesson.
 */
class ChangedLessonFieldsTest {

    private val lesson = Subtopic(
        nodeId = "knife-skills",
        title = "Knife skills",
        sectionLabel = "Section 1 of 4",
        summary = "How to hold and use a chef's knife.",
        whyItMatters = "Every recipe starts here.",
        body = listOf(
            LessonBlock.Heading("The grip", id = "h1"),
            LessonBlock.Paragraph("Pinch the blade.", id = "p1"),
        ),
        resources = listOf(ResourceLink("Wikipedia", "https://en.wikipedia.org", ResourceKind.ARTICLE)),
        estMinutes = 8,
    )

    @Test
    fun `a summary edit does not resend the lesson body`() {
        val after = lesson.applyEdit(LessonEdit.UpdateField(LessonField.SUMMARY, "Shorter."))!!

        assertEquals(setOf("summary"), changedLessonFields(lesson, after).keys)
    }

    @Test
    fun `a block edit sends the body and nothing else`() {
        val after = lesson.applyEdit(
            LessonEdit.UpdateBlock("p1", LessonBlock.Paragraph("Pinch it near the bolster.")),
        )!!

        val fields = changedLessonFields(lesson, after)
        assertEquals(setOf("body"), fields.keys)
        // The whole array goes: Firestore has no way to rewrite one entry of one in place.
        @Suppress("UNCHECKED_CAST")
        val body = fields["body"] as List<Map<String, Any>>
        assertEquals(listOf("h1", "p1"), body.map { it["id"] })
        assertEquals("Pinch it near the bolster.", body[1]["text"])
    }

    @Test
    fun `an edit that changed nothing sends nothing`() {
        // Applying it still marks the lesson edited — that flag is written separately, and is the
        // only thing this ought to cost.
        assertTrue(changedLessonFields(lesson, lesson.copy(edited = true)).isEmpty())
    }

    @Test
    fun `several fields at once are all included`() {
        val after = lesson
            .applyEdit(LessonEdit.UpdateField(LessonField.WHY_IT_MATTERS, "It is the first skill."))!!
            .applyEdit(LessonEdit.UpdateResources(emptyList()))!!
            .applyEdit(LessonEdit.DeleteBlock("h1"))!!

        assertEquals(setOf("whyItMatters", "resources", "body"), changedLessonFields(lesson, after).keys)
    }
}

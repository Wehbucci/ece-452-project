package com.example.grasp.core.edit

import com.example.grasp.data.model.BlockSource
import com.example.grasp.data.model.LessonBlock
import com.example.grasp.data.model.ResourceKind
import com.example.grasp.data.model.ResourceLink
import com.example.grasp.data.model.Subtopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The edit operations, which both the editing UI and the assistant's proposals run through — so
 * these are the tests standing behind FR4.5 and FR5.4 alike.
 */
class LessonEditTest {

    private val image = LessonBlock.Image(
        text = "A julienne cut",
        url = "https://upload.wikimedia.org/old.jpg",
        sourceUrl = "https://commons.wikimedia.org/wiki/File:Old.jpg",
        credit = "Someone · CC BY-SA 4.0",
        id = "img",
    )

    private val lesson = Subtopic(
        nodeId = "knife-skills",
        title = "Knife skills",
        sectionLabel = "Section 1 of 4",
        summary = "How to hold and use a chef's knife.",
        whyItMatters = "Every recipe starts here.",
        body = listOf(
            LessonBlock.Heading("The grip", id = "h1"),
            LessonBlock.Paragraph("Pinch the blade, don't choke the handle.", id = "p1"),
            image,
            LessonBlock.Paragraph("Keep your knuckles forward.", id = "p2"),
        ),
        resources = listOf(ResourceLink("Wikipedia", "https://en.wikipedia.org", ResourceKind.ARTICLE)),
        estMinutes = 8,
    )

    private fun Subtopic.ids() = body.map { it.id }

    private fun Subtopic.block(id: String) = body.first { it.id == id }

    @Test
    fun `updating a block keeps its id and credits whoever wrote it`() {
        val edited = lesson.applyEdit(
            LessonEdit.UpdateBlock("p1", LessonBlock.Paragraph("Pinch the blade near the bolster.")),
        )!!

        assertEquals(lesson.ids(), edited.ids())
        assertEquals("Pinch the blade near the bolster.", edited.block("p1").text)
        assertEquals(BlockSource.USER, edited.block("p1").source)
    }

    @Test
    fun `an edit by the assistant is marked as such`() {
        val edited = lesson.applyEdit(
            LessonEdit.UpdateBlock("p1", LessonBlock.Paragraph("Clearer wording.")),
            author = EditAuthor.ASSISTANT,
        )!!

        assertEquals(BlockSource.AI_EDITED, edited.block("p1").source)
    }

    @Test
    fun `a block may change kind without changing identity`() {
        val edited = lesson.applyEdit(
            LessonEdit.UpdateBlock("p1", LessonBlock.Code("chef_knife.grip()", "python")),
        )!!

        assertEquals(lesson.ids(), edited.ids())
        assertTrue(edited.block("p1") is LessonBlock.Code)
    }

    @Test
    fun `every edit marks the lesson as touched by a human`() {
        assertFalse(lesson.edited)

        // The mark is what stops the next generation pass writing over the user's work, so it has
        // to survive edits that leave no block behind to carry it.
        assertTrue(lesson.applyEdit(LessonEdit.DeleteBlock("p2"))!!.edited)
        assertTrue(lesson.applyEdit(LessonEdit.MoveBlock("p2", null))!!.edited)
        assertTrue(
            lesson.applyEdit(LessonEdit.UpdateField(LessonField.SUMMARY, "Shorter."))!!.edited,
        )
        assertTrue(lesson.applyEdit(LessonEdit.UpdateResources(emptyList()))!!.edited)
    }

    @Test
    fun `inserts a block where it was asked for`() {
        val note = LessonBlock.Paragraph("A note of my own.", id = "mine")

        assertEquals(
            listOf("h1", "p1", "mine", "img", "p2"),
            lesson.applyEdit(LessonEdit.InsertBlockAfter("p1", note))!!.ids(),
        )
        assertEquals(
            listOf("mine", "h1", "p1", "img", "p2"),
            lesson.applyEdit(LessonEdit.InsertBlockAfter(null, note))!!.ids(),
        )
    }

    @Test
    fun `refuses to insert a block whose id is already taken`() {
        // Two blocks sharing an id would make every later edit ambiguous.
        val clash = LessonBlock.Paragraph("Different words, same id.", id = "p1")

        assertNull(lesson.applyEdit(LessonEdit.InsertBlockAfter("h1", clash)))
    }

    @Test
    fun `moves a block without re-crediting it`() {
        val moved = lesson.applyEdit(LessonEdit.MoveBlock("p2", "h1"))!!

        assertEquals(listOf("h1", "p2", "p1", "img"), moved.ids())
        // Re-ordering isn't rewriting: the words are still the generator's.
        assertEquals(BlockSource.AI, moved.block("p2").source)
    }

    @Test
    fun `moving a block to the top and onto itself`() {
        assertEquals(listOf("p2", "h1", "p1", "img"), lesson.applyEdit(LessonEdit.MoveBlock("p2", null))!!.ids())
        assertEquals(lesson.ids(), lesson.applyEdit(LessonEdit.MoveBlock("p1", "p1"))!!.ids())
    }

    @Test
    fun `replaces a picture together with its attribution`() {
        val swapped = lesson.applyEdit(
            LessonEdit.ReplaceImage(
                blockId = "img",
                url = "https://upload.wikimedia.org/new.jpg",
                sourceUrl = "https://commons.wikimedia.org/wiki/File:New.jpg",
                credit = "Someone Else · CC BY 4.0",
            ),
        )!!

        val picture = swapped.block("img") as LessonBlock.Image
        assertEquals("https://upload.wikimedia.org/new.jpg", picture.url)
        assertEquals("https://commons.wikimedia.org/wiki/File:New.jpg", picture.sourceUrl)
        assertEquals("Someone Else · CC BY 4.0", picture.credit)
        // The caption is the lesson's own words about the picture, not part of the swap.
        assertEquals("A julienne cut", picture.text)
    }

    @Test
    fun `an edit that does not fit the lesson applies nothing`() {
        assertNull("no such block", lesson.applyEdit(LessonEdit.UpdateBlock("gone", LessonBlock.Paragraph("x"))))
        assertNull("no such block", lesson.applyEdit(LessonEdit.DeleteBlock("gone")))
        assertNull("nothing to follow", lesson.applyEdit(LessonEdit.MoveBlock("p1", "gone")))
        assertNull(
            "nothing to follow",
            lesson.applyEdit(LessonEdit.InsertBlockAfter("gone", LessonBlock.Paragraph("x"))),
        )
        assertNull(
            "a paragraph is not an image",
            lesson.applyEdit(LessonEdit.ReplaceImage("p1", "https://x/y.jpg", "https://x", "Someone")),
        )
    }

    @Test
    fun `applies a batch in order`() {
        val batch = lesson.applyEdits(
            listOf(
                LessonEdit.DeleteBlock("img"),
                LessonEdit.InsertBlockAfter("p1", LessonBlock.Paragraph("Replacing the picture.", id = "p3")),
                LessonEdit.UpdateField(LessonField.SUMMARY, "Holding a chef's knife."),
            ),
        )!!

        assertEquals(listOf("h1", "p1", "p3", "p2"), batch.ids())
        assertEquals("Holding a chef's knife.", batch.summary)
    }

    @Test
    fun `a batch with one bad edit changes nothing at all`() {
        // Half of a proposal is a state nobody reviewed and nobody asked for.
        val batch = lesson.applyEdits(
            listOf(
                LessonEdit.UpdateBlock("p1", LessonBlock.Paragraph("This one is fine.")),
                LessonEdit.DeleteBlock("never-existed"),
            ),
            author = EditAuthor.ASSISTANT,
        )

        assertNull(batch)
    }
}

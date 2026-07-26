package com.example.grasp.data.repository

import com.example.grasp.data.model.LessonBlock
import com.example.grasp.data.model.paragraphs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JUnit tests for reading a lesson body.
 *
 * The shape matters in two directions: it comes off the model's answer AND back out of Firestore,
 * where lessons written before headings existed are still stored as bare strings.
 */
class LessonBlockTest {

    private fun heading(text: String, level: Int = 1) =
        mapOf("type" to "heading", "text" to text, "level" to level)

    private fun paragraph(text: String) = mapOf("type" to "paragraph", "text" to text)

    @Test
    fun `reads headings and paragraphs in order`() {
        val blocks = lessonBlocks(
            listOf(heading("What it does"), paragraph("First."), paragraph("Second.")),
        )

        assertEquals(
            listOf(
                LessonBlock.Heading("What it does", 1),
                LessonBlock.Paragraph("First."),
                LessonBlock.Paragraph("Second."),
            ),
            blocks,
        )
    }

    @Test
    fun `reads a lesson saved before headings existed`() {
        val blocks = lessonBlocks(listOf("First.", "Second."))

        assertEquals(listOf(LessonBlock.Paragraph("First."), LessonBlock.Paragraph("Second.")), blocks)
    }

    @Test
    fun `treats an unlabelled block as a paragraph`() {
        val blocks = lessonBlocks(listOf(mapOf("text" to "No type given.")))

        assertEquals(listOf(LessonBlock.Paragraph("No type given.")), blocks)
    }

    @Test
    fun `flattens heading levels past a subheading`() {
        val blocks = lessonBlocks(listOf(heading("Too deep", level = 4), heading("Fine", level = 2)))

        assertEquals(listOf(LessonBlock.Heading("Too deep", 2), LessonBlock.Heading("Fine", 2)), blocks)
    }

    @Test
    fun `drops empty and unusable entries`() {
        val blocks = lessonBlocks(listOf(paragraph("  "), heading(""), 42, null, paragraph("Kept.")))

        assertEquals(listOf(LessonBlock.Paragraph("Kept.")), blocks)
    }

    @Test
    fun `round-trips through the stored shape`() {
        val original = listOf(
            LessonBlock.Heading("Section", 1),
            LessonBlock.Heading("Part", 2),
            LessonBlock.Paragraph("Body."),
        )

        assertEquals(original, lessonBlocks(original.map { it.toMap() }))
    }

    @Test
    fun `paragraphs helper skips the scaffolding`() {
        val blocks = listOf(
            LessonBlock.Heading("Section", 1),
            LessonBlock.Paragraph("Teaching."),
        )

        assertEquals(listOf("Teaching."), blocks.paragraphs())
        // A body of nothing but headings has no lesson in it — the generator rejects that.
        assertTrue(listOf(LessonBlock.Heading("Alone", 1)).paragraphs().isEmpty())
    }
}

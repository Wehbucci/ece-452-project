package com.example.grasp.data.repository

import com.example.grasp.data.model.BlockSource
import com.example.grasp.data.model.DiagramItem
import com.example.grasp.data.model.DiagramKind
import com.example.grasp.data.model.LessonBlock
import com.example.grasp.data.model.newBlockId
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
                LessonBlock.Heading("What it does", 1, id = "b0"),
                LessonBlock.Paragraph("First.", id = "b1"),
                LessonBlock.Paragraph("Second.", id = "b2"),
            ),
            blocks,
        )
    }

    @Test
    fun `reads a lesson saved before headings existed`() {
        val blocks = lessonBlocks(listOf("First.", "Second."))

        assertEquals(
            listOf(LessonBlock.Paragraph("First.", id = "b0"), LessonBlock.Paragraph("Second.", id = "b1")),
            blocks,
        )
    }

    @Test
    fun `treats an unlabelled block as a paragraph`() {
        val blocks = lessonBlocks(listOf(mapOf("text" to "No type given.")))

        assertEquals(listOf(LessonBlock.Paragraph("No type given.", id = "b0")), blocks)
    }

    @Test
    fun `flattens heading levels past a subheading`() {
        val blocks = lessonBlocks(listOf(heading("Too deep", level = 4), heading("Fine", level = 2)))

        assertEquals(
            listOf(LessonBlock.Heading("Too deep", 2, id = "b0"), LessonBlock.Heading("Fine", 2, id = "b1")),
            blocks,
        )
    }

    @Test
    fun `drops empty and unusable entries`() {
        val blocks = lessonBlocks(listOf(paragraph("  "), heading(""), 42, null, paragraph("Kept.")))

        // Its id is its position in what was STORED, gaps and all — anything already pointing at
        // the surviving block still points at it.
        assertEquals(listOf(LessonBlock.Paragraph("Kept.", id = "b4")), blocks)
    }

    @Test
    fun `keeps a stored block id rather than re-deriving one`() {
        val blocks = lessonBlocks(
            listOf(
                paragraph("Moved down here.") + ("id" to "b7a1c93f0e42"),
                paragraph("No id of its own."),
            ),
        )

        assertEquals(
            listOf(
                LessonBlock.Paragraph("Moved down here.", id = "b7a1c93f0e42"),
                LessonBlock.Paragraph("No id of its own.", id = "b1"),
            ),
            blocks,
        )
    }

    @Test
    fun `an edited block keeps its id`() {
        val original = LessonBlock.Paragraph("Before the user rewrote it.")
        val edited = original.copy(text = "After.")

        // The whole point of the id: a chat or a proposed edit pointing at this paragraph still
        // finds it once its text has changed.
        assertEquals(original.id, edited.id)
        assertEquals(listOf(edited), lessonBlocks(listOf(edited.toMap())))
    }

    @Test
    fun `remembers who wrote a block`() {
        val rewritten = LessonBlock.Paragraph("The user's own words.", source = BlockSource.USER)

        assertEquals(listOf(rewritten), lessonBlocks(listOf(rewritten.toMap())))
    }

    @Test
    fun `a block with no stored source counts as generated`() {
        // Every lesson written before provenance existed came out of the generator, and a label
        // we don't recognise is not evidence a human wrote it.
        val legacy = lessonBlocks(listOf(paragraph("From before."))).single()
        val nonsense = lessonBlocks(listOf(paragraph("Odd.") + ("source" to "wizard"))).single()

        assertEquals(BlockSource.AI, legacy.source)
        assertEquals(BlockSource.AI, nonsense.source)
    }

    @Test
    fun `fresh ids are unique and never look like positional ones`() {
        val ids = List(200) { newBlockId() }

        assertEquals(200, ids.toSet().size)
        assertTrue("must not collide with a stored lesson's b0, b1, ...", ids.none { it.length < 8 })
    }

    /** Ids included: they are what a stored lesson is edited and chatted about by. */
    @Test
    fun `round-trips every block kind through the stored shape`() {
        val original = listOf(
            LessonBlock.Heading("Section", 1),
            LessonBlock.Heading("Part", 2),
            LessonBlock.Paragraph("Body."),
            LessonBlock.Diagram(
                text = "How it flows",
                kind = DiagramKind.FLOW,
                items = listOf(DiagramItem("In", "goes in", 0f), DiagramItem("Out", "comes out", 0f)),
            ),
            LessonBlock.Image("Caption", "https://x/y.jpg", "https://x/page", "Someone · CC BY"),
        )

        assertEquals(original, lessonBlocks(original.map { it.toMap() }))
    }

    @Test
    fun `reads a diagram with its items`() {
        val blocks = lessonBlocks(
            listOf(
                mapOf(
                    "type" to "diagram",
                    "kind" to "bar",
                    "text" to "Relative cost",
                    "items" to listOf(
                        mapOf("label" to "Training", "detail" to "One-off", "value" to 8),
                        mapOf("label" to "Inference", "value" to 2.5),
                    ),
                ),
            ),
        )

        val diagram = blocks.single() as LessonBlock.Diagram
        assertEquals(DiagramKind.BAR, diagram.kind)
        assertEquals("Relative cost", diagram.text)
        assertEquals(
            listOf(DiagramItem("Training", "One-off", 8f), DiagramItem("Inference", "", 2.5f)),
            diagram.items,
        )
    }

    @Test
    fun `drops a diagram the app could not draw`() {
        val oneItem = mapOf(
            "type" to "diagram", "kind" to "flow", "text" to "",
            "items" to listOf(mapOf("label" to "Alone")),
        )
        val unknownKind = mapOf(
            "type" to "diagram", "kind" to "sankey", "text" to "",
            "items" to listOf(mapOf("label" to "A"), mapOf("label" to "B")),
        )

        assertTrue("a single item is not a diagram", lessonBlocks(listOf(oneItem)).isEmpty())
        assertTrue("no renderer for this shape", lessonBlocks(listOf(unknownKind)).isEmpty())
    }

    @Test
    fun `reads an image but not one without a real url`() {
        val found = mapOf(
            "type" to "image",
            "text" to "A julienne cut",
            "url" to "https://upload.wikimedia.org/pepper.jpg",
            "sourceUrl" to "https://commons.wikimedia.org/wiki/File:Pepper.jpg",
            "credit" to "Someone · CC BY-SA 4.0",
        )
        // A model that ignored the instructions and wrote a search query instead of a URL.
        val unresolved = mapOf("type" to "image", "text" to "A julienne cut", "query" to "pepper")

        assertEquals(
            LessonBlock.Image(
                text = "A julienne cut",
                url = "https://upload.wikimedia.org/pepper.jpg",
                sourceUrl = "https://commons.wikimedia.org/wiki/File:Pepper.jpg",
                credit = "Someone · CC BY-SA 4.0",
                id = "b0",
            ),
            lessonBlocks(listOf(found)).single(),
        )
        assertTrue(lessonBlocks(listOf(unresolved)).isEmpty())
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

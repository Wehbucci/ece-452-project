package com.example.grasp.core.edit

import com.example.grasp.data.model.BlockSource
import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.LessonBlock
import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.model.TreeNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the tutor is allowed to propose, and what happens to the rest (FR5.4).
 *
 * This is where the guardrails live, so this is where they are held to: the model is an
 * untrusted input, and every test below is a shape of output a real one has been known to
 * produce — an id it made up, a number written as a word, a batch of forty rewrites, a tool it
 * was never given.
 */
class EditProposalTest {

    private fun lesson() = Subtopic(
        nodeId = "n1",
        title = "Knife Skills",
        sectionLabel = "Section 2 of 6",
        summary = "How to hold and use a knife.",
        whyItMatters = "Everything else in the kitchen goes faster.",
        body = listOf(
            LessonBlock.Heading("The grip", id = "b0"),
            LessonBlock.Paragraph("Pinch the blade between thumb and forefinger.", id = "b1"),
            LessonBlock.Code("chop(onion)", language = "python", id = "b2"),
            LessonBlock.Paragraph("My own note about onions.", id = "b3", source = BlockSource.USER),
        ),
        resources = emptyList(),
        estMinutes = 12,
    )

    private fun roadmap() = LearningPath(
        id = "cooking",
        title = "Cooking",
        nodes = listOf(
            TreeNode("root", "Kitchen Basics", estMinutes = 8, children = listOf("knife")),
            TreeNode("knife", "Knife Skills", estMinutes = 12, parentId = "root", children = listOf("heat")),
            TreeNode("heat", "Heat Control", estMinutes = 10, parentId = "knife"),
        ),
    )

    private fun call(name: String, vararg args: Pair<String, String>, id: String = "c0") =
        ToolCall(id, name, args.toMap())

    // ---- Rewriting ----------------------------------------------------------------------------

    @Test
    fun `rewriting a block becomes an update carrying the old and new words`() {
        val proposal = proposeLessonEdits(
            lesson(),
            listOf(call(TutorTool.REWRITE_BLOCK, "block_id" to "b1", "text" to "Pinch the blade.")),
        )

        val change = proposal.changes.single()
        assertEquals("Pinch the blade between thumb and forefinger.", change.before)
        assertEquals("Pinch the blade.", change.after)
        val edit = (change.target as EditTarget.Lesson).edit as LessonEdit.UpdateBlock
        assertEquals("b1", edit.blockId)
    }

    @Test
    fun `a rewrite keeps the kind of block it is rewriting`() {
        val proposal = proposeLessonEdits(
            lesson(),
            listOf(call(TutorTool.REWRITE_BLOCK, "block_id" to "b2", "text" to "slice(onion)")),
        )

        val edit = (proposal.changes.single().target as EditTarget.Lesson).edit as LessonEdit.UpdateBlock
        val block = edit.block
        // Turning a code sample into prose while "fixing" it would destroy its formatting.
        assertTrue("a rewritten code block must still be code", block is LessonBlock.Code)
        assertEquals("python", (block as LessonBlock.Code).language)
    }

    @Test
    fun `rewriting the user's own words is offered, but flagged`() {
        val proposal = proposeLessonEdits(
            lesson(),
            listOf(call(TutorTool.REWRITE_BLOCK, "block_id" to "b3", "text" to "Onions, revised.")),
        )

        assertTrue(proposal.changes.single().overwritesUserWork)
        assertTrue(proposal.overwritesUserWork)
    }

    @Test
    fun `rewriting the AI's own words is not flagged`() {
        val proposal = proposeLessonEdits(
            lesson(),
            listOf(call(TutorTool.REWRITE_BLOCK, "block_id" to "b1", "text" to "Hold it firmly.")),
        )

        assertFalse(proposal.changes.single().overwritesUserWork)
    }

    // ---- Refusals -----------------------------------------------------------------------------

    @Test
    fun `an invented block id is refused, and said out loud`() {
        val proposal = proposeLessonEdits(
            lesson(),
            listOf(call(TutorTool.REWRITE_BLOCK, "block_id" to "b99", "text" to "Anything.")),
        )

        assertTrue(proposal.changes.isEmpty())
        assertEquals(1, proposal.declined.size)
    }

    @Test
    fun `an empty rewrite is refused`() {
        val proposal = proposeLessonEdits(
            lesson(),
            listOf(call(TutorTool.REWRITE_BLOCK, "block_id" to "b1", "text" to "   ")),
        )

        assertTrue(proposal.changes.isEmpty())
        assertEquals(1, proposal.declined.size)
    }

    @Test
    fun `a runaway wall of text is refused`() {
        val proposal = proposeLessonEdits(
            lesson(),
            listOf(call(TutorTool.REWRITE_BLOCK, "block_id" to "b1", "text" to "x".repeat(9_000))),
        )

        assertTrue(proposal.changes.isEmpty())
    }

    @Test
    fun `a tool the tutor was never given is refused`() {
        val proposal = proposeLessonEdits(lesson(), listOf(call("delete_everything")))

        assertTrue(proposal.changes.isEmpty())
        assertEquals(1, proposal.declined.size)
    }

    @Test
    fun `marking a subtopic complete is not something the tutor can even ask for`() {
        // The proposal's mitigation for its own unintended harm: progress is the user's to record,
        // so this is not gated behind approval — there is no tool for it at all, and a model that
        // guesses at the name gets the same nothing as a model that invents one.
        val proposal = proposeLessonEdits(
            lesson(),
            listOf(call("update_node_completion", "node_id" to "n1", "completed" to "true")),
        )

        assertTrue(proposal.changes.isEmpty())
    }

    @Test
    fun `a batch bigger than the cap is cut down and the rest reported`() {
        val calls = (0..9).map {
            call(TutorTool.REWRITE_BLOCK, "block_id" to "b1", "text" to "Take $it.", id = "c$it")
        }

        val proposal = proposeLessonEdits(lesson(), calls)

        assertEquals(MAX_CHANGES_PER_PROPOSAL, proposal.changes.size)
        assertEquals(1, proposal.declined.size)
        assertTrue(proposal.declined.single().contains("10 changes"))
    }

    // ---- Adding, deleting, moving ---------------------------------------------------------------

    @Test
    fun `adding a block with no neighbour puts it at the top`() {
        val proposal = proposeLessonEdits(
            lesson(),
            listOf(call(TutorTool.ADD_BLOCK, "kind" to "paragraph", "text" to "A new opening.")),
        )

        val edit = (proposal.changes.single().target as EditTarget.Lesson).edit
        assertEquals(null, (edit as LessonEdit.InsertBlockAfter).afterBlockId)
    }

    @Test
    fun `adding a block after something that isn't there is refused`() {
        val proposal = proposeLessonEdits(
            lesson(),
            listOf(
                call(
                    TutorTool.ADD_BLOCK,
                    "kind" to "paragraph",
                    "text" to "Orphan.",
                    "after_block_id" to "b99",
                ),
            ),
        )

        assertTrue(proposal.changes.isEmpty())
    }

    @Test
    fun `a picture cannot be added, because its credit could not be honest`() {
        val proposal = proposeLessonEdits(
            lesson(),
            listOf(call(TutorTool.ADD_BLOCK, "kind" to "image", "text" to "A knife")),
        )

        assertTrue(proposal.changes.isEmpty())
    }

    @Test
    fun `deleting a block shows what would go and nothing in its place`() {
        val proposal = proposeLessonEdits(
            lesson(),
            listOf(call(TutorTool.DELETE_BLOCK, "block_id" to "b1")),
        )

        val change = proposal.changes.single()
        assertTrue(change.before.isNotBlank())
        assertEquals("", change.after)
    }

    @Test
    fun `moving a block describes where it goes rather than its words`() {
        val proposal = proposeLessonEdits(
            lesson(),
            listOf(call(TutorTool.MOVE_BLOCK, "block_id" to "b1", "after_block_id" to "b2")),
        )

        val change = proposal.changes.single()
        // Showing the same paragraph twice would read as a change that does nothing.
        assertFalse(change.before == change.after)
        assertTrue(change.after.contains("chop(onion)"))
    }

    // ---- The batch as a whole -------------------------------------------------------------------

    @Test
    fun `later calls in a batch see what the earlier ones did`() {
        val proposal = proposeLessonEdits(
            lesson(),
            listOf(
                call(TutorTool.DELETE_BLOCK, "block_id" to "b1", id = "c0"),
                call(TutorTool.REWRITE_BLOCK, "block_id" to "b1", "text" to "Too late.", id = "c1"),
            ),
        )

        // The second names a block the first removed, which no longer exists by the time it runs.
        assertEquals(1, proposal.changes.size)
        assertEquals(1, proposal.declined.size)
    }

    @Test
    fun `everything offered applies as a batch`() {
        val proposal = proposeLessonEdits(
            lesson(),
            listOf(
                call(TutorTool.REWRITE_BLOCK, "block_id" to "b1", "text" to "Held firmly.", id = "c0"),
                call(TutorTool.DELETE_BLOCK, "block_id" to "b2", id = "c1"),
                call(TutorTool.MOVE_BLOCK, "block_id" to "b3", id = "c2"),
            ),
        )

        assertEquals(3, proposal.changes.size)
        // The promise the cards make: accepting cannot half-work.
        assertNotNull(lesson().applyEdits(proposal.lessonEdits(), EditAuthor.ASSISTANT))
    }

    // ---- Roadmap --------------------------------------------------------------------------------

    @Test
    fun `renaming a section becomes a rename`() {
        val proposal = proposeRoadmapEdits(
            roadmap(),
            listOf(call(TutorTool.RENAME_SECTION, "section_id" to "knife", "title" to "Cutting")),
        )

        val edit = (proposal.changes.single().target as EditTarget.Roadmap).edit
        assertEquals("Cutting", (edit as RoadmapEdit.RenameNode).title)
    }

    @Test
    fun `a time written as a decimal is understood, one written as a word is not`() {
        val ok = proposeRoadmapEdits(
            roadmap(),
            listOf(call(TutorTool.RETIME_SECTION, "section_id" to "knife", "minutes" to "45.0")),
        )
        val nonsense = proposeRoadmapEdits(
            roadmap(),
            listOf(call(TutorTool.RETIME_SECTION, "section_id" to "knife", "minutes" to "twenty")),
        )

        val edit = (ok.changes.single().target as EditTarget.Roadmap).edit
        assertEquals(45, (edit as RoadmapEdit.RetimeNode).estMinutes)
        assertTrue(nonsense.changes.isEmpty())
    }

    @Test
    fun `an absurd estimate is refused rather than quietly clamped`() {
        val proposal = proposeRoadmapEdits(
            roadmap(),
            listOf(call(TutorTool.RETIME_SECTION, "section_id" to "knife", "minutes" to "1440")),
        )

        assertTrue(proposal.changes.isEmpty())
    }

    @Test
    fun `a new section arrives with no lesson written yet`() {
        val proposal = proposeRoadmapEdits(
            roadmap(),
            listOf(call(TutorTool.ADD_SECTION, "parent_id" to "knife", "title" to "Sharpening")),
        )

        val edit = (proposal.changes.single().target as EditTarget.Roadmap).edit as RoadmapEdit.AddNode
        assertEquals("knife", edit.parentId)
        assertFalse("its lesson is generated on first open", edit.node.contentReady)
    }

    @Test
    fun `the root cannot be deleted, because the root is the roadmap`() {
        val proposal = proposeRoadmapEdits(
            roadmap(),
            listOf(call(TutorTool.DELETE_SECTION, "section_id" to "root")),
        )

        assertTrue(proposal.changes.isEmpty())
        assertEquals(1, proposal.declined.size)
    }

    @Test
    fun `deleting a section is always treated as touching the user's work`() {
        val proposal = proposeRoadmapEdits(
            roadmap(),
            listOf(call(TutorTool.DELETE_SECTION, "section_id" to "heat")),
        )

        assertTrue(proposal.changes.single().overwritesUserWork)
    }

    @Test
    fun `a section cannot be moved under itself`() {
        val proposal = proposeRoadmapEdits(
            roadmap(),
            listOf(call(TutorTool.MOVE_SECTION, "section_id" to "knife", "new_parent_id" to "knife")),
        )

        assertTrue(proposal.changes.isEmpty())
    }

    @Test
    fun `a turn with no tool calls in it proposes nothing`() {
        assertTrue(proposeLessonEdits(lesson(), emptyList()).isEmpty)
    }
}

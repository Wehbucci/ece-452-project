package com.example.grasp.core.edit

import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.TreeNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The roadmap edit operations.
 *
 * These assert the SHAPE that survives an edit as much as the edit itself: the board re-derives
 * every position from `children` on each render, so a malformed tree here is a roadmap with
 * sections missing from the screen, not a slightly untidy one.
 */
class RoadmapEditTest {

    /**
     *  cooking ─┬─ knives ─── sharpening
     *           └─ heat
     */
    private val path = LearningPath(
        id = "cooking",
        title = "Cooking",
        nodes = listOf(
            TreeNode("cooking", "Cooking basics", children = listOf("knives", "heat")),
            TreeNode("knives", "Knife skills", children = listOf("sharpening"), parentId = "cooking"),
            TreeNode("sharpening", "Sharpening", parentId = "knives"),
            TreeNode("heat", "Heat control", parentId = "cooking"),
        ),
    )

    private fun LearningPath.ids() = nodes.map { it.id }

    private fun LearningPath.node(id: String) = nodes.first { it.id == id }

    private fun LearningPath.parentOf(id: String) = node(id).parentId

    @Test
    fun `renames retimes and retiers a node`() {
        assertEquals(
            "Knife handling",
            path.applyEdit(RoadmapEdit.RenameNode("knives", "  Knife handling  "))!!.node("knives").title,
        )
        assertEquals(20, path.applyEdit(RoadmapEdit.RetimeNode("knives", 20))!!.node("knives").estMinutes)
        assertEquals(
            "FOUNDATIONS",
            path.applyEdit(RoadmapEdit.RetierNode("knives", "FOUNDATIONS"))!!.node("knives").tier,
        )
        assertNull(path.applyEdit(RoadmapEdit.RetierNode("knives", "  "))!!.node("knives").tier)
    }

    @Test
    fun `refuses a rename that would leave a node unlabelled`() {
        assertNull(path.applyEdit(RoadmapEdit.RenameNode("knives", "   ")))
    }

    @Test
    fun `adds a node under its parent and in reading order`() {
        val grown = path.applyEdit(RoadmapEdit.AddNode("knives", TreeNode("dicing", "Dicing")))!!

        // Depth-first: the new node sits inside the knives strand, not tacked onto the end.
        assertEquals(listOf("cooking", "knives", "sharpening", "dicing", "heat"), grown.ids())
        assertEquals("knives", grown.parentOf("dicing"))
        assertEquals(listOf("sharpening", "dicing"), grown.node("knives").children)
    }

    @Test
    fun `refuses to add a node that would collide or hang from nothing`() {
        assertNull(path.applyEdit(RoadmapEdit.AddNode("knives", TreeNode("heat", "Duplicate id"))))
        assertNull(path.applyEdit(RoadmapEdit.AddNode("nowhere", TreeNode("dicing", "Dicing"))))
    }

    @Test
    fun `deleting a node keeps what grew beyond it`() {
        val trimmed = path.applyEdit(RoadmapEdit.DeleteNode("knives"))!!

        assertEquals(listOf("cooking", "sharpening", "heat"), trimmed.ids())
        // Sharpening moves up to where its parent stood rather than disappearing with it.
        assertEquals("cooking", trimmed.parentOf("sharpening"))
        assertEquals(listOf("sharpening", "heat"), trimmed.node("cooking").children)
    }

    @Test
    fun `deleting with descendants takes the whole branch`() {
        val trimmed = path.applyEdit(RoadmapEdit.DeleteNode("knives", withDescendants = true))!!

        assertEquals(listOf("cooking", "heat"), trimmed.ids())
        assertEquals(listOf("heat"), trimmed.node("cooking").children)
    }

    @Test
    fun `refuses to delete the root`() {
        // It isn't a section of the path, it IS the path.
        assertNull(path.applyEdit(RoadmapEdit.DeleteNode("cooking")))
        assertNull(path.applyEdit(RoadmapEdit.DeleteNode("cooking", withDescendants = true)))
    }

    @Test
    fun `reparenting moves a node and everything below it`() {
        val moved = path.applyEdit(RoadmapEdit.ReparentNode("knives", "heat"))!!

        assertEquals(listOf("cooking", "heat", "knives", "sharpening"), moved.ids())
        assertEquals("heat", moved.parentOf("knives"))
        assertEquals("knives", moved.parentOf("sharpening"))
        assertEquals(listOf("heat"), moved.node("cooking").children)
    }

    @Test
    fun `a node can move down its own branch, leaving that branch behind`() {
        // The ordinary "this should come later" move on a roadmap that is mostly one chain.
        val moved = path.applyEdit(RoadmapEdit.ReparentNode("knives", "sharpening"))!!

        assertEquals("sharpening", moved.parentOf("knives"))
        // Its branch couldn't come with it, so it closed up into the place knives left.
        assertEquals("cooking", moved.parentOf("sharpening"))
        assertEquals(listOf("sharpening", "heat"), moved.node("cooking").children)
        assertEquals(listOf("knives"), moved.node("sharpening").children)
        // Still one tree, with nothing dropped on the floor.
        assertEquals(listOf("cooking", "sharpening", "knives", "heat"), moved.ids())
    }

    @Test
    fun `refuses a reparent that isn't a move at all`() {
        assertNull("nowhere to hang the root from", path.applyEdit(RoadmapEdit.ReparentNode("cooking", "heat")))
        assertNull("under itself", path.applyEdit(RoadmapEdit.ReparentNode("knives", "knives")))
        assertNull("no such destination", path.applyEdit(RoadmapEdit.ReparentNode("knives", "nowhere")))
        assertNull("no such node", path.applyEdit(RoadmapEdit.ReparentNode("nowhere", "heat")))
    }

    @Test
    fun `reparenting a node onto the parent it already has changes nothing`() {
        assertSame(path, path.applyEdit(RoadmapEdit.ReparentNode("knives", "cooking")))
    }

    @Test
    fun `applies a batch and refuses one with a bad edit in it`() {
        val reshaped = path.applyEdits(
            listOf(
                RoadmapEdit.AddNode("cooking", TreeNode("seasoning", "Seasoning")),
                RoadmapEdit.ReparentNode("heat", "seasoning"),
                RoadmapEdit.RenameNode("seasoning", "Salt and acid"),
            ),
        )!!

        assertEquals(listOf("cooking", "knives", "sharpening", "seasoning", "heat"), reshaped.ids())
        assertEquals("seasoning", reshaped.parentOf("heat"))
        assertEquals("Salt and acid", reshaped.node("seasoning").title)

        assertNull(
            path.applyEdits(
                listOf(RoadmapEdit.RenameNode("knives", "Fine"), RoadmapEdit.DeleteNode("nowhere")),
            ),
        )
    }
}

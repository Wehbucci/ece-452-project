package com.example.grasp.data.repository

import com.example.grasp.data.model.TreeNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Plain-JUnit tests for the roadmap's STRUCTURE — [normalizeTree] and the branch-id helpers.
 *
 * These deliberately don't touch Gemini: what the model writes is non-deterministic and changes
 * with the prompt, but what the app does with that answer must not. [normalizeTree] is the
 * guarantee the presenter's row/lock/connector logic is built on, so it's pinned here.
 */
class LearnerTreeGeneratorTest {

    private fun node(id: String, vararg children: String, estMinutes: Int = 0) =
        GeneratedNode(id, id.replace('-', ' ').replaceFirstChar { it.uppercase() }, children.toList(), estMinutes)

    private fun List<TreeNode>.byId(id: String) = firstOrNull { it.id == id }

    @Test
    fun `renames the first node to the path id and rewrites references to it`() {
        val tree = normalizeTree(
            "cooking-101",
            listOf(node("intro", "knife-skills"), node("knife-skills")),
        )

        assertEquals("cooking-101", tree.first().id)
        assertNull("the model's own root id is gone", tree.byId("intro"))
        assertEquals(listOf("knife-skills"), tree.byId("cooking-101")!!.children)
        assertEquals("cooking-101", tree.byId("knife-skills")!!.parentId)
    }

    @Test
    fun `always ends in a branch-out affordance hanging off the deepest leaf`() {
        val tree = normalizeTree(
            "root",
            listOf(
                node("root", "a", "detour"),
                node("a", "b"),
                node("b"),
                node("detour"), // shallower leaf — should NOT get the affordance
            ),
        )

        val branch = tree.single { it.isBranchOut }
        assertEquals(listOf(branch.id), tree.byId("b")!!.children)
        assertEquals("b", branch.parentId)
        assertTrue("the affordance is not a lesson", tree.byId("detour")!!.children.isEmpty())
    }

    @Test
    fun `gives the branch-out node a free id when the model already used that id`() {
        val tree = normalizeTree("root", listOf(node("root", BRANCH_OUT_ID), node(BRANCH_OUT_ID)))

        val branch = tree.single { it.isBranchOut }
        assertEquals("$BRANCH_OUT_ID-2", branch.id)
    }

    @Test
    fun `drops child references that dangle, repeat, self-reference or steal another parent`() {
        val tree = normalizeTree(
            "root",
            listOf(
                node("root", "a", "a", "ghost", "root"), // duplicate, unknown, and back to the root
                node("a", "shared", "a"),                 // self-reference
                node("shared"),
                node("thief", "shared"),                  // "shared" is already a's child
            ),
        )

        assertEquals(listOf("a"), tree.byId("root")!!.children)
        assertEquals("shared", tree.byId("a")!!.children.single())
        assertNull("unreachable nodes are discarded", tree.byId("thief"))
        assertNull(tree.byId("ghost"))
    }

    @Test
    fun `orders nodes depth-first so the main line comes before a detour`() {
        val tree = normalizeTree(
            "root",
            listOf(
                node("root", "main-1", "detour"),
                node("detour"),
                node("main-1", "main-2"),
                node("main-2"),
            ),
        )

        // The presenter picks the "current" node by list order, so the spine must win ties.
        assertEquals(
            listOf("root", "main-1", "main-2", "detour"),
            tree.filterNot { it.isBranchOut }.map { it.id },
        )
    }

    @Test
    fun `keeps the spine centered and pushes a detour to one side`() {
        val tree = normalizeTree(
            "root",
            listOf(node("root", "main", "detour"), node("main"), node("detour")),
        )

        assertEquals(TreeNode.LANE_CENTER, tree.byId("root")!!.lane)
        assertEquals("the first child continues straight down", TreeNode.LANE_CENTER, tree.byId("main")!!.lane)
        val detourLane = tree.byId("detour")!!.lane
        assertTrue(
            "a detour must clear the 112dp slot width, but sat at lane $detourLane",
            abs(detourLane - TreeNode.LANE_CENTER) >= 112,
        )
        assertTrue("and stay on the canvas", detourLane in 56..284)
    }

    @Test
    fun `carries time estimates and content refs through`() {
        val tree = normalizeTree("root", listOf(node("root", "a", estMinutes = 7), node("a", estMinutes = 12)))

        assertEquals(7, tree.byId("root")!!.estMinutes)
        assertEquals("content/root/a.md", tree.byId("a")!!.contentRef)
        assertNotNull(tree.single { it.isBranchOut })
    }

    @Test
    fun `survives a model answer with nothing usable in it`() {
        assertTrue(normalizeTree("root", emptyList()).isEmpty())
        assertTrue(normalizeTree("root", listOf(GeneratedNode("", "", emptyList()))).isEmpty())
    }

    @Test
    fun `unique ids never collide with what is already on the path`() {
        assertEquals("deep-learning", uniqueId("deep-learning", emptySet()))
        assertEquals("deep-learning-2", uniqueId("deep-learning", setOf("deep-learning")))
        assertEquals("deep-learning-3", uniqueId("deep-learning", setOf("deep-learning", "deep-learning-2")))
    }

    @Test
    fun `slugs are safe node ids`() {
        assertEquals("deep-learning-basics", slugify("  Deep Learning Basics! "))
        assertEquals("c-the-basics", slugify("C++: the basics"))
        assertEquals("node", slugify("!!!"))
    }
}

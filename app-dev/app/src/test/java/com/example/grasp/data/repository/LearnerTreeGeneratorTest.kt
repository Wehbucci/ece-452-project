package com.example.grasp.data.repository

import com.example.grasp.data.model.TreeNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JUnit tests for the roadmap's STRUCTURE — [normalizeTree] and the branch-id helpers.
 *
 * These deliberately don't touch Gemini: what the model writes is non-deterministic and changes
 * with the prompt, but what the app does with that answer must not. [normalizeTree] is the
 * guarantee the board's layout and connector logic is built on, so it's pinned here.
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
    fun `holds lessons only, with no standing branch-out placeholder`() {
        val tree = normalizeTree("root", listOf(node("root", "a", "detour"), node("a"), node("detour")))

        // The user picks a real lesson to branch from, so nothing on the board is a placeholder.
        assertTrue(tree.none { it.isBranchOut })
        assertEquals(listOf("root", "a", "detour"), tree.map { it.id })
        assertTrue("a leaf leads nowhere", tree.byId("a")!!.children.isEmpty())
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
    fun `orders nodes depth-first so each strand is contiguous, first strand first`() {
        val tree = normalizeTree(
            "root",
            listOf(
                node("root", "strand-1", "strand-2"),
                node("strand-2", "strand-2b"),
                node("strand-1", "strand-1b"),
                node("strand-1b"),
                node("strand-2b"),
            ),
        )

        // The presenter picks the "current" node by list order and the board lays strands out left
        // to right in this order, so a strand must never be interleaved with another.
        assertEquals(
            listOf("root", "strand-1", "strand-1b", "strand-2", "strand-2b"),
            tree.map { it.id },
        )
    }

    @Test
    fun `carries time estimates and content refs through`() {
        val tree = normalizeTree("root", listOf(node("root", "a", estMinutes = 7), node("a", estMinutes = 12)))

        assertEquals(7, tree.byId("root")!!.estMinutes)
        assertEquals(12, tree.byId("a")!!.estMinutes)
        assertEquals("content/root/a.md", tree.byId("a")!!.contentRef)
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

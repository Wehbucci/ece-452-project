package com.example.grasp.core.layout

import com.example.grasp.data.model.TreeNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Plain-JUnit tests for the board's geometry.
 *
 * Two rules everything here exists to protect: a parent is centered over its children (an
 * unbranched run is therefore a straight line), and two nodes sharing a row are never less than one
 * column apart — one column is one slot width, so anything closer overlaps on screen.
 */
class TreeLayoutTest {

    private fun chain(vararg ids: String): List<TreeNode> =
        ids.mapIndexed { index, id ->
            TreeNode(id = id, title = id.uppercase(), children = listOfNotNull(ids.getOrNull(index + 1)))
        }

    /** Every pair of nodes on the same row, as (column, column). */
    private fun rowNeighbours(layout: BoardLayout): List<Pair<Float, Float>> =
        layout.rows.entries
            .groupBy({ it.value }, { layout.columns.getValue(it.key) })
            .values
            .flatMap { columns -> columns.flatMapIndexed { i, a -> columns.drop(i + 1).map { a to it } } }

    private fun assertNoOverlap(layout: BoardLayout) {
        rowNeighbours(layout).forEach { (a, b) ->
            assertTrue("nodes at $a and $b share a row and overlap", abs(a - b) >= 1f)
        }
    }

    @Test
    fun `an unbranched roadmap is a straight vertical line`() {
        val layout = layoutBoard(chain("a", "b", "c", "d"))

        assertEquals(setOf(0f), layout.columns.values.toSet())
        assertEquals(0f, layout.columnSpan, 0.001f)
        assertEquals(listOf(0, 1, 2, 3), listOf("a", "b", "c", "d").map { layout.rows.getValue(it) })
    }

    @Test
    fun `a parent sits at the midpoint of its children`() {
        val layout = layoutBoard(
            listOf(
                TreeNode("root", "Root", children = listOf("left", "right")),
                TreeNode("left", "Left"),
                TreeNode("right", "Right"),
            ),
        )

        assertEquals(0f, layout.columns.getValue("left"), 0.001f)
        assertEquals(1f, layout.columns.getValue("right"), 0.001f)
        assertEquals(0.5f, layout.columns.getValue("root"), 0.001f)
        assertNoOverlap(layout)
    }

    @Test
    fun `a three-strand roadmap centers the root over the middle strand`() {
        // The shape the generator now asks for: root fans out, each strand then runs deep.
        val layout = layoutBoard(
            listOf(
                TreeNode("root", "Root", children = listOf("s1", "s2", "s3")),
                TreeNode("s1", "S1", children = listOf("s1b")),
                TreeNode("s1b", "S1b"),
                TreeNode("s2", "S2", children = listOf("s2b")),
                TreeNode("s2b", "S2b"),
                TreeNode("s3", "S3", children = listOf("s3b")),
                TreeNode("s3b", "S3b"),
            ),
        )

        assertEquals(2f, layout.columnSpan, 0.001f)
        assertEquals("centered over the middle strand", 1f, layout.columns.getValue("root"), 0.001f)
        // Each strand runs straight down its own column.
        listOf("s1" to 0f, "s2" to 1f, "s3" to 2f).forEach { (id, column) ->
            assertEquals(id, column, layout.columns.getValue(id), 0.001f)
            assertEquals("${id}b", column, layout.columns.getValue("${id}b"), 0.001f)
        }
        assertNoOverlap(layout)
    }

    @Test
    fun `strands of different depths still clear each other`() {
        val layout = layoutBoard(
            listOf(
                TreeNode("root", "Root", children = listOf("deep", "shallow")),
                TreeNode("deep", "Deep", children = listOf("deep-2")),
                TreeNode("deep-2", "Deep 2", children = listOf("deep-3")),
                TreeNode("deep-3", "Deep 3"),
                TreeNode("shallow", "Shallow"),
            ),
        )

        assertNoOverlap(layout)
        assertEquals(3, layout.rows.getValue("deep-3"))
        assertEquals(1, layout.rows.getValue("shallow"))
    }

    @Test
    fun `a detour hanging off a strand pushes only that strand's subtree aside`() {
        val layout = layoutBoard(
            listOf(
                TreeNode("root", "Root", children = listOf("a")),
                TreeNode("a", "A", children = listOf("b", "detour")),
                TreeNode("b", "B"),
                TreeNode("detour", "Explore: something"),
            ),
        )

        assertEquals(0f, layout.columns.getValue("b"), 0.001f)
        assertEquals(1f, layout.columns.getValue("detour"), 0.001f)
        // The line above the split re-centers over what is now below it.
        assertEquals(0.5f, layout.columns.getValue("a"), 0.001f)
        assertEquals(0.5f, layout.columns.getValue("root"), 0.001f)
        assertNoOverlap(layout)
    }

    @Test
    fun `a converge sits below both of its parents, laid out under the first`() {
        val nodes = listOf(
            TreeNode("root", "Root", children = listOf("left", "right")),
            TreeNode("left", "Left", children = listOf("merge")),
            TreeNode("right", "Right", children = listOf("deep")),
            TreeNode("deep", "Deep", children = listOf("merge")),
            TreeNode("merge", "Merge"),
        )
        val layout = layoutBoard(nodes)

        // "merge" is one below "left" but two below "deep" — the longer path wins.
        assertEquals(3, layout.rows.getValue("merge"))
        // It hangs off whichever parent reached it first, so it stays in the left subtree's column.
        assertEquals(layout.columns.getValue("left"), layout.columns.getValue("merge"), 0.001f)
        assertNoOverlap(layout)
    }

    @Test
    fun `the leftmost node always sits at column zero`() {
        // The board is exactly as wide as its content and centered by the View, so the layout only
        // ever emits columns from 0 up.
        val layout = layoutBoard(
            listOf(
                TreeNode("root", "Root", children = listOf("a", "b", "c")),
                TreeNode("a", "A"),
                TreeNode("b", "B"),
                TreeNode("c", "C"),
            ),
        )

        assertEquals(0f, layout.columns.values.min(), 0.001f)
        assertEquals(layout.columnSpan, layout.columns.values.max(), 0.001f)
    }

    @Test
    fun `an empty roadmap lays out to nothing rather than throwing`() {
        val layout = layoutBoard(emptyList())

        assertTrue(layout.columns.isEmpty())
        assertEquals(0f, layout.columnSpan, 0.001f)
    }

    @Test
    fun `a cycle in a stored roadmap still lays out`() {
        // No document should contain this, but a layout pass is the wrong place to hang or crash —
        // walking up the parents for rows would otherwise recurse forever.
        val layout = layoutBoard(
            listOf(
                TreeNode("a", "A", children = listOf("b")),
                TreeNode("b", "B", children = listOf("a")),
            ),
        )

        assertEquals(2, layout.columns.size)
        assertEquals(2, layout.rows.size)
    }
}

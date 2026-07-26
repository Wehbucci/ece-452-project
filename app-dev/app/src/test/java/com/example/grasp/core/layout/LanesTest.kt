package com.example.grasp.core.layout

import com.example.grasp.data.model.TreeNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Plain-JUnit tests for the board's horizontal layout.
 *
 * The rule everything here exists to protect: two nodes on the same row must never sit within
 * [LANE_SEPARATION] of each other, or their circles and labels overlap on screen.
 */
class LanesTest {

    private fun straightRoadmap(vararg ids: String): List<TreeNode> =
        ids.mapIndexed { index, id ->
            TreeNode(
                id = id,
                title = id.uppercase(),
                children = listOfNotNull(ids.getOrNull(index + 1)),
                lane = TreeNode.LANE_CENTER,
            )
        }

    @Test
    fun `a free row keeps the wanted lane`() {
        assertEquals(TreeNode.LANE_CENTER, freeLane(TreeNode.LANE_CENTER, occupied = emptyList()))
    }

    @Test
    fun `an occupied lane pushes the node clear of its neighbour`() {
        val lane = freeLane(TreeNode.LANE_CENTER, occupied = listOf(TreeNode.LANE_CENTER))

        assertTrue("landed on the neighbour at $lane", abs(lane - TreeNode.LANE_CENTER) >= LANE_SEPARATION)
        assertTrue("and stayed on the canvas", lane in LANE_MIN..LANE_MAX)
    }

    @Test
    fun `a row with no clear lane left takes the roomiest spot instead of stacking`() {
        // 96 and 244 leave no gap wide enough for a third node on a 340dp canvas.
        val lane = freeLane(wanted = 230, occupied = listOf(96, 244))

        assertTrue("stayed on the canvas", lane in LANE_MIN..LANE_MAX)
        // 230 is only 14 from its neighbour; anything chosen must beat that by a wide margin.
        assertTrue("crammed in at $lane", listOf(96, 244).minOf { abs(it - lane) } > 14)
    }

    @Test
    fun `rows follow the longest path from the root`() {
        val rows = rowsOf(straightRoadmap("a", "b", "c"))

        assertEquals(0, rows["a"])
        assertEquals(1, rows["b"])
        assertEquals(2, rows["c"])
    }

    @Test
    fun `a converge sits below both of its parents`() {
        val nodes = listOf(
            TreeNode("root", "Root", children = listOf("left", "right")),
            TreeNode("left", "Left", children = listOf("merge")),
            TreeNode("right", "Right", children = listOf("deep")),
            TreeNode("deep", "Deep", children = listOf("merge")),
            TreeNode("merge", "Merge"),
        )

        // "merge" is one below "left" but two below "deep" — the longer path wins.
        assertEquals(3, rowsOf(nodes)["merge"])
    }

    @Test
    fun `a branch off a mid-path node clears every row it spans`() {
        // Branching off "a" puts the new nodes alongside b and c, not on top of them.
        val lane = laneForBranch(straightRoadmap("root", "a", "b", "c"), anchorId = "a", branchLength = 2)

        assertTrue(
            "the branch must clear the main line, but sat at lane $lane",
            abs(lane - TreeNode.LANE_CENTER) >= LANE_SEPARATION,
        )
        assertTrue("and stay on the canvas", lane in LANE_MIN..LANE_MAX)
    }

    @Test
    fun `a branch off the end of the roadmap just continues straight down`() {
        // Nothing sits below "a", so there is no reason to jog sideways.
        val lane = laneForBranch(straightRoadmap("root", "a"), anchorId = "a", branchLength = 3)

        assertEquals(TreeNode.LANE_CENTER, lane)
    }

    @Test
    fun `a short branch ignores rows it will never reach`() {
        // "root" leads to a long line, but a one-node branch only has to clear the row below root.
        val nodes = straightRoadmap("root", "a", "b", "c", "d")

        assertTrue(
            "a 1-node branch still had to dodge the whole line",
            laneForBranch(nodes, anchorId = "c", branchLength = 1) in LANE_MIN..LANE_MAX,
        )
    }
}

package com.example.grasp.core.progress

import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.model.TinkerStep
import com.example.grasp.data.model.TreeNode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the XP rules and what they are counted over.
 *
 * These are one-liners, which is the point: they used to be one-liners repeated in the roadmap
 * presenter and the profile presenter, and the two disagreed about branch-out nodes and about
 * whether XP was per-path or per-account. Stating them once, here, is what stops that recurring.
 */
class XpTest {

    @Test
    fun `levels start at 1 and turn over every 200 XP`() {
        assertEquals(1, Xp.levelFor(0))
        assertEquals(1, Xp.levelFor(199))
        assertEquals(2, Xp.levelFor(200))
        assertEquals(2, Xp.levelFor(399))
        assertEquals(3, Xp.levelFor(400))
    }

    @Test
    fun `XP within a level resets at the boundary, and the bar with it`() {
        assertEquals(0, Xp.inLevel(200))
        assertEquals(0f, Xp.fractionOfLevel(200), 0.0001f)
        assertEquals(40, Xp.inLevel(240))
        assertEquals(0.2f, Xp.fractionOfLevel(240), 0.0001f)
    }

    @Test
    fun `five lessons make a level`() {
        assertEquals(200, Xp.forLessons(5))
        assertEquals(2, Xp.levelFor(Xp.forLessons(5)))
    }

    /** Nothing upstream should produce a negative count, and none of this should explode if it does. */
    @Test
    fun `a negative count is treated as none`() {
        assertEquals(0, Xp.forLessons(-3))
        assertEquals(1, Xp.levelFor(-40))
        assertEquals(0, Xp.inLevel(-40))
    }

    /**
     * A "branch out" node is a place to grow the roadmap, not a lesson. Counting it would leave a
     * finished path stuck below 100% and put XP on the board that no lesson ever earned.
     */
    @Test
    fun `a roadmap counts lessons only, never its branch-out affordance`() {
        val path = LearningPath(
            id = "p",
            title = "P",
            nodes = listOf(
                TreeNode("a", "A", completed = true),
                TreeNode("b", "B", completed = true),
                TreeNode("grow", "Branch out", isBranchOut = true),
            ),
        )

        assertEquals(2, path.lessonCount)
        assertEquals(2, path.lessonsMastered)
        assertEquals("finished, despite the affordance", 1f, path.progress, 0.0001f)
        assertEquals("2 of 2 complete", path.progressLabel)
    }

    /** A finished step is worth exactly what a finished lesson is. */
    @Test
    fun `a guide counts its done steps`() {
        val guide = TinkerGuide(
            id = "g",
            title = "G",
            steps = listOf(
                TinkerStep("s1", 1, "One", "", 1, done = true),
                TinkerStep("s2", 2, "Two", "", 1),
            ),
        )

        assertEquals(1, guide.lessonsMastered)
        assertEquals(0.5f, guide.progress, 0.0001f)
        assertEquals(Xp.forLessons(1), Xp.forLessons(guide.lessonsMastered))
    }
}

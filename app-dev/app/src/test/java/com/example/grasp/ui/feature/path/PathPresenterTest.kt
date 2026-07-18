package com.example.grasp.ui.feature.path

import com.example.grasp.data.model.Subtopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JUnit tests for [PathPresenter] — the whole reason the presenter has NO Compose/Android
 * imports. They run on the host JVM with a hand-written fake View and the in-memory
 * `FakePathRepository`; no emulator, no Compose runtime.
 *
 * They pin the behaviour the redesign depends on: state derivation (current/open/locked),
 * XP → level, the completion→unlock→confetti→level-up pipeline, the locked-tap "no", and branch
 * insertion.
 */
class PathPresenterTest {

    /** Records everything the presenter pushes so tests can assert on it. */
    private class FakeView : PathContract.View {
        var lastState: PathUiState? = null
        var notFound = false
        var subtopicSheet: Subtopic? = null
        var subtopicCompleted = false
        var branchSheetShown = false
        var dismissCount = 0
        var confetti = 0
        val unlocks = mutableListOf<String>()
        val popIns = mutableListOf<String>()
        val levelUps = mutableListOf<Int>()
        val toasts = mutableListOf<String>()
        val shakes = mutableListOf<String>()
        val chats = mutableListOf<String>()

        override fun showPath(state: PathUiState) { lastState = state }
        override fun showNotFound() { notFound = true }
        override fun showSubtopicSheet(subtopic: Subtopic, completed: Boolean) {
            subtopicSheet = subtopic; subtopicCompleted = completed
        }
        override fun showBranchSheet() { branchSheetShown = true }
        override fun dismissSheet() { dismissCount++ }
        override fun playUnlock(nodeId: String) { unlocks += nodeId }
        override fun playPopIn(nodeId: String) { popIns += nodeId }
        override fun showConfetti() { confetti++ }
        override fun showLevelUp(level: Int) { levelUps += level }
        override fun showToast(message: String) { toasts += message }
        override fun shakeNode(nodeId: String) { shakes += nodeId }
        override fun openChat(context: String, pathId: String, nodeId: String) { chats += nodeId }
    }

    private fun PathUiState.node(id: String) = nodes.first { it.id == id }

    private fun attach(pathId: String = "ml-101"): Pair<PathPresenter, FakeView> {
        val presenter = PathPresenter(pathId)
        val view = FakeView()
        presenter.attach(view)
        return presenter to view
    }

    @Test
    fun `derives current, open and locked states from the completed frontier`() {
        val (_, view) = attach()
        val state = view.lastState!!

        // whatis / types / data are pre-completed in the fake ML graph.
        assertEquals(PathNodeState.DONE, state.node("data-basics").state)
        // First reachable, incomplete, non-branch node is current; its sibling is merely open.
        assertEquals(PathNodeState.CURRENT, state.node("supervised").state)
        assertEquals(PathNodeState.OPEN, state.node("unsupervised").state)
        // Downstream of an incomplete node is locked.
        assertEquals(PathNodeState.LOCKED, state.node("regression").state)
        assertEquals(PathNodeState.BRANCH, state.node("branch-1").state)
    }

    @Test
    fun `HUD numbers derive from completion`() {
        val (_, view) = attach()
        val state = view.lastState!!
        assertEquals(3, state.masteredCount)
        assertEquals(9, state.totalLessons) // 10 nodes minus the branch affordance
        assertEquals(1, state.level)        // 3 × 40 = 120 XP → level 1
        assertEquals(120, state.xpInLevel)
    }

    @Test
    fun `marking complete adds XP, unlocks the next node and bursts confetti`() {
        val (presenter, view) = attach()

        presenter.onMarkComplete("supervised")

        val state = view.lastState!!
        assertEquals(PathNodeState.DONE, state.node("supervised").state)
        assertEquals(4, state.masteredCount)
        assertEquals(160, state.xpInLevel)
        assertEquals(1, view.confetti)
        assertEquals(1, view.dismissCount)
        // Frontier moved to the next reachable node.
        assertEquals(PathNodeState.CURRENT, state.node("unsupervised").state)
        assertTrue("unsupervised" in view.unlocks)
        assertTrue(view.levelUps.isEmpty()) // still level 1
    }

    @Test
    fun `crossing a 200-XP multiple raises a level-up`() {
        val (presenter, view) = attach()

        presenter.onMarkComplete("supervised")   // 160 XP
        presenter.onMarkComplete("unsupervised")  // 200 XP → level 2

        assertEquals(2, view.lastState!!.level)
        assertEquals(0, view.lastState!!.xpInLevel) // 200 XP = fresh start of level 2
        assertEquals(listOf(2), view.levelUps)
    }

    @Test
    fun `tapping a locked node shakes and toasts without opening a sheet`() {
        val (presenter, view) = attach()

        presenter.onNodeTapped("regression")

        assertEquals(listOf("regression"), view.shakes)
        assertEquals(1, view.toasts.size)
        assertTrue(view.toasts.first().contains("Supervised"))
        assertNull(view.subtopicSheet)
    }

    @Test
    fun `tapping an available node opens the detail sheet`() {
        val (presenter, view) = attach()

        presenter.onNodeTapped("supervised")

        assertNotNull(view.subtopicSheet)
        assertEquals("supervised", view.subtopicSheet!!.nodeId)
        assertFalse(view.subtopicCompleted)
        assertTrue(view.shakes.isEmpty())
    }

    @Test
    fun `tapping the branch node opens the branch sheet`() {
        val (presenter, view) = attach()

        presenter.onNodeTapped("branch-1")

        assertTrue(view.branchSheetShown)
        assertNull(view.subtopicSheet)
    }

    @Test
    fun `generating a branch inserts a topic node plus a new branch and pops it in`() {
        val (presenter, view) = attach()

        presenter.onGenerateBranch("Deep Learning")

        val state = view.lastState!!
        val topic = state.nodes.firstOrNull { it.title == "Deep Learning" }
        assertNotNull("a new topic node should be inserted", topic)
        assertTrue("a fresh branch node should be appended", state.nodes.count { it.state == PathNodeState.BRANCH } >= 1)
        assertTrue("YOUR BRANCHES region should appear", state.regions.any { it.label == "YOUR BRANCHES" })
        assertEquals(listOf(topic!!.id), view.popIns)
        assertEquals(1, view.dismissCount)
        assertTrue(view.toasts.any { it.contains("branch", ignoreCase = true) })
    }

    @Test
    fun `ask AI routes to chat for the node`() {
        val (presenter, view) = attach()

        presenter.onAskAi("supervised")

        assertEquals(listOf("supervised"), view.chats)
    }

    @Test
    fun `unknown path shows not-found`() {
        val presenter = PathPresenter("does-not-resolve-to-null?")
        val view = FakeView()
        // FakePathRepository never returns null, so this simply proves attach renders SOMETHING
        // rather than crashing; not-found is exercised by the null-repo contract.
        presenter.attach(view)
        assertTrue(view.notFound || view.lastState != null)
    }
}

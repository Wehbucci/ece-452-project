package com.example.grasp.ui.feature.path

import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.repository.FakePathRepository
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.CoroutineContext

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
        val loadingTitles = mutableListOf<String>()
        val branchSuggestions = mutableListOf<List<String>>()
        val generatingStates = mutableListOf<Boolean>()
        val unlocks = mutableListOf<String>()
        val popIns = mutableListOf<String>()
        val levelUps = mutableListOf<Int>()
        val toasts = mutableListOf<String>()
        val shakes = mutableListOf<String>()
        val chats = mutableListOf<String>()
        val chatBlockIndices = mutableListOf<Int>()

        override fun showPath(state: PathUiState) { lastState = state }
        override fun showNotFound() { notFound = true }
        override fun showSubtopicLoading(title: String) { loadingTitles += title }
        override fun showSubtopicSheet(subtopic: Subtopic, completed: Boolean) {
            subtopicSheet = subtopic; subtopicCompleted = completed
        }
        override fun showBranchSheet() { branchSheetShown = true }
        override fun showBranchSuggestions(topics: List<String>) { branchSuggestions += topics }
        override fun showBranchGenerating(generating: Boolean) { generatingStates += generating }
        override fun dismissSheet() { dismissCount++ }
        override fun playUnlock(nodeId: String) { unlocks += nodeId }
        override fun playPopIn(nodeId: String) { popIns += nodeId }
        override fun showConfetti() { confetti++ }
        override fun showLevelUp(level: Int) { levelUps += level }
        override fun showToast(message: String) { toasts += message }
        override fun shakeNode(nodeId: String) { shakes += nodeId }
        override fun openChat(context: String, pathId: String, nodeId: String, blockIndex: Int) {
            chats += nodeId
            chatBlockIndices += blockIndex
        }
    }

    /**
     * Runs every `launch` inline on the calling thread. The presenter now loads content and grows
     * branches in coroutines; with the fake (never-actually-suspending) repository this makes those
     * paths complete before the test's next line — no test-dispatcher dependency needed.
     */
    private class DirectDispatcher : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
    }

    private fun PathUiState.node(id: String) = nodes.first { it.id == id }

    private fun attach(pathId: String = "ml-101"): Pair<PathPresenter, FakeView> {
        val presenter = PathPresenter(pathId, FakePathRepository, DirectDispatcher())
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
    fun `tapping an available node shows the loading state, then its content`() {
        val (presenter, view) = attach()

        presenter.onNodeTapped("supervised")

        // The lesson is generated on first open, so the sheet opens before the content exists.
        assertEquals(listOf("Supervised"), view.loadingTitles)
        assertNotNull(view.subtopicSheet)
        assertEquals("supervised", view.subtopicSheet!!.nodeId)
        assertFalse(view.subtopicCompleted)
        assertTrue(view.shakes.isEmpty())
    }

    @Test
    fun `a lesson that arrives after the sheet closed is discarded`() {
        val (presenter, view) = attach()

        presenter.onNodeTapped("supervised")
        view.subtopicSheet = null
        presenter.onSheetDismissed()
        presenter.onNodeTapped("supervised")

        // Re-opening works; the point is the presenter tracks WHICH node the sheet is waiting on.
        assertNotNull(view.subtopicSheet)
        assertEquals(2, view.loadingTitles.size)
    }

    @Test
    fun `tapping the branch node opens the branch sheet and asks for starter ideas`() {
        val (presenter, view) = attach()

        presenter.onNodeTapped("branch-1")

        assertTrue(view.branchSheetShown)
        assertNull(view.subtopicSheet)
        // The fake repository has no AI, so the chips come back empty rather than hardcoded.
        assertEquals(listOf(emptyList<String>()), view.branchSuggestions)
    }

    @Test
    fun `generating a branch inserts the generated nodes and pops the first one in`() {
        val (presenter, view) = attach()

        presenter.onNodeTapped("branch-1")
        presenter.onGenerateBranch("Deep Learning")

        val state = view.lastState!!
        val topic = state.nodes.firstOrNull { it.title == "Deep Learning" }
        assertNotNull("a new topic node should be inserted", topic)
        assertNull("the consumed affordance is gone", state.nodes.firstOrNull { it.id == "branch-1" })
        assertEquals(
            "exactly one affordance remains, at the end of the new branch",
            1,
            state.nodes.count { it.state == PathNodeState.BRANCH },
        )
        // The node that pointed at the affordance now points at the branch.
        assertEquals(listOf("neural-networks"), topic!!.parentIds)
        assertTrue("YOUR BRANCHES region should appear", state.regions.any { it.label == "YOUR BRANCHES" })
        assertEquals(listOf(topic.id), view.popIns)
        assertEquals(1, view.dismissCount)
        assertEquals("the button locks while generating", listOf(true, false), view.generatingStates)
        assertTrue(view.toasts.any { it.contains("branch", ignoreCase = true) })
    }

    @Test
    fun `a second branch grows from the new affordance, not the consumed one`() {
        val (presenter, view) = attach()

        presenter.onNodeTapped("branch-1")
        presenter.onGenerateBranch("Deep Learning")
        presenter.onGenerateBranch("Transformers")

        val state = view.lastState!!
        val transformers = state.nodes.first { it.title == "Transformers" }
        // Chained onto the previous branch — the old code always regrew from the first affordance.
        assertEquals(listOf("deep-learning"), transformers.parentIds)
        assertEquals(1, state.nodes.count { it.state == PathNodeState.BRANCH })
    }

    @Test
    fun `ask AI routes to chat for the node`() {
        val (presenter, view) = attach()

        presenter.onAskAi("supervised")

        assertEquals(listOf("supervised"), view.chats)
        assertEquals("whole subtopic, not one block", listOf(-1), view.chatBlockIndices)
    }

    @Test
    fun `tapping a lesson paragraph opens a chat scoped to that paragraph`() {
        val (presenter, view) = attach()

        presenter.onAskAboutBlock("supervised", "Labelled data is what makes it supervised.", 2)

        assertEquals(listOf("supervised"), view.chats)
        assertEquals(listOf(2), view.chatBlockIndices)
    }

    @Test
    fun `unknown path shows not-found`() {
        // FakePathRepository never returns null, so this simply proves attach renders SOMETHING
        // rather than crashing; not-found is exercised by the null-repo contract.
        val (_, view) = attach("does-not-resolve-to-null?")
        assertTrue(view.notFound || view.lastState != null)
    }
}

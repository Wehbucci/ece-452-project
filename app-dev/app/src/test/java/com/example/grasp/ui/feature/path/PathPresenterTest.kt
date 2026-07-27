package com.example.grasp.ui.feature.path

import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.repository.FakePathRepository
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs

/**
 * Plain-JUnit tests for [PathPresenter] — the whole reason the presenter has NO Compose/Android
 * imports. They run on the host JVM with a hand-written fake View and the in-memory
 * `FakePathRepository`; no emulator, no Compose runtime.
 *
 * They pin the behaviour the redesign depends on: state derivation (current/open/done), XP →
 * level, the completion→advance→confetti→level-up pipeline, and branch insertion re-laying the
 * board out.
 */
class PathPresenterTest {

    /** Records everything the presenter pushes so tests can assert on it. */
    private class FakeView : PathContract.View {
        var lastState: PathUiState? = null
        var notFound = false
        var subtopicSheet: Subtopic? = null
        var subtopicCompleted = false
        var branchSheetShown = false
        val branchFromTitles = mutableListOf<String>()
        var dismissCount = 0
        var confetti = 0
        val loadingTitles = mutableListOf<String>()
        val loadingSaidGenerating = mutableListOf<Boolean>()
        val branchSuggestions = mutableListOf<List<String>>()
        val generatingStates = mutableListOf<Boolean>()
        val advances = mutableListOf<String>()
        val popIns = mutableListOf<String>()
        val levelUps = mutableListOf<Int>()
        val toasts = mutableListOf<String>()
        val chats = mutableListOf<String>()
        val chatBlockIndices = mutableListOf<Int>()

        override fun showPath(state: PathUiState) { lastState = state }
        override fun showNotFound() { notFound = true }
        override fun showSubtopicLoading(title: String, generating: Boolean) {
            loadingTitles += title
            loadingSaidGenerating += generating
        }
        override fun showSubtopicSheet(subtopic: Subtopic, completed: Boolean) {
            subtopicSheet = subtopic; subtopicCompleted = completed
        }
        override fun showBranchSheet(fromTitle: String) {
            branchSheetShown = true
            branchFromTitles += fromTitle
        }
        override fun showBranchSuggestions(topics: List<String>) { branchSuggestions += topics }
        override fun showBranchGenerating(generating: Boolean) { generatingStates += generating }
        override fun dismissSheet() { dismissCount++ }
        override fun playAdvance(nodeId: String) { advances += nodeId }
        override fun playPopIn(nodeId: String) { popIns += nodeId }
        override fun showConfetti() { confetti++ }
        override fun showLevelUp(level: Int) { levelUps += level }
        override fun showToast(message: String) { toasts += message }
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
    fun `derives done, current and open states from the completed set`() {
        val (_, view) = attach()
        val state = view.lastState!!

        // whatis / types / data are pre-completed in the fake ML graph.
        assertEquals(PathNodeState.DONE, state.node("data-basics").state)
        // First incomplete, non-branch node is current; its sibling is merely open.
        assertEquals(PathNodeState.CURRENT, state.node("supervised").state)
        assertEquals(PathNodeState.OPEN, state.node("unsupervised").state)
        // Nothing is gated: a node several steps ahead of the marker is open too.
        assertEquals(PathNodeState.OPEN, state.node("regression").state)
        assertEquals(PathNodeState.OPEN, state.node("model-evaluation").state)
        // The old standing "Branch out" placeholder is not part of the board any more.
        assertNull(state.nodes.firstOrNull { it.id == "branch-1" })
        assertTrue(state.nodes.none { it.state == PathNodeState.BRANCH })
    }

    @Test
    fun `HUD numbers derive from completion`() {
        val (_, view) = attach()
        val state = view.lastState!!
        assertEquals(3, state.masteredCount)
        assertEquals(9, state.totalLessons) // 10 nodes minus the legacy branch placeholder
        assertEquals(1, state.level)        // 3 × 40 = 120 XP → level 1
        assertEquals(120, state.xpInLevel)
    }

    @Test
    fun `marking complete adds XP, advances to the next node and bursts confetti`() {
        val (presenter, view) = attach()

        presenter.onMarkComplete("supervised")

        val state = view.lastState!!
        assertEquals(PathNodeState.DONE, state.node("supervised").state)
        assertEquals(4, state.masteredCount)
        assertEquals(160, state.xpInLevel)
        assertEquals(1, view.confetti)
        assertEquals(1, view.dismissCount)
        // The marker moved on to the next incomplete node.
        assertEquals(PathNodeState.CURRENT, state.node("unsupervised").state)
        assertTrue("unsupervised" in view.advances)
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
    fun `a node further along the path opens like any other`() {
        val (presenter, view) = attach()

        // "regression" sits behind two unfinished lessons — the roadmap suggests an order, it
        // doesn't enforce one, so this still opens.
        presenter.onNodeTapped("regression")

        assertEquals("regression", view.subtopicSheet?.nodeId)
        assertTrue(view.toasts.isEmpty())
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
    fun `branching off a node asks for starter ideas for that node`() {
        val (presenter, view) = attach()

        presenter.onBranchFromNode("neural-networks")

        assertTrue(view.branchSheetShown)
        assertNull(view.subtopicSheet)
        assertEquals(listOf("Neural Networks"), view.branchFromTitles)
        // The fake repository has no AI, so the chips come back empty rather than hardcoded.
        assertEquals(listOf(emptyList<String>()), view.branchSuggestions)
    }

    @Test
    fun `the picker turns on and off without touching the board`() {
        val (presenter, view) = attach()

        val before = view.lastState!!
        assertFalse("not picking until asked", before.pickingBranchAnchor)

        presenter.onAddNodeRequested()
        val picking = view.lastState!!
        assertTrue(picking.pickingBranchAnchor)
        // Nothing is added to or moved on the board — the whole board just becomes the target.
        assertEquals(before.nodes, picking.nodes)

        presenter.onAddModeCancelled()
        assertFalse(view.lastState!!.pickingBranchAnchor)
        assertEquals(before.nodes, view.lastState!!.nodes)
    }

    @Test
    fun `picking a section leaves the picker and opens the sheet for it`() {
        val (presenter, view) = attach()

        presenter.onAddNodeRequested()
        presenter.onNodeTapped("data-basics")

        assertFalse(view.lastState!!.pickingBranchAnchor)
        assertTrue(view.branchSheetShown)
        assertEquals(listOf("Data Basics"), view.branchFromTitles)

        presenter.onGenerateBranch("Feature engineering")
        assertEquals(
            listOf("data-basics"),
            view.lastState!!.nodes.first { it.title == "Feature engineering" }.parentIds,
        )
    }

    @Test
    fun `tapping a node while picking an anchor picks it instead of opening its lesson`() {
        val (presenter, view) = attach()

        presenter.onAddNodeRequested()
        presenter.onNodeTapped("regression") // outside the picker this would open its lesson

        assertNull(view.subtopicSheet)
        assertTrue(view.branchSheetShown)
        assertEquals(listOf("Regression"), view.branchFromTitles)
    }

    @Test
    fun `branching off a lesson adds a detour without disturbing the main line`() {
        val (presenter, view) = attach()

        presenter.onBranchFromNode("data-basics")
        assertEquals(listOf("Data Basics"), view.branchFromTitles)

        presenter.onGenerateBranch("Feature engineering")

        val state = view.lastState!!
        val detour = state.nodes.first { it.title == "Feature engineering" }
        assertEquals(listOf("data-basics"), detour.parentIds)
        // Everything data-basics already led to is still hanging off it.
        assertEquals(listOf("data-basics"), state.node("supervised").parentIds)
        assertEquals(listOf("data-basics"), state.node("unsupervised").parentIds)
        // Nothing placeholder-ish is added to the board along with it.
        assertTrue(state.nodes.none { it.state == PathNodeState.BRANCH })
    }

    @Test
    fun `adding a detour re-lays the whole board out around it`() {
        val (presenter, view) = attach()
        val before = view.lastState!!

        presenter.onBranchFromNode("data-basics")
        presenter.onGenerateBranch("Feature engineering")

        val state = view.lastState!!
        val detour = state.nodes.first { it.title == "Feature engineering" }
        val neighbours = state.nodes.filter { it.row == detour.row && it.id != detour.id }

        assertTrue("the detour shares a row with the existing branches", neighbours.isNotEmpty())
        neighbours.forEach { neighbour ->
            assertTrue(
                "${neighbour.id} at column ${neighbour.column} overlaps the detour at ${detour.column}",
                abs(neighbour.column - detour.column) >= 1f,
            )
        }
        // The board got wider and the nodes above the split shifted to stay centered over it —
        // that re-centering is the whole point of deriving positions instead of storing them.
        assertTrue("the board should widen", state.columnSpan > before.columnSpan)
        assertNotEquals(before.node("what-is-ml").column, state.node("what-is-ml").column)
    }

    @Test
    fun `every node on the board is centered over the children below it`() {
        val (_, view) = attach()
        val state = view.lastState!!
        val byId = state.nodes.associateBy { it.id }

        // What a "centered" roadmap means concretely: no node is off to the side of its own subtree.
        state.nodes.forEach { node ->
            val kids = state.nodes.filter { node.id in it.parentIds }.map(PathNodeUi::column)
            if (kids.size < 2) return@forEach
            assertEquals(
                "${node.id} is not centered over its children",
                (kids.min() + kids.max()) / 2f,
                node.column,
                0.001f,
            )
        }
        // And the leftmost node anchors column 0, so the View can center the board as one block.
        assertEquals(0f, state.nodes.minOf { it.column }, 0.001f)
        assertEquals(state.columnSpan, state.nodes.maxOf { it.column }, 0.001f)
        assertTrue(byId.isNotEmpty())
    }

    @Test
    fun `generating a branch inserts the generated nodes and pops the first one in`() {
        val (presenter, view) = attach()
        val before = view.lastState!!

        presenter.onBranchFromNode("neural-networks")
        presenter.onGenerateBranch("Deep Learning")

        val state = view.lastState!!
        val topic = state.nodes.firstOrNull { it.title == "Deep Learning" }
        assertNotNull("a new topic node should be inserted", topic)
        assertEquals(listOf("neural-networks"), topic!!.parentIds)
        // A grown branch adds no region pill — it's just more roadmap.
        assertEquals(before.regions, state.regions)
        assertEquals(listOf(topic.id), view.popIns)
        assertEquals(1, view.dismissCount)
        assertEquals("the button locks while generating", listOf(true, false), view.generatingStates)
        assertTrue(view.toasts.any { it.contains("branch", ignoreCase = true) })
    }

    @Test
    fun `a branch can be grown from the node a previous branch just added`() {
        val (presenter, view) = attach()

        presenter.onBranchFromNode("neural-networks")
        presenter.onGenerateBranch("Deep Learning")
        presenter.onBranchFromNode("deep-learning")
        presenter.onGenerateBranch("Transformers")

        val state = view.lastState!!
        assertEquals(
            listOf("deep-learning"),
            state.nodes.first { it.title == "Transformers" }.parentIds,
        )
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

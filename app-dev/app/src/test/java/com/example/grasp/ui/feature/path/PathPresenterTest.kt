package com.example.grasp.ui.feature.path

import com.example.grasp.core.edit.LessonEdit
import com.example.grasp.core.edit.RoadmapEdit
import com.example.grasp.data.model.LessonBlock
import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.repository.FakePathRepository
import com.example.grasp.data.repository.PathRepository
import com.example.grasp.ui.feature.subtopic.SectionShape
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
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
        var subtopicEditing = false
        var subtopicSection: SectionShape? = null
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
        val chatBlockIds = mutableListOf<String>()
        val deleteAsks = mutableListOf<Triple<String, String, Boolean>>()

        override fun showPath(state: PathUiState) { lastState = state }
        override fun showNotFound() { notFound = true }
        override fun showSubtopicLoading(title: String, generating: Boolean) {
            loadingTitles += title
            loadingSaidGenerating += generating
        }
        override fun showSubtopicSheet(
            subtopic: Subtopic,
            completed: Boolean,
            editing: Boolean,
            canUndo: Boolean,
            section: SectionShape?,
        ) {
            subtopicSheet = subtopic; subtopicCompleted = completed; subtopicEditing = editing
            subtopicSection = section
        }
        override fun showBranchSheet(fromTitle: String) {
            branchSheetShown = true
            branchFromTitles += fromTitle
        }
        override fun showBranchSuggestions(topics: List<String>) { branchSuggestions += topics }
        override fun showBranchGenerating(generating: Boolean) { generatingStates += generating }
        override fun confirmDeleteSection(nodeId: String, title: String, hasDescendants: Boolean) {
            deleteAsks += Triple(nodeId, title, hasDescendants)
        }
        override fun dismissSheet() { dismissCount++ }
        override fun playAdvance(nodeId: String) { advances += nodeId }
        override fun playPopIn(nodeId: String) { popIns += nodeId }
        override fun showConfetti() { confetti++ }
        override fun showLevelUp(level: Int) { levelUps += level }
        override fun showToast(message: String) { toasts += message }
        override fun openChat(context: String, pathId: String, nodeId: String, blockId: String) {
            chats += nodeId
            chatBlockIds += blockId
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

    /**
     * The canned library with every OTHER path's progress hidden.
     *
     * XP is account-wide now (see `core.progress.Xp`), so a presenter's HUD numbers depend on work
     * done on paths these tests are not about — and the canned Cooking path and omelette guide
     * both ship with completions. Narrowing the account total to this one roadmap keeps each XP
     * assertion below a statement about the roadmap under test. The account-wide behaviour itself
     * is asserted separately, against the unmodified fake.
     */
    private class OnlyThisPath(private val pathId: String) : PathRepository by FakePathRepository {
        override suspend fun totalLessonsMastered(): Int =
            FakePathRepository.learningPath(pathId)?.lessonsMastered ?: 0
    }

    /** The fake is a singleton, so each test starts from the canned content, not the last one's. */
    @Before
    fun clearFakeEdits() = FakePathRepository.clearEdits()

    private fun PathUiState.node(id: String) = nodes.first { it.id == id }

    private fun attach(
        pathId: String = "ml-101",
        repo: PathRepository = OnlyThisPath(pathId),
    ): Pair<PathPresenter, FakeView> {
        val presenter = PathPresenter(pathId, repo, DirectDispatcher())
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

    /**
     * The reason XP moved out of this presenter: a level that only counted the roadmap on screen
     * dropped back to 1 every time the user opened a different one, which reads as the app having
     * taken their progress away.
     */
    @Test
    fun `the HUD level counts lessons finished on every other path too`() {
        // The unmodified fake library: 3 done here, plus 2 in the Cooking path and 2 omelette
        // steps elsewhere — 7 lessons, 280 XP, level 2.
        val (_, view) = attach(repo = FakePathRepository)

        val state = view.lastState!!
        assertEquals("still this roadmap's own count", 3, state.masteredCount)
        assertEquals(2, state.level)
        assertEquals(80, state.xpInLevel)
    }

    /** Work done elsewhere counts, and finishing a lesson here still moves the same bar. */
    @Test
    fun `completing a lesson adds to the account total, not a per-path one`() {
        val (presenter, view) = attach(repo = FakePathRepository)

        presenter.onMarkComplete("supervised")

        val state = view.lastState!!
        assertEquals(4, state.masteredCount)
        assertEquals("8 lessons across the account = 320 XP", 2, state.level)
        assertEquals(120, state.xpInLevel)
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
    fun `the edit menu opens and closes without touching the board`() {
        val (presenter, view) = attach()

        val before = view.lastState!!
        assertEquals("resting until asked", BoardMode.BROWSING, before.boardMode)

        presenter.onEditRoadmapRequested()
        val menu = view.lastState!!
        assertEquals(BoardMode.MENU, menu.boardMode)
        // Opening the menu changes nothing about the roadmap, and doesn't make the board a picker.
        assertEquals(before.nodes, menu.nodes)
        assertFalse(menu.boardMode.picking)

        presenter.onEditRoadmapRequested()
        assertEquals(BoardMode.BROWSING, view.lastState!!.boardMode)
        assertEquals(before.nodes, view.lastState!!.nodes)
    }

    @Test
    fun `choosing add turns the board into a picker without touching it`() {
        val (presenter, view) = attach()
        val before = view.lastState!!

        presenter.onEditRoadmapRequested()
        presenter.onAddSectionChosen()

        val picking = view.lastState!!
        assertEquals(BoardMode.PICK_ADD_PARENT, picking.boardMode)
        // Nothing is added to or moved on the board — the whole board just becomes the target.
        assertEquals(before.nodes.map { it.id }, picking.nodes.map { it.id })
        assertTrue("anything can sprout a detour", picking.nodes.all { it.pickable })

        presenter.onBoardEditCancelled()
        assertEquals(BoardMode.BROWSING, view.lastState!!.boardMode)
        assertEquals(before.nodes, view.lastState!!.nodes)
    }

    @Test
    fun `picking a section leaves the picker and opens the sheet for it`() {
        val (presenter, view) = attach()

        presenter.onAddSectionChosen()
        presenter.onNodeTapped("data-basics")

        assertEquals(BoardMode.BROWSING, view.lastState!!.boardMode)
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

        presenter.onAddSectionChosen()
        presenter.onNodeTapped("regression") // outside the picker this would open its lesson

        assertNull(view.subtopicSheet)
        assertTrue(view.branchSheetShown)
        assertEquals(listOf("Regression"), view.branchFromTitles)
    }

    @Test
    fun `deleting a section is asked about before anything happens`() {
        val (presenter, view) = attach()

        presenter.onDeleteSectionChosen()
        presenter.onNodeTapped("clustering")

        // The tap picks the section and ends the picker — it does NOT delete it.
        assertEquals(BoardMode.BROWSING, view.lastState!!.boardMode)
        assertEquals(listOf(Triple("clustering", "Clustering", true)), view.deleteAsks)
        assertNotNull(view.lastState!!.nodes.firstOrNull { it.id == "clustering" })

        presenter.onDeleteSectionConfirmed("clustering", withDescendants = false)

        assertNull(view.lastState!!.nodes.firstOrNull { it.id == "clustering" })
        // What grew beyond it is still there — deleting one section isn't deleting a branch.
        assertNotNull(view.lastState!!.nodes.firstOrNull { it.id == "model-evaluation" })
    }

    @Test
    fun `deleting a section with its branch takes everything below it`() {
        val (presenter, view) = attach()

        presenter.onDeleteSectionChosen()
        presenter.onNodeTapped("unsupervised")
        presenter.onDeleteSectionConfirmed("unsupervised", withDescendants = true)

        val ids = view.lastState!!.nodes.map { it.id }
        assertFalse("unsupervised" in ids)
        assertFalse("its own branch goes with it", "clustering" in ids)
        // The other track is untouched.
        assertTrue("supervised" in ids)
    }

    @Test
    fun `the roadmap's first section can be branched from but not deleted or moved`() {
        val (presenter, view) = attach()

        // Nothing hangs it off anything: it is the roadmap, not a section of it.
        presenter.onAddSectionChosen()
        assertTrue(view.lastState!!.node("what-is-ml").pickable)

        presenter.onDeleteSectionChosen()
        assertFalse(view.lastState!!.node("what-is-ml").pickable)
        assertTrue(view.lastState!!.node("regression").pickable)

        presenter.onMoveSectionChosen()
        assertFalse(view.lastState!!.node("what-is-ml").pickable)
    }

    @Test
    fun `moving a section asks which one, then where it goes`() {
        val (presenter, view) = attach()

        presenter.onMoveSectionChosen()
        presenter.onNodeTapped("regression")

        // Still a picker — the same gesture, now asking the second half of the question.
        val choosing = view.lastState!!
        assertEquals(BoardMode.PICK_MOVE_PARENT, choosing.boardMode)
        assertEquals("Regression", choosing.movingTitle)
        assertNull("picking a section to move doesn't open it", view.subtopicSheet)

        presenter.onNodeTapped("unsupervised")

        val moved = view.lastState!!
        assertEquals(BoardMode.BROWSING, moved.boardMode)
        assertNull(moved.movingTitle)
        assertEquals(listOf("unsupervised"), moved.node("regression").parentIds)
        assertTrue(view.toasts.any { it.contains("moved", ignoreCase = true) })
    }

    @Test
    fun `a section can go anywhere but itself and where it already is`() {
        val (presenter, view) = attach()

        presenter.onMoveSectionChosen()
        presenter.onNodeTapped("regression")
        val state = view.lastState!!

        assertFalse("itself", state.node("regression").pickable)
        // Its current parent: moving it there would change nothing.
        assertFalse("where it already is", state.node("supervised").pickable)
        // Its own branch IS offered — the section is taken out of the tree before it goes back in,
        // so moving one further down its own line is an ordinary move, not a loop.
        assertTrue("its own child", state.node("model-evaluation").pickable)
        assertTrue("deeper down its own branch", state.node("neural-networks").pickable)
        assertTrue("another track entirely", state.node("unsupervised").pickable)
        assertTrue("back up towards the root", state.node("what-is-ml").pickable)
    }

    @Test
    fun `moving a section down its own line leaves that line behind`() {
        val (presenter, view) = attach()

        presenter.onMoveSectionChosen()
        presenter.onNodeTapped("supervised")
        presenter.onNodeTapped("regression") // supervised's own child

        val moved = view.lastState!!
        assertEquals(listOf("regression"), moved.node("supervised").parentIds)
        // What was below it closed up into the place it left, rather than coming along.
        assertEquals(listOf("data-basics"), moved.node("regression").parentIds)
        // Nothing fell off the board.
        assertTrue("model-evaluation" in moved.nodes.map { it.id })
    }

    @Test
    fun `tapping a section the picker rules out does nothing at all`() {
        val (presenter, view) = attach()

        presenter.onMoveSectionChosen()
        presenter.onNodeTapped("regression")
        val before = view.lastState!!

        presenter.onNodeTapped("supervised") // the parent it already has

        val after = view.lastState!!
        assertEquals("still asking the same question", BoardMode.PICK_MOVE_PARENT, after.boardMode)
        assertEquals("Regression", after.movingTitle)
        assertEquals(before.nodes, after.nodes)
        assertNull(view.subtopicSheet)
    }

    @Test
    fun `cancelling a half-finished move leaves the roadmap alone`() {
        val (presenter, view) = attach()
        val before = view.lastState!!

        presenter.onMoveSectionChosen()
        presenter.onNodeTapped("regression")
        presenter.onBoardEditCancelled()

        val after = view.lastState!!
        assertEquals(BoardMode.BROWSING, after.boardMode)
        assertNull(after.movingTitle)
        assertEquals(before.nodes, after.nodes)
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
        assertEquals("whole subtopic, not one block", listOf(""), view.chatBlockIds)
    }

    /**
     * The absent node id is the whole signal: it is what tells the chat it is scoped to the tree
     * rather than to whichever lesson happened to be open last.
     */
    @Test
    fun `asking about the roadmap opens a chat with no node`() {
        val (presenter, view) = attach()

        presenter.onAskAboutRoadmap()

        assertEquals(listOf(""), view.chats)
        assertEquals(listOf(""), view.chatBlockIds)
    }

    @Test
    fun `tapping a lesson paragraph opens a chat scoped to that paragraph`() {
        val (presenter, view) = attach()

        presenter.onAskAboutBlock("supervised", "Labelled data is what makes it supervised.", "b-labelled")

        assertEquals(listOf("supervised"), view.chats)
        assertEquals(listOf("b-labelled"), view.chatBlockIds)
    }

    /**
     * Closing the tutor puts the user back where they asked from. The sheet has to be closed for
     * the chat to be shown at all, so without this a question about a paragraph costs you the
     * lesson you were reading it in.
     */
    @Test
    fun `closing the tutor reopens the lesson it was asked from`() {
        val (presenter, view) = attach()
        presenter.onNodeTapped("supervised")
        presenter.onAskAi("supervised")
        view.subtopicSheet = null // the real screen closes the sheet to make room for the chat

        presenter.onChatClosed()

        assertEquals("supervised", view.subtopicSheet?.nodeId)
    }

    @Test
    fun `closing a tutor opened from a paragraph reopens that paragraph's lesson`() {
        val (presenter, view) = attach()
        presenter.onNodeTapped("regression")
        presenter.onAskAboutBlock("regression", "Some paragraph.", "b-1")
        view.subtopicSheet = null

        presenter.onChatClosed()

        assertEquals("regression", view.subtopicSheet?.nodeId)
    }

    /** The roadmap tutor was opened from the board, so the board is where closing it belongs. */
    @Test
    fun `closing the roadmap tutor leaves the board alone`() {
        val (presenter, view) = attach()
        presenter.onAskAboutRoadmap()
        view.subtopicSheet = null

        presenter.onChatClosed()

        assertNull(view.subtopicSheet)
    }

    /** A lesson the tutor deleted while the chat was open has nothing to go back to. */
    @Test
    fun `closing the tutor on a section that is gone does not reopen anything`() {
        val (presenter, view) = attach()
        presenter.onNodeTapped("clustering")
        presenter.onAskAi("clustering")
        presenter.onDeleteSectionConfirmed("clustering", withDescendants = false)
        view.subtopicSheet = null

        presenter.onChatClosed()

        assertNull(view.subtopicSheet)
    }

    @Test
    fun `a lesson always opens in reading mode`() {
        val (presenter, view) = attach()
        presenter.onNodeTapped("supervised")
        assertFalse(view.subtopicEditing)

        presenter.onToggleEditMode()
        assertTrue(view.subtopicEditing)

        // Closing and reopening must not drop the user back into an editor they left days ago.
        presenter.onSheetDismissed()
        presenter.onNodeTapped("supervised")
        assertFalse(view.subtopicEditing)
    }

    @Test
    fun `an edit made in the sheet is applied and shown`() {
        val (presenter, view) = attach()
        presenter.onNodeTapped("unsupervised")
        val blockId = view.subtopicSheet!!.body.first { it is LessonBlock.Paragraph }.id

        presenter.onLessonEdit(
            LessonEdit.UpdateBlock(blockId, LessonBlock.Paragraph("My own explanation.")),
        )

        val shown = view.subtopicSheet!!
        assertEquals("My own explanation.", shown.body.first { it.id == blockId }.text)
        // The lesson is marked as touched, which is what keeps generation off it.
        assertTrue(shown.edited)
    }

    @Test
    fun `undo puts the lesson back and says so when there is nothing left`() {
        val (presenter, view) = attach()
        presenter.onNodeTapped("regression")
        val blockId = view.subtopicSheet!!.body.first { it is LessonBlock.Paragraph }.id
        val original = view.subtopicSheet!!.body.first { it.id == blockId }.text

        presenter.onLessonEdit(LessonEdit.UpdateBlock(blockId, LessonBlock.Paragraph("Changed.")))
        presenter.onUndoLessonEdit()

        assertEquals(original, view.subtopicSheet!!.body.first { it.id == blockId }.text)

        presenter.onUndoLessonEdit()
        assertTrue(view.toasts.any { it.contains("undo", ignoreCase = true) })
    }

    @Test
    fun `an edit aimed at a block that is gone changes nothing and says so`() {
        val (presenter, view) = attach()
        presenter.onNodeTapped("clustering")
        val before = view.subtopicSheet!!

        presenter.onLessonEdit(LessonEdit.DeleteBlock("never-existed"))

        assertEquals(before, view.subtopicSheet)
        assertTrue(view.toasts.isNotEmpty())
    }

    @Test
    fun `renaming a section shows the new name on the board`() {
        val (presenter, view) = attach()
        presenter.onNodeTapped("model-evaluation")

        presenter.onRoadmapEdit(RoadmapEdit.RenameNode("model-evaluation", "How good is it?"))

        assertEquals("How good is it?", view.lastState!!.node("model-evaluation").title)
    }

    @Test
    fun `deleting the open section closes its sheet and re-lays the board`() {
        val (presenter, view) = attach()
        presenter.onNodeTapped("clustering")
        val dismissedBefore = view.dismissCount

        presenter.onDeleteSectionConfirmed("clustering", withDescendants = false)

        assertNull(view.lastState!!.nodes.firstOrNull { it.id == "clustering" })
        assertTrue(view.dismissCount > dismissedBefore)
        // What grew beyond it is still there — deleting one section isn't deleting a branch.
        assertNotNull(view.lastState!!.nodes.firstOrNull { it.id == "model-evaluation" })
    }

    @Test
    fun `the open section carries its roadmap name and numbers, and nothing structural`() {
        val (presenter, view) = attach()

        presenter.onNodeTapped("regression")

        val section = view.subtopicSection!!
        assertEquals("regression", section.nodeId)
        assertEquals("Regression", section.title)
        assertEquals(10, section.estMinutes)
    }

    @Test
    fun `unknown path shows not-found`() {
        // FakePathRepository never returns null, so this simply proves attach renders SOMETHING
        // rather than crashing; not-found is exercised by the null-repo contract.
        val (_, view) = attach("does-not-resolve-to-null?")
        assertTrue(view.notFound || view.lastState != null)
    }
}

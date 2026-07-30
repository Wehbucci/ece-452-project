package com.example.grasp.ui.feature.path

import android.util.Log
import com.example.grasp.core.edit.LessonEdit
import com.example.grasp.core.edit.RoadmapEdit
import com.example.grasp.core.layout.layoutBoard
import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.model.TreeNode
import com.example.grasp.data.repository.FirebasePathRepository
import com.example.grasp.data.repository.PathRepository
import com.example.grasp.ui.feature.subtopic.SectionShape
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Logic for the gamified Learner roadmap. Loads [pathId] on attach and thereafter owns the
 * whole game state: which nodes are complete, the derived per-node visual state, XP → level,
 * and branch insertion.
 */
class PathPresenter(
    private val pathId: String,
    private val repo: PathRepository = FirebasePathRepository(),
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : BasePresenter<PathContract.View>(), PathContract.Presenter {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    /** Path display title, set once the path loads. */
    private var title: String = ""

    /** The live graph. Mutable only via branch insertion; node completion lives in [completed]. */
    private var nodes: List<TreeNode> = emptyList()

    /** Source of truth for progress: the set of completed (non-branch) node ids. */
    private val completed: MutableSet<String> = mutableSetOf()

    /** Node whose lesson is being loaded into the sheet — a late result for anything else is stale. */
    private var openingNodeId: String? = null

    /** Node the open branch sheet will grow from — an affordance, or any lesson on the board. */
    private var pendingBranchId: String? = null

    /** The lesson the sheet is showing, kept so an edit has something to apply itself to. */
    private var openLesson: Subtopic? = null

    /** Whether that lesson is open for editing (FR4.5). Reset every time the sheet opens. */
    private var editing = false

    /** Whether the open lesson has a stored earlier version to go back to. */
    private var canUndo = false

    /** Guards against a second "generate" tap while the AI is still building the first branch. */
    private var growing = false

    /** Whether the board is resting, showing its edit menu, or being used to pick a section. */
    private var boardMode = BoardMode.BROWSING

    /** The section picked to move, while the board is asking where it should go. */
    private var movingId: String? = null

    override fun onViewAttached() {
        scope.launch {
            val path = repo.learningPath(pathId)
            if (path == null) {
                view?.showNotFound()
                return@launch
            }
            title = path.title
            // Roadmaps saved before this flow existed carry standing "Branch out" placeholder nodes.
            // Growing the path starts from a real lesson now, so the board shows lessons only.
            nodes = path.nodes.filterNot { it.isBranchOut }
            completed.clear()
            completed += path.nodes.filter { it.completed && !it.isBranchOut }.map { it.id }
            emit()
        }
    }

    override fun detach() {
        scope.cancel()
        super.detach()
    }

    // ── User events ───────────────────────────────────────────────────────────────────────

    override fun onNodeTapped(nodeId: String) {
        val node = nodes.firstOrNull { it.id == nodeId } ?: return
        // In a picking mode the whole board is the picker, and a node the mode can't act on is
        // inert — the board has already shown it dimmed, so the tap simply doesn't land.
        if (boardMode.picking && !isPickable(nodeId)) return

        when (boardMode) {
            // The tapped lesson is what the new branch grows out of, rather than opening.
            BoardMode.PICK_ADD_PARENT -> {
                leaveBoardEdit() // drop the banner first, so the board behind the sheet is normal
                openBranchSheet(node.id)
            }

            BoardMode.PICK_DELETE -> {
                leaveBoardEdit()
                // Asked, never done on the tap: this is the one board gesture that destroys work.
                view?.confirmDeleteSection(node.id, node.title, node.children.isNotEmpty())
            }

            // Half of a move. The board stays a picker, now asking the second question.
            BoardMode.PICK_MOVE -> {
                movingId = node.id
                boardMode = BoardMode.PICK_MOVE_PARENT
                emit()
            }

            BoardMode.PICK_MOVE_PARENT -> {
                val moving = movingId ?: return
                leaveBoardEdit()
                applyRoadmapEdit(RoadmapEdit.ReparentNode(moving, node.id), "↕️ Section moved")
            }

            // Every lesson is open: the roadmap suggests an order, it doesn't enforce one.
            BoardMode.BROWSING, BoardMode.MENU -> {
                if (boardMode == BoardMode.MENU) leaveBoardEdit()
                openSubtopic(node)
            }
        }
    }

    override fun onBranchFromNode(nodeId: String) {
        // Any lesson can sprout a detour — this is the same thing the picker does, reached from
        // inside a lesson the user already has open.
        val node = nodes.firstOrNull { it.id == nodeId } ?: return
        openBranchSheet(node.id)
    }

    override fun onEditRoadmapRequested() {
        boardMode = if (boardMode == BoardMode.MENU) BoardMode.BROWSING else BoardMode.MENU
        movingId = null
        emit()
    }

    override fun onAddSectionChosen() = enterPicker(BoardMode.PICK_ADD_PARENT)

    override fun onMoveSectionChosen() = enterPicker(BoardMode.PICK_MOVE)

    override fun onDeleteSectionChosen() = enterPicker(BoardMode.PICK_DELETE)

    override fun onBoardEditCancelled() {
        if (boardMode == BoardMode.BROWSING) return
        leaveBoardEdit()
    }

    override fun onDeleteSectionConfirmed(nodeId: String, withDescendants: Boolean) {
        applyRoadmapEdit(RoadmapEdit.DeleteNode(nodeId, withDescendants), "🗑️ Section deleted")
    }

    private fun enterPicker(mode: BoardMode) {
        boardMode = mode
        movingId = null
        emit()
    }

    /** Back to a plain board, whatever mode it was in. */
    private fun leaveBoardEdit() {
        boardMode = BoardMode.BROWSING
        movingId = null
        emit()
    }

    /**
     * Opens the detail sheet for [node]. A node's lesson is written the first time it is opened,
     * so the sheet appears immediately in a loading state instead of freezing the tap for the
     * length of an AI call (NFR 1.2).
     */
    private fun openSubtopic(node: TreeNode) {
        openingNodeId = node.id
        // Nearly always a fetch of an already-written lesson. It only says "writing" for a node the
        // up-front pass failed on, which is a real multi-second wait and worth naming.
        view?.showSubtopicLoading(node.title, generating = !node.contentReady)
        scope.launch {
            val subtopic = repo.subtopic(pathId, node.id)
            // The user may have dismissed the sheet, or opened another node, while we waited.
            if (openingNodeId != node.id) return@launch
            if (subtopic == null) {
                view?.dismissSheet()
                view?.showToast("⚠️ Couldn't open that lesson — check your connection")
                return@launch
            }
            openLesson = subtopic
            // Always opens in reading mode; edit mode is asked for, never inherited.
            editing = false
            canUndo = repo.lessonRevisions(pathId, node.id).isNotEmpty()
            showOpenLesson()
            // Generation is also where a node's reading time comes from — keep the board honest.
            if (subtopic.estMinutes != node.estMinutes) {
                nodes = nodes.map { if (it.id == node.id) it.copy(estMinutes = subtopic.estMinutes) else it }
                emit()
            }
        }
    }

    /** Opens the "grow your path" sheet anchored at [anchorId] and fetches its starter chips. */
    private fun openBranchSheet(anchorId: String) {
        val anchor = nodes.firstOrNull { it.id == anchorId } ?: return
        pendingBranchId = anchorId
        openingNodeId = null
        view?.showBranchSheet(anchor.title)
        scope.launch {
            val ideas = repo.branchSuggestions(pathId, anchorId)
            // Ignore suggestions that arrive after the user moved on to a different anchor.
            if (pendingBranchId == anchorId) view?.showBranchSuggestions(ideas)
        }
    }

    override fun onMarkComplete(nodeId: String) {
        val node = nodes.firstOrNull { it.id == nodeId } ?: return
        if (node.isBranchOut || nodeId in completed) return

        val previousXp = xp()
        val previousCurrent = currentId()

        completed += nodeId

        // Persist change to cloud
        scope.launch {
            repo.updateNodeCompletion(pathId, nodeId, true)
        }

        val newXp = xp()
        val newCurrent = currentId()

        // Re-render the board first so the node flips to done and the XP bar animates.
        emit()
        view?.dismissSheet()
        view?.showConfetti()

        // Move the "you are here" marker on: spotlight whichever node is now current.
        if (newCurrent != null && newCurrent != previousCurrent) {
            view?.playAdvance(newCurrent)
        }
        // Crossed a level boundary (every [XP_PER_LEVEL] XP).
        val newLevel = levelFor(newXp)
        if (newLevel > levelFor(previousXp)) {
            view?.showLevelUp(newLevel)
        }
    }

    override fun onGenerateBranch(name: String) {
        val branchId = pendingBranchId ?: return
        if (growing) return
        growing = true
        view?.showBranchGenerating(true)

        scope.launch {
            // The repository generates the branch AND persists it, so what pops in here is what
            // will still be on the board after a reload.
            val grown = repo.growBranch(pathId, branchId, name)
            growing = false
            view?.showBranchGenerating(false)
            if (grown.isEmpty()) {
                view?.showToast("⚠️ Couldn't grow that branch — try again")
                return@launch
            }
            spliceBranch(branchId, grown)
            pendingBranchId = null

            emit()
            view?.dismissSheet()
            view?.playPopIn(grown.first().id)
            view?.showToast("🌱 New branch added")
        }
    }

    /**
     * Grafts [grown] onto the board at [anchorId], mirroring what the repository just persisted:
     * the anchor keeps everything it already led to and gains one more child.
     */
    private fun spliceBranch(anchorId: String, grown: List<TreeNode>) {
        val firstId = grown.first().id
        nodes = nodes.map { node ->
            if (node.id == anchorId) node.copy(children = node.children + firstId) else node
        } + grown
    }

    override fun onAskAi(nodeId: String) {
        val node = nodes.firstOrNull { it.id == nodeId }
        view?.openChat(node?.title ?: "your material", pathId, nodeId, blockId = "")
    }

    override fun onAskAboutRoadmap() {
        // No node id: that absence is exactly what tells the chat it is scoped to the whole path.
        view?.openChat(title.ifEmpty { "your roadmap" }, pathId, nodeId = "", blockId = "")
    }

    override fun onAskAboutBlock(nodeId: String, blockText: String, blockId: String) {
        // Same convention as the full subtopic screen: the block's opening words name the chat.
        view?.openChat(blockText.take(60), pathId, nodeId, blockId)
    }

    override fun onSheetDismissed() {
        // Drop the pending targets so a late generation result can't reopen a closed sheet.
        openingNodeId = null
        pendingBranchId = null
        // Closing the lesson closes the editor with it: reopening a node should always land the
        // user in a clean reading view, never back in a mode they left days ago (NFR 2.2).
        editing = false
        openLesson = null
    }

    override fun onToggleEditMode() {
        editing = !editing
        showOpenLesson()
    }

    /**
     * Applies one change and shows the result.
     *
     * The repository is the one that applies it, so a change made by hand goes through exactly the
     * same code an accepted AI proposal will (FR5.4) — including saving the version before it.
     */
    override fun onLessonEdit(edit: LessonEdit) {
        val lesson = openLesson ?: return
        scope.launch {
            val updated = repo.editLesson(pathId, lesson.nodeId, listOf(edit))
            if (updated == null) {
                // The only way here is an edit aimed at something no longer in the lesson.
                view?.showToast("⚠️ Couldn't make that change")
                return@launch
            }
            openLesson = updated
            canUndo = true
            showOpenLesson()
        }
    }

    override fun onUndoLessonEdit() {
        val lesson = openLesson ?: return
        scope.launch {
            val restored = repo.undoLastLessonEdit(pathId, lesson.nodeId)
            if (restored == null) {
                view?.showToast("Nothing left to undo")
                canUndo = false
                showOpenLesson()
                return@launch
            }
            openLesson = restored
            canUndo = repo.lessonRevisions(pathId, lesson.nodeId).isNotEmpty()
            showOpenLesson()
        }
    }

    /**
     * Applies one change to the roadmap's shape and re-lays the board out.
     *
     * The board derives every position from the tree on each render, so there is nothing to move
     * here — re-emitting the state IS the re-layout.
     */
    override fun onRoadmapEdit(edit: RoadmapEdit) = applyRoadmapEdit(edit)

    /** [onRoadmapEdit], plus a word about what happened for the changes made out on the board. */
    private fun applyRoadmapEdit(edit: RoadmapEdit, announce: String? = null) {
        scope.launch {
            val updated = repo.editRoadmap(pathId, listOf(edit))
            if (updated == null) {
                view?.showToast("⚠️ Couldn't change the roadmap")
                return@launch
            }
            nodes = updated.nodes
            completed.retainAll(nodes.mapTo(mutableSetOf()) { it.id })
            emit()
            // A section the user just deleted has no sheet to go back to.
            if (nodes.none { it.id == openLesson?.nodeId }) {
                openLesson = null
                editing = false
                view?.dismissSheet()
            } else {
                showOpenLesson()
            }
            announce?.let { view?.showToast(it) }
        }
    }

    /** Re-renders the open sheet from the lesson and mode the presenter currently holds. */
    private fun showOpenLesson() {
        val lesson = openLesson ?: return
        view?.showSubtopicSheet(
            subtopic = lesson,
            completed = lesson.nodeId in completed,
            editing = editing,
            canUndo = canUndo,
            section = sectionShapeFor(lesson.nodeId),
        )
    }

    /**
     * The facts about the open node that live on the roadmap rather than in its lesson.
     *
     * Names and numbers only. Adding, moving and deleting a section are done on the board, where
     * the shape being changed is actually visible, not from inside one lesson looking out at it.
     */
    private fun sectionShapeFor(nodeId: String): SectionShape? =
        nodes.firstOrNull { it.id == nodeId }?.let {
            SectionShape(nodeId = it.id, title = it.title, estMinutes = it.estMinutes, tier = it.tier)
        }

    // ── Picking rules ───────────────────────────────────────────────────────────────────────

    /**
     * Whether [nodeId] can be tapped in the mode the board is currently in.
     *
     * These mirror what `RoadmapEdit.applyEdit` would refuse, deliberately: the operations already
     * reject a move that would cut a loop off the tree, and this is the same rule stated early
     * enough for the board to grey the node out rather than swallow the tap.
     */
    private fun isPickable(nodeId: String): Boolean = when (boardMode) {
        // Any lesson can sprout a detour, including the root.
        BoardMode.PICK_ADD_PARENT -> true

        // The root is the one nothing hangs off — the roadmap itself, not a section of it, so it
        // can neither be cut out nor rehung somewhere else.
        BoardMode.PICK_DELETE, BoardMode.PICK_MOVE -> !isRoot(nodeId)

        // Anywhere but itself and where it already is. A section may be moved down its own branch:
        // `RoadmapEdit.ReparentNode` takes it out of the tree before putting it back, so the
        // branch simply closes up behind it rather than tying a loop.
        BoardMode.PICK_MOVE_PARENT -> {
            val moving = movingId
            when {
                moving == null -> false
                nodeId == moving -> false
                // Already its parent: offering a move that changes nothing reads as a broken one.
                moving in (nodes.firstOrNull { it.id == nodeId }?.children ?: emptyList()) -> false
                else -> true
            }
        }

        BoardMode.BROWSING, BoardMode.MENU -> true
    }

    /**
     * Derived from `children`, never read off the node: `parentId` is the inverse of that relation
     * and is only filled in on the paths that happen to store it.
     */
    private fun isRoot(nodeId: String) = nodes.none { nodeId in it.children }

    // ── Derivation (pure functions of `nodes` + `completed`) ────────────────────────────────

    /** parent-id → list of its parent ids (inverse of `children`). */
    private fun parentsMap(): Map<String, List<String>> {
        val map = HashMap<String, MutableList<String>>()
        nodes.forEach { n -> n.children.forEach { c -> map.getOrPut(c) { mutableListOf() }.add(n.id) } }
        return map
    }

    /**
     * The current node = the first non-branch, not-yet-complete node in order.
     *
     * Purely a "you are here" marker for where the roadmap picks up — it gates nothing, since any
     * node can be opened whenever the user likes. Depth-first ordering is what makes it land on the
     * main line rather than in a detour.
     */
    private fun currentId(): String? = nodes.firstOrNull { !it.isBranchOut && it.id !in completed }?.id

    private fun stateOf(node: TreeNode, current: String? = currentId()): PathNodeState = when {
        node.isBranchOut -> PathNodeState.BRANCH
        node.id in completed -> PathNodeState.DONE
        node.id == current -> PathNodeState.CURRENT
        else -> PathNodeState.OPEN
    }

    private fun xp(): Int = completed.count { id -> nodes.any { it.id == id && !it.isBranchOut } } * XP_PER_LESSON
    private fun levelFor(xp: Int): Int = xp / XP_PER_LEVEL + 1

    /**
     * Assemble the immutable snapshot the View renders.
     *
     * The board's geometry is re-derived here on every frame by [layoutBoard] rather than stored on
     * the nodes, which is what lets a freshly grown branch slot in and its neighbours shift aside
     * to make room without anybody having to pick coordinates.
     */
    private fun emit() {
        val parents = parentsMap()
        val layout = layoutBoard(nodes)
        val current = currentId()
        val xp = xp()

        val nodeUis = nodes.map { n ->
            PathNodeUi(
                id = n.id,
                title = n.title,
                estMinutes = n.estMinutes,
                state = stateOf(n, current),
                column = layout.columns.getValue(n.id),
                row = layout.rows.getValue(n.id),
                parentIds = parents[n.id].orEmpty(),
                pickable = !boardMode.picking || isPickable(n.id),
            )
        }
        // One pill per tier, anchored at the tier's first (shallowest) row — several nodes may
        // declare the same tier. Only hand-authored roadmaps set one; nothing generated or grown
        // does, so in practice the board carries no pills.
        val regions = nodes
            .filter { !it.tier.isNullOrBlank() }
            .map { RegionUi(it.tier!!, layout.rows.getValue(it.id)) }
            .groupBy { it.label }
            .map { (_, group) -> group.minBy { it.row } }
            .sortedBy { it.row }

        view?.showPath(
            PathUiState(
                title = title,
                nodes = nodeUis,
                regions = regions,
                boardMode = boardMode,
                movingTitle = movingId?.let { id -> nodes.firstOrNull { it.id == id }?.title },
                masteredCount = completed.size,
                totalLessons = nodes.count { !it.isBranchOut },
                streak = STREAK,
                level = levelFor(xp),
                xpInLevel = xp % XP_PER_LEVEL,
                xpPerLevel = XP_PER_LEVEL,
                xpFraction = (xp % XP_PER_LEVEL).toFloat() / XP_PER_LEVEL,
                rowCount = (layout.rows.values.maxOrNull() ?: 0) + 1,
                columnSpan = layout.columnSpan,
            ),
        )
    }

    companion object {
        /** XP granted per lesson completed (README §Interactions: "+40 XP"). */
        const val XP_PER_LESSON = 40

        /** XP needed to advance a level. Level = xp / this + 1 (README §Interactions). */
        const val XP_PER_LEVEL = 200

        /** Demo streak count shown in the HUD (real value would come from UserRepository). */
        const val STREAK = 6
    }
}

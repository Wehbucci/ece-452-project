package com.example.grasp.ui.feature.path

import android.util.Log
import com.example.grasp.GraspApp
import com.example.grasp.core.util.NetworkMonitor
import com.example.grasp.data.model.DownloadState
import com.example.grasp.data.model.Mode
import com.example.grasp.data.model.Subtopic
import com.example.grasp.core.edit.LessonEdit
import com.example.grasp.core.edit.RoadmapEdit
import com.example.grasp.core.layout.layoutBoard
import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.core.progress.StudyStreak
import com.example.grasp.core.progress.Xp
import com.example.grasp.core.progress.asOf
import com.example.grasp.core.progress.recordingStudy
import com.example.grasp.core.progress.todayEpochDay
import com.example.grasp.data.model.TreeNode
import com.example.grasp.data.repository.FirebasePathRepository
import com.example.grasp.data.repository.FirebaseUserRepository
import com.example.grasp.data.repository.PathRepository
import com.example.grasp.data.repository.UserRepository
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
    private val userRepo: UserRepository = FirebaseUserRepository(),
) : BasePresenter<PathContract.View>(), PathContract.Presenter {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    /** Path display title, set once the path loads. */
    private var title: String = ""

    /** The live graph. Mutable only via branch insertion; node completion lives in [completed]. */
    private var nodes: List<TreeNode> = emptyList()

    /** Source of truth for progress: the set of completed (non-branch) node ids. */
    private val completed: MutableSet<String> = mutableSetOf()

    /**
     * Lessons finished on every OTHER saved path and guide.
     *
     * The HUD's level is the ACCOUNT's level (see `core.progress.Xp`), so it has to know about work
     * done elsewhere — otherwise opening a second roadmap reads as having been reset to zero. Held
     * as "everything except this path" and added to the live [completed] set, so marking a lesson
     * complete moves the bar immediately instead of waiting on a re-read of the whole library.
     */
    private var lessonsMasteredElsewhere = 0

    /**
     * How many days in a row the user has studied — the flame in the HUD.
     *
     * The record as STORED. How long it is today is asked of it on every render, because a streak
     * that reached nine days last week is still a nine-day record and is no longer a live streak.
     */
    private var streak: StudyStreak = StudyStreak.None

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

    private var downloadState = DownloadState.NONE
    private var isGenerating = false

    /**
     * The lesson the tutor was opened FROM, so closing the tutor goes back to it rather than to
     * the board.
     *
     * The detail sheet has to close before the chat opens (a `ModalBottomSheet` is its own window
     * and would sit over the overlay), so without this the user is dropped two levels back from
     * where they were reading. Null for a chat about the roadmap itself, which was opened from the
     * board and so belongs back on it.
     */
    private var chatOpenedFromNodeId: String? = null

    override fun onViewAttached() {
        scope.launch {
            load(firstLoad = true)
            tidyTitles()
        }
    }

    /**
     * Shortens any section title too long to read under a node, then re-renders (FR2.1).
     *
     * After the board is already on screen, never before it: this may cost an AI round-trip, and
     * holding the roadmap back behind one so that a few labels arrive tidier would be the wrong
     * trade. Roadmaps generated since titles were constrained have nothing to shorten, so for them
     * the repository returns immediately without calling anything.
     */
    private suspend fun tidyTitles() {
        val tidied = repo.shortenNodeTitles(pathId) ?: return
        // The user may have grown or deleted a section while we were waiting; keep the board's own
        // shape and take only the names.
        val titles = tidied.nodes.associate { it.id to it.title }
        nodes = nodes.map { node -> titles[node.id]?.let { node.copy(title = it) } ?: node }
        emit()
        // The open sheet is showing one of those titles in its header.
        showOpenLesson()
    }

    /**
     * Read the roadmap and render it.
     *
     * [firstLoad] is the difference between "there is no such roadmap" and "we couldn't reach it
     * just now": on the way in, nothing found means nothing to show; on a re-read there is already
     * a board on screen, and replacing it with an error because one fetch missed would be worse
     * than leaving it as it was.
     */
    private suspend fun load(firstLoad: Boolean) {
        val path = repo.learningPath(pathId)
        if (path == null) {
            if (firstLoad) view?.showNotFound()
            return
        }
        title = path.title
        downloadState = path.downloadState

        if (path.nodes.isEmpty() && !isGenerating) {
            isGenerating = true
            emit() // Show GeneratingState via PathScreen
            
            val generated = repo.createTopic(path.title, Mode.LEARNER)
            isGenerating = false
            if (generated != null) {
                load(firstLoad = false)
                return
            }
        }

        // Roadmaps saved before this flow existed carry standing "Branch out" placeholder nodes.
        // Growing the path starts from a real lesson now, so the board shows lessons only.
        nodes = path.nodes.filterNot { it.isBranchOut }
        completed.clear()
        completed += path.nodes.filter { it.completed && !it.isBranchOut }.map { it.id }
        // The account total, minus what this path contributes to it — the rest is added back from
        // the live set above. A failed read leaves this at zero rather than going negative, which
        // would show the user LESS XP than they have earned on this roadmap alone.
        lessonsMasteredElsewhere =
            (repo.totalLessonsMastered() - completed.size).coerceAtLeast(0)
        streak = userRepo.studyStreak()
        emit()
    }

    /**
     * The chat overlay closed. The tutor can reshape the roadmap from in there with the user's
     * say-so (FR5.4), so the board is read again — the overlay never detaches this presenter, and
     * a section that was added or renamed behind it would otherwise only appear on the next visit.
     *
     * Then the lesson the question was asked FROM comes back. Asking about a paragraph and being
     * returned to the tree reads as having lost your place; the material is what the user was in
     * the middle of, and the answer only makes sense next to it. It is re-read rather than
     * re-shown from memory, for the same reason the board is: the tutor may have just rewritten it.
     */
    override fun onChatClosed() {
        val resumeId = chatOpenedFromNodeId
        chatOpenedFromNodeId = null
        scope.launch {
            load(firstLoad = false)
            // A lesson the tutor deleted while the chat was open has nothing to go back to.
            nodes.firstOrNull { it.id == resumeId }?.let { openSubtopic(it) }
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

            BoardMode.BROWSING, BoardMode.MENU -> {
                if (boardMode == BoardMode.MENU) leaveBoardEdit()
                // A locked lesson says what is in the way rather than nothing at all. A tap that
                // does nothing reads as a broken button; a tap that names the one lesson standing
                // between the user and this one is a direction to go in.
                if (node.id !in completed && !isUnlocked(node.id)) {
                    view?.showToast(lockedMessage(node.id))
                    return
                }
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

        // Today counts as studied. Applied to the local record FIRST so the flame moves with the
        // rest of the celebration rather than a round-trip later; the repository applies the same
        // pure rule to the stored record, so the two cannot disagree.
        streak = streak.recordingStudy(todayEpochDay())

        // Persist change to cloud
        scope.launch {
            repo.updateNodeCompletion(pathId, nodeId, true)
            userRepo.recordStudyToday()
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
        // Crossed a level boundary (every [Xp.PER_LEVEL] XP).
        val newLevel = Xp.levelFor(newXp)
        if (newLevel > Xp.levelFor(previousXp)) {
            view?.showLevelUp(newLevel)
        }
    }

    override fun onGenerateBranch(name: String) {
        if (!NetworkMonitor(GraspApp.context).isOnline()) {
            view?.showToast("⚠️ Generation requires an internet connection")
            return
        }
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
        chatOpenedFromNodeId = nodeId
        view?.openChat(node?.title ?: "your material", pathId, nodeId, blockId = "")
    }

    override fun onAskAboutRoadmap() {
        // Asked from the board, so the board is where closing it belongs.
        chatOpenedFromNodeId = null
        // No node id: that absence is exactly what tells the chat it is scoped to the whole path.
        view?.openChat(title.ifEmpty { "your roadmap" }, pathId, nodeId = "", blockId = "")
    }

    override fun onAskAboutBlock(nodeId: String, blockText: String, blockId: String) {
        chatOpenedFromNodeId = nodeId
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
     * Whether [nodeId] can be opened yet: everything feeding into it is finished.
     *
     * The roadmap is a prerequisite graph — a connector means "this builds on that" — so a lesson
     * is available exactly when its parents are done. The root has no parents and is therefore
     * always open, which guarantees every roadmap has somewhere to start.
     *
     * A converge (two parents naming the same child) needs BOTH, which is the only reading that
     * matches what the board draws: two lines arriving into one lesson because it depends on both.
     *
     * Note this is about opening a lesson, not about editing the roadmap — adding, moving and
     * deleting sections stay available everywhere (see [isPickable]). Locking is a statement about
     * what the user is ready to read, never about what they are allowed to change.
     */
    private fun isUnlocked(nodeId: String, parents: Map<String, List<String>> = parentsMap()): Boolean =
        parents[nodeId].orEmpty().all { it in completed }

    /**
     * The current node = the first not-yet-complete node that is actually open.
     *
     * The "you are here" marker, and now also the frontier: with the board gated, this is the
     * lesson the roadmap has arrived at. Depth-first ordering is what makes it land on the main
     * line rather than in a detour.
     */
    private fun currentId(): String? {
        val parents = parentsMap()
        return nodes.firstOrNull {
            !it.isBranchOut && it.id !in completed && isUnlocked(it.id, parents)
        }?.id
    }

    private fun stateOf(
        node: TreeNode,
        current: String? = currentId(),
        parents: Map<String, List<String>> = parentsMap(),
    ): PathNodeState = when {
        node.isBranchOut -> PathNodeState.BRANCH
        // Completion outranks locking. A roadmap reshaped after the fact — a section moved under
        // an unfinished one — must never take back a lesson the user has already done.
        node.id in completed -> PathNodeState.DONE
        node.id == current -> PathNodeState.CURRENT
        !isUnlocked(node.id, parents) -> PathNodeState.LOCKED
        else -> PathNodeState.OPEN
    }

    /** What the user has to finish before [nodeId] opens, named so the toast can say it. */
    private fun blockingTitles(nodeId: String): List<String> =
        parentsMap()[nodeId].orEmpty()
            .filter { it !in completed }
            .mapNotNull { id -> nodes.firstOrNull { it.id == id }?.title }

    /**
     * Why a locked lesson wouldn't open, in one line.
     *
     * Names the blocker when there is exactly one, because that is a thing the user can go and do.
     * With two it lists both; beyond that the sentence stops being a direction and becomes a wall
     * of titles, so it just counts them.
     */
    private fun lockedMessage(nodeId: String): String {
        val blockers = blockingTitles(nodeId)
        return when (blockers.size) {
            0 -> "🔒 Finish the sections leading here first"
            1 -> "🔒 Finish “${blockers.single()}” first"
            2 -> "🔒 Finish “${blockers[0]}” and “${blockers[1]}” first"
            else -> "🔒 Finish the ${blockers.size} sections leading here first"
        }
    }

    /** Lessons finished on THIS roadmap — what the "{n} of {m} mastered" line counts. */
    private fun masteredHere(): Int =
        completed.count { id -> nodes.any { it.id == id && !it.isBranchOut } }

    /**
     * The ACCOUNT's XP, not this roadmap's.
     *
     * Which is the whole point: a level that dropped back to 1 every time the user opened a
     * different roadmap made the reward for finishing a lesson look like it had been taken away.
     */
    private fun xp(): Int = Xp.forLessons(lessonsMasteredElsewhere + masteredHere())

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
                state = stateOf(n, current, parents),
                column = layout.columns.getValue(n.id),
                row = layout.rows.getValue(n.id),
                parentIds = parents[n.id].orEmpty(),
                pickable = !boardMode.picking || isPickable(n.id),
            )
        }
        // Bands down the board, from hand-authored tiers where they exist and from the tree's own
        // depth where they don't — which is every generated roadmap. See [regionsFor] for why a
        // roadmap gets a full set of bands or none at all, never a lone one.
        val regions = regionsFor(
            rowByNode = layout.rows,
            tierByNode = nodes
                .filter { !it.tier.isNullOrBlank() }
                .associate { it.id to it.tier!! },
        )

        view?.showPath(
            PathUiState(
                title = title,
                nodes = nodeUis,
                regions = regions,
                boardMode = boardMode,
                movingTitle = movingId?.let { id -> nodes.firstOrNull { it.id == id }?.title },
                // Path-scoped: this line is about the roadmap on screen. The level and XP beside
                // it are not — they are the account's, and deliberately keep counting across every
                // roadmap the user has.
                masteredCount = masteredHere(),
                totalLessons = nodes.count { !it.isBranchOut },
                streak = streak.asOf(todayEpochDay()),
                level = Xp.levelFor(xp),
                xpInLevel = Xp.inLevel(xp),
                xpPerLevel = Xp.PER_LEVEL,
                xpFraction = Xp.fractionOfLevel(xp),
                rowCount = (layout.rows.values.maxOrNull() ?: 0) + 1,
                columnSpan = layout.columnSpan,
                isDownloaded = downloadState == DownloadState.AVAILABLE,
                isDownloading = downloadState == DownloadState.DOWNLOADING || downloadState == DownloadState.PENDING,
                isGenerating = isGenerating,
            ),
        )
    }

    override fun onDownloadPath() {
        if (downloadState != DownloadState.NONE && downloadState != DownloadState.FAILED) return
        downloadState = DownloadState.PENDING
        emit()
        scope.launch {
            val success = repo.downloadTopic(pathId)
            if (success) {
                downloadState = DownloadState.AVAILABLE
                view?.showToast("📥 Roadmap downloaded for offline use")
            } else {
                downloadState = DownloadState.FAILED
                view?.showToast("⚠️ Download failed — check your connection")
            }
            emit()
        }
    }

}

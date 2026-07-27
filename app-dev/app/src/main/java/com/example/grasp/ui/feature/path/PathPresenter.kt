package com.example.grasp.ui.feature.path

import android.util.Log
import com.example.grasp.core.layout.LANE_GRID
import com.example.grasp.core.layout.gridColumnOf
import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.TreeNode
import com.example.grasp.data.repository.FirebasePathRepository
import com.example.grasp.data.repository.PathRepository
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

    /** Guards against a second "generate" tap while the AI is still building the first branch. */
    private var growing = false

    /** True while the board is showing "+" slots and waiting for the user to pick one. */
    private var addMode = false

    override fun onViewAttached() {
        scope.launch {
            val path = repo.learningPath(pathId)
            if (path == null) {
                view?.showNotFound()
                return@launch
            }
            title = path.title
            // Roadmaps saved before add mode existed carry standing "Branch out" placeholder nodes.
            // Growing the path is an add-mode action now, so the board shows lessons only.
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
        // In add mode the whole board is a picker, so tapping a node means "put it here" — the
        // same thing its "+" slot means.
        if (addMode) {
            onAddSlotTapped(nodeId)
            return
        }
        when (stateOf(node)) {
            // Locked: don't navigate — nudge and explain what's blocking it.
            PathNodeState.LOCKED -> {
                view?.shakeNode(nodeId)
                view?.showToast("🔒 Finish ${blockingParentTitle(node)} first")
            }
            // Done / current / open all open the detail sheet.
            else -> openSubtopic(node)
        }
    }

    override fun onBranchFromNode(nodeId: String) {
        // Any lesson can sprout a detour, not just the dashed affordance at the end.
        val node = nodes.firstOrNull { it.id == nodeId } ?: return
        openBranchSheet(node.id)
    }

    override fun onAddNodeRequested() {
        addMode = !addMode
        emit()
    }

    override fun onAddModeCancelled() {
        if (!addMode) return
        addMode = false
        emit()
    }

    override fun onAddSlotTapped(anchorId: String) {
        addMode = false
        emit() // clear the slots first, so the board behind the sheet is back to normal
        openBranchSheet(anchorId)
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
            view?.showSubtopicSheet(subtopic, completed = node.id in completed)
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

        // The frontier moved: unlock + spotlight the node that is now "current".
        if (newCurrent != null && newCurrent != previousCurrent) {
            view?.playUnlock(newCurrent)
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
        view?.openChat(node?.title ?: "your material", pathId, nodeId, blockIndex = -1)
    }

    override fun onAskAboutBlock(nodeId: String, blockText: String, blockIndex: Int) {
        // Same convention as the full subtopic screen: the block's opening words name the chat.
        view?.openChat(blockText.take(60), pathId, nodeId, blockIndex)
    }

    override fun onSheetDismissed() {
        // Drop the pending targets so a late generation result can't reopen a closed sheet.
        openingNodeId = null
        pendingBranchId = null
    }

    // ── Derivation (pure functions of `nodes` + `completed`) ────────────────────────────────

    /** parent-id → list of its parent ids (inverse of `children`). */
    private fun parentsMap(): Map<String, List<String>> {
        val map = HashMap<String, MutableList<String>>()
        nodes.forEach { n -> n.children.forEach { c -> map.getOrPut(c) { mutableListOf() }.add(n.id) } }
        return map
    }

    /** Row of every node = longest path from a root, so a node always sits below its parents. */
    private fun rowsMap(parents: Map<String, List<String>>): Map<String, Int> {
        val cache = HashMap<String, Int>()
        fun depth(id: String): Int = cache.getOrPut(id) {
            val ps = parents[id]
            if (ps.isNullOrEmpty()) 0 else ps.maxOf { depth(it) } + 1
        }
        return nodes.associate { it.id to depth(it.id) }
    }

    /** A node is reachable when every parent is complete (roots are always reachable). */
    private fun isReachable(node: TreeNode, parents: Map<String, List<String>>): Boolean =
        parents[node.id].orEmpty().all { it in completed }

    /** The current node = the first non-branch, not-yet-complete, reachable node in order. */
    private fun currentId(parents: Map<String, List<String>> = parentsMap()): String? =
        nodes.firstOrNull { !it.isBranchOut && it.id !in completed && isReachable(it, parents) }?.id

    private fun stateOf(
        node: TreeNode,
        parents: Map<String, List<String>> = parentsMap(),
        current: String? = currentId(parents),
    ): PathNodeState = when {
        node.isBranchOut -> PathNodeState.BRANCH
        node.id in completed -> PathNodeState.DONE
        node.id == current -> PathNodeState.CURRENT
        isReachable(node, parents) -> PathNodeState.OPEN
        else -> PathNodeState.LOCKED
    }

    /** Title of the first incomplete parent — used in the "Finish X first" locked toast. */
    private fun blockingParentTitle(node: TreeNode): String {
        val parentId = parentsMap()[node.id].orEmpty().firstOrNull { it !in completed }
        return nodes.firstOrNull { it.id == parentId }?.title ?: "the previous lesson"
    }

    private fun xp(): Int = completed.count { id -> nodes.any { it.id == id && !it.isBranchOut } } * XP_PER_LESSON
    private fun levelFor(xp: Int): Int = xp / XP_PER_LEVEL + 1

    /**
     * Exactly one "+" ghost per lesson, marking where that lesson's next section would go.
     *
     * Placed the way the real branch will be, so a ghost previews the result rather than just
     * marking a target:
     *  - a lesson with nothing after it continues STRAIGHT DOWN, in its own lane;
     *  - a lesson that already leads somewhere gets its ghost to the RIGHT of the children it has,
     *    because the board reads left to right.
     *
     * Ghosts only ever move rightward and downward, never back to the left — bouncing side to side
     * to find room is what made a column of them zig-zag. When the row beside the children is
     * full, the ghost drops a row and tries again from beside its own lesson instead, which draws
     * as the dotted wire sweeping down past the children.
     */
    private fun addSlots(rows: Map<String, Int>): List<AddSlotUi> {
        if (!addMode) return emptyList()
        val usedColumns = HashMap<Int, MutableSet<Int>>()
        nodes.forEach { node ->
            usedColumns.getOrPut(rows.getValue(node.id)) { mutableSetOf() } += gridColumnOf(node.lane)
        }
        val byId = nodes.associateBy { it.id }

        return nodes.map { node ->
            val anchorRow = rows.getValue(node.id)
            // A lesson with nothing after it continues in its own column; one that already leads
            // somewhere takes the next column along from its right-most child, so the row stays in
            // left-to-right order.
            val rightmostChild = node.children.mapNotNull { byId[it] }
                .maxOfOrNull { gridColumnOf(it.lane) }
            val wanted = rightmostChild?.plus(1)?.coerceAtMost(LANE_GRID.lastIndex)
                ?: gridColumnOf(node.lane)

            // Try the preferred side of each row first, then the rest of that same row, before
            // dropping to the next one. Staying on the nearest row keeps a ghost next to the
            // lesson it belongs to; the columns are what stop it from wandering.
            val placement = (1..SLOT_ROW_SEARCH).firstNotNullOfOrNull { drop ->
                val row = anchorRow + drop
                val taken = usedColumns.getOrPut(row) { mutableSetOf() }
                val rightward = (wanted..LANE_GRID.lastIndex).asSequence()
                val leftward = (wanted - 1 downTo 0).asSequence()
                (rightward + leftward).firstOrNull { it !in taken }?.let { row to it }
            } ?: (anchorRow + 1 to wanted)

            val (row, column) = placement
            usedColumns.getOrPut(row) { mutableSetOf() } += column
            AddSlotUi(anchorId = node.id, lane = LANE_GRID[column], row = row)
        }
    }

    /** Assemble the immutable snapshot the View renders. */
    private fun emit() {
        val parents = parentsMap()
        val rows = rowsMap(parents)
        val current = currentId(parents)
        val xp = xp()

        val nodeUis = nodes.map { n ->
            PathNodeUi(
                id = n.id,
                title = n.title,
                estMinutes = n.estMinutes,
                state = stateOf(n, parents, current),
                // Snapped to the grid so the board reads as ordered columns. Generated roadmaps
                // already land on these lanes; this only pulls in hand-authored or older ones,
                // and it is what lets the add-mode ghosts share the same three columns.
                lane = LANE_GRID[gridColumnOf(n.lane)],
                row = rows.getValue(n.id),
                parentIds = parents[n.id].orEmpty(),
            )
        }
        // One pill per tier, anchored at the tier's first (shallowest) row — several nodes may
        // declare the same tier (e.g. every grown topic is "YOUR BRANCHES").
        val regions = nodes
            .filter { !it.tier.isNullOrBlank() }
            .map { RegionUi(it.tier!!, rows.getValue(it.id)) }
            .groupBy { it.label }
            .map { (_, group) -> group.minBy { it.row } }
            .sortedBy { it.row }

        val slots = addSlots(rows)

        view?.showPath(
            PathUiState(
                title = title,
                nodes = nodeUis,
                regions = regions,
                addSlots = slots,
                masteredCount = completed.size,
                totalLessons = nodes.count { !it.isBranchOut },
                streak = STREAK,
                level = levelFor(xp),
                xpInLevel = xp % XP_PER_LEVEL,
                xpPerLevel = XP_PER_LEVEL,
                xpFraction = (xp % XP_PER_LEVEL).toFloat() / XP_PER_LEVEL,
                // Slots sit a row below their anchor, so they can extend the canvas past the
                // deepest node.
                rowCount = maxOf(rows.values.maxOrNull() ?: 0, slots.maxOfOrNull { it.row } ?: 0) + 1,
            ),
        )
    }

    companion object {
        /** XP granted per lesson completed (README §Interactions: "+40 XP"). */
        const val XP_PER_LESSON = 40

        /** XP needed to advance a level. Level = xp / this + 1 (README §Interactions). */
        const val XP_PER_LEVEL = 200

        /** How many rows down a "+" ghost may look for a free column before giving up. */
        const val SLOT_ROW_SEARCH = 3

        /** Demo streak count shown in the HUD (real value would come from UserRepository). */
        const val STREAK = 6
    }
}

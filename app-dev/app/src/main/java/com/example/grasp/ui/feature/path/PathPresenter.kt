package com.example.grasp.ui.feature.path

import android.util.Log
import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.TreeNode
import com.example.grasp.data.repository.FirebasePathRepository
import com.example.grasp.data.repository.PathRepository
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
) : BasePresenter<PathContract.View>(), PathContract.Presenter {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Path display title, set once the path loads. */
    private var title: String = ""

    /** The live graph. Mutable only via branch insertion; node completion lives in [completed]. */
    private var nodes: List<TreeNode> = emptyList()

    /** Source of truth for progress: the set of completed (non-branch) node ids. */
    private val completed: MutableSet<String> = mutableSetOf()

    /** Monotonic counter so each generated branch gets unique node ids. */
    private var branchesGrown = 0

    override fun onViewAttached() {
        scope.launch {
            val path = repo.learningPath(pathId)
            if (path == null) {
                view?.showNotFound()
                return@launch
            }
            title = path.title
            nodes = path.nodes
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
        if (node.isBranchOut) {
            view?.showBranchSheet()
            return
        }
        when (stateOf(node)) {
            // Locked: don't navigate — nudge and explain what's blocking it.
            PathNodeState.LOCKED -> {
                view?.shakeNode(nodeId)
                view?.showToast("🔒 Finish ${blockingParentTitle(node)} first")
            }
            // Done / current / open all open the detail sheet.
            else -> {
                val subtopic = repo.subtopic(pathId, nodeId) ?: return
                view?.showSubtopicSheet(subtopic, completed = nodeId in completed)
            }
        }
    }

    override fun onBranchRequested() {
        view?.showBranchSheet()
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
        val branchNode = nodes.firstOrNull { it.isBranchOut } ?: return
        val topicName = name.trim().ifEmpty { "New Branch" }
        branchesGrown++
        val topicId = "grown-$branchesGrown"
        val nextBranchId = "branch-out-$branchesGrown"

        val newTopic = TreeNode(
            id = topicId,
            title = topicName,
            estMinutes = 12,
            children = listOf(nextBranchId),
            lane = branchNode.lane,
            tier = "YOUR BRANCHES",
        )
        val newBranch = TreeNode(
            id = nextBranchId,
            title = "Branch out",
            isBranchOut = true,
            lane = branchNode.lane,
        )

        // The old branch node's slot becomes the new topic; the parent that pointed at the
        // branch now points at the topic; a fresh branch node is appended below it.
        nodes = nodes.map { n ->
            when {
                n.id == branchNode.id -> newTopic
                branchNode.id in n.children ->
                    n.copy(children = n.children.map { if (it == branchNode.id) topicId else it })
                else -> n
            }
        } + newBranch

        emit()
        view?.dismissSheet()
        view?.playPopIn(topicId)
        view?.showToast("🌱 New branch added")
    }

    override fun onAskAi(nodeId: String) {
        val node = nodes.firstOrNull { it.id == nodeId }
        view?.openChat(node?.title ?: "your material", pathId, nodeId)
    }

    override fun onSheetDismissed() {
        // Nothing to persist yet; the View owns sheet visibility. Hook kept for symmetry so a
        // future version can, e.g., record "peeked but didn't complete" analytics.
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
                lane = n.lane,
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

        view?.showPath(
            PathUiState(
                title = title,
                nodes = nodeUis,
                regions = regions,
                masteredCount = completed.size,
                totalLessons = nodes.count { !it.isBranchOut },
                streak = STREAK,
                level = levelFor(xp),
                xpInLevel = xp % XP_PER_LEVEL,
                xpPerLevel = XP_PER_LEVEL,
                xpFraction = (xp % XP_PER_LEVEL).toFloat() / XP_PER_LEVEL,
                rowCount = (rows.values.maxOrNull() ?: 0) + 1,
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

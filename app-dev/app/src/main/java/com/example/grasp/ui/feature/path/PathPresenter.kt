package com.example.grasp.ui.feature.path

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.TreeNode
import com.example.grasp.data.repository.AiTreeGenerator
import com.example.grasp.data.repository.FakePathRepository
import com.example.grasp.data.repository.GeneratedPathCache
import com.example.grasp.data.repository.PathRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PathPresenter(
    private val pathId: String,
    private val repo: PathRepository = FakePathRepository,
    private val generator: AiTreeGenerator = AiTreeGenerator,
) : BasePresenter<PathContract.View>(), PathContract.Presenter {

    private var currentPath: LearningPath? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onViewAttached() {
        val path = GeneratedPathCache.paths[pathId] ?: repo.learningPath(pathId)
        currentPath = path
        if (path == null) view?.showNotFound() else view?.showPath(path)
    }

    override fun detach() {
        scope.cancel()
        super.detach()
    }

    override fun onNodeClicked(node: TreeNode) {
        if (node.isBranchOut) {
            view?.showAddBranchDialog()
            return
        }
        view?.openSubtopic(pathId, node.id)
    }

    override fun onAddBranch(query: String) {
        if (query.isBlank()) return
        val path = currentPath ?: return

        val existingTitles = path.nodes
            .filter { !it.isBranchOut }
            .map { it.title }

        scope.launch {
            try {
                val newNode = generator.generateBranchNode(
                    pathTitle = path.title,
                    existingTitles = existingTitles,
                    branchRequest = query.trim(),
                )
                // Insert the new node just before the branch-out affordance.
                val updatedNodes = path.nodes.toMutableList()
                val branchOutIndex = updatedNodes.indexOfFirst { it.isBranchOut }
                if (branchOutIndex >= 0) {
                    updatedNodes.add(branchOutIndex, newNode)
                } else {
                    updatedNodes += newNode
                }
                val updatedPath = path.copy(nodes = updatedNodes)
                currentPath = updatedPath
                GeneratedPathCache.paths[pathId] = updatedPath
                view?.showPath(updatedPath)
            } catch (e: Exception) {
                // Leave the path as-is; the dialog already closed on the view side.
            }
        }
    }
}

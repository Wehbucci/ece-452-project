package com.example.grasp.ui.feature.path

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.TreeNode
import com.example.grasp.data.repository.FirebasePathRepository
import com.example.grasp.data.repository.PathRepository

/**
 * Logic for the Learner roadmap screen. Loads the path for [pathId] on attach.
 *
 * The id is a constructor dependency (not a method arg) because a presenter instance is
 * scoped to one path — the screen creates it with the navigation argument.
 *
 * @param pathId which roadmap to load
 * @param repo data source — defaults to the in-memory fake; injectable for tests/previews.
 */
class PathPresenter(
    private val pathId: String,
    private val repo: PathRepository = FirebasePathRepository(),
) : BasePresenter<PathContract.View>(), PathContract.Presenter {

    override fun onViewAttached() {
        val path = repo.learningPath(pathId)
        if (path == null) view?.showNotFound() else view?.showPath(path)
    }

    override fun onNodeClicked(node: TreeNode) {
        // Branch-out nodes are an "add a branch" affordance; for the skeleton we don't open
        // content for them (real flow: trigger AI expansion of a new branch).
        if (node.isBranchOut) return
        view?.openSubtopic(pathId, node.id)
    }
}

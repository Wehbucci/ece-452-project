package com.example.grasp.ui.feature.path

import com.example.grasp.core.mvp.MvpPresenter
import com.example.grasp.core.mvp.MvpView
import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.TreeNode

/**
 * MVP contract for the Learner roadmap screen.
 *
 * The same screen shows the roadmap as either a LIST or a visual TREE, that toggle is pure
 * view state and stays in the Composable. The Presenter only loads the path and handles
 * node taps.
 */
interface PathContract {

    interface View : MvpView {
        /** Render the loaded roadmap. */
        fun showPath(path: LearningPath)

        /** The id didn't resolve to a path (show an error state, never crash). */
        fun showNotFound()

        /** Navigate to a node's detail/content. */
        fun openSubtopic(pathId: String, nodeId: String)

        /** Show the "add a branch" input dialog. */
        fun showAddBranchDialog()
    }

    interface Presenter : MvpPresenter<View> {
        /** User tapped a node — open it or trigger the branch dialog for branch-out nodes. */
        fun onNodeClicked(node: TreeNode)

        /** User submitted a branch request from the dialog. */
        fun onAddBranch(query: String)
    }
}

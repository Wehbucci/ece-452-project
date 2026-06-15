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
    }

    interface Presenter : MvpPresenter<View> {
        /** User tapped a node — open it (branch-out nodes are handled in the View as a hint). */
        fun onNodeClicked(node: TreeNode)
    }
}

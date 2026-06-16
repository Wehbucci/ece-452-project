package com.example.grasp.ui.feature.subtopic

import com.example.grasp.core.mvp.MvpPresenter
import com.example.grasp.core.mvp.MvpView
import com.example.grasp.data.model.ResourceLink
import com.example.grasp.data.model.Subtopic

/**
 * MVP contract for the Subtopic detail screen.
 *
 * Shows the resolved content for one node (summary, why it matters, body blocks, resources)
 * and lets the user mark it complete or open the AI chat about it.
 */
interface SubtopicContract {

    interface View : MvpView {
        /** Render the loaded content. */
        fun showSubtopic(subtopic: Subtopic)

        /** Couldn't resolve the content (graceful error). */
        fun showNotFound()

        /** Reflect the latest completion state on the "Mark complete" control. */
        fun showCompleted(completed: Boolean)

        /** Open the multi-modal AI chat scoped to [context] (e.g. the subtopic or a block). */
        fun openChat(context: String)

        /** Open an external resource link. */
        fun openResource(url: String)
    }

    interface Presenter : MvpPresenter<View> {
        /** Toggle the subtopic's completion. */
        fun onToggleComplete()

        /** Ask the AI about the whole subtopic. */
        fun onAskAi()

        /** Ask the AI about a specific content block the user tapped. */
        fun onBlockClicked(blockText: String)

        /** Open a "Dive deeper" resource. */
        fun onResourceClicked(link: ResourceLink)
    }
}

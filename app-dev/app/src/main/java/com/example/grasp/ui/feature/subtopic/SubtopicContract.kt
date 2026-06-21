package com.example.grasp.ui.feature.subtopic

import com.example.grasp.core.mvp.MvpPresenter
import com.example.grasp.core.mvp.MvpView
import com.example.grasp.data.model.ResourceLink
import com.example.grasp.data.model.Subtopic

interface SubtopicContract {

    interface View : MvpView {
        fun showSubtopic(subtopic: Subtopic)
        fun showNotFound()
        fun showCompleted(completed: Boolean)
        /** Open the AI chat. [blockIndex] is -1 for the subtopic-level FAB chat. */
        fun openChat(context: String, pathId: String, nodeId: String, blockIndex: Int = -1)
        fun openResource(url: String)
        /** Drive the history indicators: FAB badge and per-block indicators. */
        fun showChatIndicators(hasFabHistory: Boolean, blockHistoryIndices: Set<Int>)
    }

    interface Presenter : MvpPresenter<View> {
        fun onToggleComplete()
        fun onAskAi()
        /** [blockIndex] is the position of the tapped block in the body list. */
        fun onBlockClicked(blockText: String, blockIndex: Int)
        fun onResourceClicked(link: ResourceLink)
    }
}

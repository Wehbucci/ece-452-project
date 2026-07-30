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
        /** Open the AI chat. [blockId] is empty for the subtopic-level FAB chat. */
        fun openChat(context: String, pathId: String, nodeId: String, blockId: String = "")
        fun openResource(url: String)
        /** Drive the history indicators: FAB badge and per-block indicators. */
        fun showChatIndicators(hasFabHistory: Boolean, blockHistoryIds: Set<String>)
    }

    interface Presenter : MvpPresenter<View> {
        fun onToggleComplete()
        fun onAskAi()
        /** [blockId] is the tapped block's stable id — not its position, which editing changes. */
        fun onBlockClicked(blockText: String, blockId: String)
        fun onResourceClicked(link: ResourceLink)

        /**
         * The chat overlay closed. A conversation that did not exist when this screen loaded may
         * exist now, so the "Continue chat" indicators are re-read — and so is the lesson itself,
         * which the tutor may have rewritten in there with the user's say-so (FR5.4). Back when
         * the chat was a separate destination the navigation lifecycle did this for free; an
         * overlay never detaches the presenter, so it has to be asked.
         */
        fun onChatClosed()
    }
}

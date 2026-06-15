package com.example.grasp.ui.feature.home

import com.example.grasp.core.mvp.MvpPresenter
import com.example.grasp.core.mvp.MvpView
import com.example.grasp.data.model.Mode
import com.example.grasp.data.model.TopicSuggestion

/**
 * MVP contract for the Home / topic-entry screen.
 *
 * The Presenter decides where a topic goes: a Learner query opens a roadmap, a Tinkerer
 * query opens a step guide. It signals that decision through the View's `open*` methods,
 * which the screen turns into actual navigation, keeping the View free of routing logic.
 */
interface HomeContract {

    interface View : MvpView {
        /** Render the popular-topic suggestions. */
        fun showPopularTopics(topics: List<TopicSuggestion>)

        /** Navigate to a Learner roadmap for [pathId]. */
        fun openLearner(pathId: String)

        /** Navigate to a Tinkerer guide for [pathId]. */
        fun openTinker(pathId: String)
    }

    interface Presenter : MvpPresenter<View> {
        /** User submitted a free-text topic/task in the given [mode]. */
        fun onSubmitTopic(query: String, mode: Mode)

        /** User tapped one of the popular-topic cards. */
        fun onPopularTopicClicked(topic: TopicSuggestion)
    }
}

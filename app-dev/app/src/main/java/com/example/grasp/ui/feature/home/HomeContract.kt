package com.example.grasp.ui.feature.home

import com.example.grasp.core.mvp.MvpPresenter
import com.example.grasp.core.mvp.MvpView
import com.example.grasp.data.model.Mode
import com.example.grasp.data.model.SavedItem

/**
 * MVP contract for the Home / topic-entry screen.
 *
 * The Presenter decides where a topic goes: a Learner query opens a roadmap, a Tinkerer
 * query opens a step guide. It signals that decision through the View's `open*` methods,
 * which the screen turns into actual navigation, keeping the View free of routing logic.
 *
 * Home used to also list "popular topics", which were suggestions pointing at path ids nobody
 * had created — tapping one opened an empty roadmap. The real examples now live in the user's
 * Library as ordinary saved items (see `StarterLibrary`), so Home just points at them.
 */
interface HomeContract {

    interface View : MvpView {
        /**
         * Cover the screen while the whole roadmap and all of its lessons are generated, or clear
         * it when [topic] is null. Generation takes long enough that the user needs to see it
         * happening rather than a frozen Home screen.
         */
        fun showGenerating(topic: String?, mode: Mode = Mode.LEARNER)

        /** Generation failed — the user is still on Home and can try again. */
        fun showGenerationFailed()

        /**
         * Render (or clear, when `null`) the "jump back in" banner — the saved path the user is
         * closest to finishing. Keeps the most likely next action one tap from the launch screen.
         */
        fun showContinue(item: SavedItem?)

        /** Navigate to a Learner roadmap for [pathId]. */
        fun openLearner(pathId: String)

        /** Navigate to a Tinkerer guide for [pathId]. */
        fun openTinker(pathId: String)
    }

    interface Presenter : MvpPresenter<View> {
        /** User submitted a free-text topic/task in the given [mode]. */
        fun onSubmitTopic(query: String, mode: Mode)

        /** User tapped the "jump back in" banner. */
        fun onContinueClicked(item: SavedItem)
    }
}

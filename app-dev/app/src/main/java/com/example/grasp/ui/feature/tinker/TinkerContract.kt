package com.example.grasp.ui.feature.tinker

import com.example.grasp.core.mvp.MvpPresenter
import com.example.grasp.core.mvp.MvpView
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.model.TinkerStep

/**
 * MVP contract for the Tinkerer guide screen which is a flat, ordered
 * checklist with a linear progress bar.
 */
interface TinkerContract {

    interface View : MvpView {
        /** Render (or re-render after a toggle) the guide. */
        fun showGuide(guide: TinkerGuide)

        /** Couldn't resolve the guide (graceful error). */
        fun showNotFound()

        /** Open the multi-modal AI chat scoped to [context]. */
        fun openChat(context: String)
    }

    interface Presenter : MvpPresenter<View> {
        /** Check / uncheck a step. */
        fun onToggleStep(step: TinkerStep)

        /** Ask the AI about this task (e.g. Scenario 3's "is the stove temp right?"). */
        fun onAskAi()
    }
}

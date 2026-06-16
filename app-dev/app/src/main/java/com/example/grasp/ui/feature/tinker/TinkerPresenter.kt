package com.example.grasp.ui.feature.tinker

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.model.TinkerStep
import com.example.grasp.data.repository.FakePathRepository
import com.example.grasp.data.repository.PathRepository

/**
 * Logic for the Tinkerer guide screen. Holds the working copy of the guide so step toggles
 * recompute progress, then re-emits it to the View.
 *
 * SKELETON behavior: toggles live in memory only (not persisted). Offline checklist updates
 * are explicitly allowed, so this is the right seam to later persist locally.
 */
class TinkerPresenter(
    private val guideId: String,
    private val repo: PathRepository = FakePathRepository,
) : BasePresenter<TinkerContract.View>(), TinkerContract.Presenter {

    private var guide: TinkerGuide? = null

    override fun onViewAttached() {
        val loaded = repo.tinkerGuide(guideId)
        if (loaded == null) view?.showNotFound() else {
            guide = loaded
            view?.showGuide(loaded)
        }
    }

    override fun onToggleStep(step: TinkerStep) {
        val current = guide ?: return
        val updated = current.copy(
            steps = current.steps.map { if (it.id == step.id) it.copy(done = !it.done) else it },
        )
        guide = updated
        view?.showGuide(updated)
    }

    override fun onAskAi() {
        view?.openChat(guide?.title ?: "your task")
    }
}

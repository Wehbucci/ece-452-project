package com.example.grasp.ui.feature.home

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.Mode
import com.example.grasp.data.model.TopicSuggestion
import com.example.grasp.data.repository.FirebasePathRepository
import com.example.grasp.data.repository.PathRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Logic for the Home screen. Loads popular topics on attach and routes topic submissions to
 * the right mode.
 */
class HomePresenter(
    private val repo: PathRepository = FirebasePathRepository(),
) : BasePresenter<HomeContract.View>(), HomeContract.Presenter {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onViewAttached() {
        scope.launch {
            view?.showPopularTopics(repo.popularTopics())
        }
    }

    override fun detach() {
        scope.cancel()
        super.detach()
    }

    /** True while a topic is being generated, so a second submit can't start a parallel one. */
    private var generating = false

    override fun onSubmitTopic(query: String, mode: Mode) {
        if (query.isBlank() || generating) return
        generating = true
        // The roadmap AND every lesson in it are written before we navigate, so this is a real
        // wait — show it rather than leaving Home looking frozen.
        view?.showGenerating(query.trim())
        scope.launch {
            val createdPath = repo.createTopic(query, mode)
            generating = false
            view?.showGenerating(null)
            if (createdPath == null) {
                view?.showGenerationFailed()
                return@launch
            }
            when (mode) {
                Mode.LEARNER -> view?.openLearner(createdPath.id)
                Mode.TINKERER -> view?.openTinker(createdPath.id)
            }
        }
    }

    override fun onPopularTopicClicked(topic: TopicSuggestion) {
        when (topic.mode) {
            Mode.LEARNER -> view?.openLearner(topic.id)
            Mode.TINKERER -> view?.openTinker(topic.id)
        }
    }
}

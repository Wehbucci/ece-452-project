package com.example.grasp.ui.feature.home

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.Mode
import com.example.grasp.data.model.TopicSuggestion
import com.example.grasp.data.repository.FirebasePathRepository
import com.example.grasp.data.repository.PathRepository

/**
 * Logic for the Home screen. Loads popular topics on attach and routes topic submissions to
 * the right mode.
 *
 * SKELETON behavior: a free-text query is turned into a slug id and handed to the path/guide
 * screens, which currently fall back to sample data for unknown ids. Real generation (the AI
 * call) will replace [onSubmitTopic]'s routing with a "generate then open" flow.
 *
 * @param repo data source it defaults to the in-memory fake; injectable for tests/previews.
 */
class HomePresenter(
    private val repo: PathRepository = FirebasePathRepository(),
) : BasePresenter<HomeContract.View>(), HomeContract.Presenter {

    override fun onViewAttached() {
        view?.showPopularTopics(repo.popularTopics())
    }

    override fun onSubmitTopic(query: String, mode: Mode) {
        if (query.isBlank()) return
        val createdPath = repo.createTopic(query, mode)
        val id = createdPath?.id ?: query.trim().lowercase().replace(Regex("\\s+"), "-")
        when (mode) {
            Mode.LEARNER -> view?.openLearner(id)
            Mode.TINKERER -> view?.openTinker(id)
        }
    }

    override fun onPopularTopicClicked(topic: TopicSuggestion) {
        when (topic.mode) {
            Mode.LEARNER -> view?.openLearner(topic.id)
            Mode.TINKERER -> view?.openTinker(topic.id)
        }
    }
}

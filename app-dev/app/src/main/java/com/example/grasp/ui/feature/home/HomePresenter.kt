package com.example.grasp.ui.feature.home

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.Mode
import com.example.grasp.data.model.TopicSuggestion
import com.example.grasp.data.repository.AiTreeGenerator
import com.example.grasp.data.repository.FakePathRepository
import com.example.grasp.data.repository.GeneratedPathCache
import com.example.grasp.data.repository.PathRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Logic for the Home screen. Loads popular topics on attach and routes topic submissions through
 * the AI generator before opening the appropriate screen.
 *
 * @param repo data source for popular topics (defaults to the in-memory fake)
 * @param generator AI tree/guide generator (injectable for tests)
 */
class HomePresenter(
    private val repo: PathRepository = FakePathRepository,
    private val generator: AiTreeGenerator = AiTreeGenerator,
) : BasePresenter<HomeContract.View>(), HomeContract.Presenter {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onViewAttached() {
        view?.showPopularTopics(repo.popularTopics())
    }

    override fun detach() {
        scope.cancel()
        super.detach()
    }

    override fun onSubmitTopic(query: String, mode: Mode) {
        if (query.isBlank()) return
        view?.showGenerating()
        scope.launch {
            try {
                when (mode) {
                    Mode.LEARNER -> {
                        val path = generator.generateLearningPath(query.trim())
                        GeneratedPathCache.paths[path.id] = path
                        view?.openLearner(path.id)
                    }
                    Mode.TINKERER -> {
                        val guide = generator.generateTinkerGuide(query.trim())
                        GeneratedPathCache.guides[guide.id] = guide
                        view?.openTinker(guide.id)
                    }
                }
            } catch (e: Exception) {
                view?.showGenerateError("${e.javaClass.simpleName}: ${e.message?.take(120)}")
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

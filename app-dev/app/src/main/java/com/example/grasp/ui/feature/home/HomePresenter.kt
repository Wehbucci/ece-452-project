package com.example.grasp.ui.feature.home

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.Mode
import com.example.grasp.data.model.SavedItem
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.model.TopicSuggestion
import com.example.grasp.data.repository.FirebasePathRepository
import com.example.grasp.data.repository.PathRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Logic for the Home screen. On attach it loads the popular topics and picks the saved path that
 * deserves the "jump back in" banner; it also routes topic submissions to the right mode.
 */
class HomePresenter(
    private val repo: PathRepository = FirebasePathRepository(),
) : BasePresenter<HomeContract.View>(), HomeContract.Presenter {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onViewAttached() {
        scope.launch {
            // Both reads still block (see PathRepository), and the Firebase-backed implementation
            // waits on network inside them — so they run on IO and only the view callbacks touch
            // Main. Topics resolve first so the list paints without waiting on the (slower)
            // saved-items query behind the banner.
            val topics = withContext(Dispatchers.IO) { repo.popularTopics() }
            view?.showPopularTopics(topics)

            val resumable = withContext(Dispatchers.IO) { pickResumable(repo.savedItems()) }
            view?.showContinue(resumable)
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

    override fun onContinueClicked(item: SavedItem) {
        when (item) {
            is LearningPath -> view?.openLearner(item.id)
            is TinkerGuide -> view?.openTinker(item.id)
        }
    }

    /**
     * The banner shows the unfinished path the user is CLOSEST to finishing — the one where one
     * more lesson is most likely to land a "done" moment. Completed paths are skipped (nothing to
     * resume) and an empty library yields null, which hides the banner entirely.
     */
    private fun pickResumable(items: List<SavedItem>): SavedItem? =
        items.filter { it.progress < 1f }.maxByOrNull { it.progress }
}

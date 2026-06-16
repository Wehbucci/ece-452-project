package com.example.grasp.ui.feature.subtopic

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.ResourceLink
import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.repository.FakePathRepository
import com.example.grasp.data.repository.PathRepository

/**
 * Logic for the Subtopic detail screen. Resolves the node's content on attach.
 *
 * SKELETON behavior: completion is tracked in memory only and not persisted. Real version
 * writes through the repository and syncs to the cloud.
 */
class SubtopicPresenter(
    private val pathId: String,
    private val nodeId: String,
    private val repo: PathRepository = FakePathRepository,
) : BasePresenter<SubtopicContract.View>(), SubtopicContract.Presenter {

    private var subtopic: Subtopic? = null
    private var completed: Boolean = false

    override fun onViewAttached() {
        val loaded = repo.subtopic(pathId, nodeId)
        if (loaded == null) {
            view?.showNotFound()
            return
        }
        subtopic = loaded
        completed = loaded.completed
        view?.showSubtopic(loaded)
        view?.showCompleted(completed)
    }

    override fun onToggleComplete() {
        completed = !completed
        view?.showCompleted(completed)
        // TODO(persistence): write completion through the repository + cloud sync.
    }

    override fun onAskAi() {
        view?.openChat(subtopic?.title ?: "your material")
    }

    override fun onBlockClicked(blockText: String) {
        // Scope the chat to the tapped block (a short preview is enough for context).
        view?.openChat(blockText.take(60))
    }

    override fun onResourceClicked(link: ResourceLink) {
        view?.openResource(link.url)
    }
}

package com.example.grasp.ui.feature.subtopic

import android.util.Log
import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.ResourceLink
import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.repository.ChatRepository
import com.example.grasp.data.repository.FirebaseChatRepository
import com.example.grasp.data.repository.FirebasePathRepository
import com.example.grasp.data.repository.FirebaseUserRepository
import com.example.grasp.data.repository.PathRepository
import com.example.grasp.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SubtopicPresenter(
    private val pathId: String,
    private val nodeId: String,
    private val repo: PathRepository = FirebasePathRepository(),
    private val chatRepo: ChatRepository = FirebaseChatRepository(),
    private val userRepo: UserRepository = FirebaseUserRepository(),
) : BasePresenter<SubtopicContract.View>(), SubtopicContract.Presenter {

    private var subtopic: Subtopic? = null
    private var completed: Boolean = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onViewAttached() {
        scope.launch {
            val loaded = repo.subtopic(pathId, nodeId)
            if (loaded == null) {
                view?.showNotFound()
                return@launch
            }
            subtopic = loaded
            completed = loaded.completed
            view?.showSubtopic(loaded)
            view?.showCompleted(completed)
            loadChatIndicators()
        }
    }

    /**
     * Re-read the lesson, quietly.
     *
     * Unlike the first load, a miss here changes nothing on screen: there is already a lesson in
     * front of the user, and swapping it for "not found" because one fetch failed would lose the
     * thing they were reading.
     */
    private fun reloadLesson() {
        scope.launch {
            val loaded = repo.subtopic(pathId, nodeId) ?: return@launch
            subtopic = loaded
            completed = loaded.completed
            view?.showSubtopic(loaded)
            view?.showCompleted(completed)
        }
    }

    override fun detach() {
        scope.cancel()
        super.detach()
    }

    override fun onToggleComplete() {
        completed = !completed
        view?.showCompleted(completed)

        // Persist change to cloud. Independently of each other: a Firestore write resolves only
        // when the SERVER acknowledges it, so offline the first of these takes effect locally and
        // then waits indefinitely — and anything sequenced behind it never runs at all.
        scope.launch { repo.updateNodeCompletion(pathId, nodeId, completed) }
        // Only finishing something counts as studying. Un-ticking a lesson is a correction, and
        // taking a day off the streak for it would punish fixing a mistake.
        if (completed) scope.launch { userRepo.recordStudyToday() }
    }

    override fun onAskAi() {
        view?.openChat(subtopic?.title ?: "your material", pathId, nodeId)
    }

    override fun onBlockClicked(blockText: String, blockId: String) {
        view?.openChat(blockText.take(60), pathId, nodeId, blockId)
    }

    override fun onResourceClicked(link: ResourceLink) {
        view?.openResource(link.url)
    }

    override fun onChatClosed() {
        // The lesson first: the tutor can rewrite it from inside the chat once the user accepts
        // (FR5.4), and closing the panel onto the paragraph they just replaced would read as the
        // change having been lost.
        reloadLesson()
        loadChatIndicators()
    }

    private fun loadChatIndicators() {
        scope.launch {
            val prefix = "${pathId}__${nodeId}"
            val ids = chatRepo.existingChatIds(prefix)
            val hasFabHistory = prefix in ids
            val blockIds = ids
                .filter { it.startsWith("${prefix}__") }
                .map { it.removePrefix("${prefix}__") }
                .filter { it.isNotEmpty() }
                .toSet()
            view?.showChatIndicators(hasFabHistory, blockIds)
        }
    }
}

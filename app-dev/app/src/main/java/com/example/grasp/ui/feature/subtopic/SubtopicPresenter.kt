package com.example.grasp.ui.feature.subtopic

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.ResourceLink
import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.repository.ChatRepository
import com.example.grasp.data.repository.FirebaseChatRepository
import com.example.grasp.data.repository.FirebasePathRepository
import com.example.grasp.data.repository.PathRepository
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
) : BasePresenter<SubtopicContract.View>(), SubtopicContract.Presenter {

    private var subtopic: Subtopic? = null
    private var completed: Boolean = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
        loadChatIndicators()
    }

    override fun detach() {
        scope.cancel()
        super.detach()
    }

    override fun onToggleComplete() {
        completed = !completed
        view?.showCompleted(completed)
    }

    override fun onAskAi() {
        view?.openChat(subtopic?.title ?: "your material", pathId, nodeId)
    }

    override fun onBlockClicked(blockText: String, blockIndex: Int) {
        view?.openChat(blockText.take(60), pathId, nodeId, blockIndex)
    }

    override fun onResourceClicked(link: ResourceLink) {
        view?.openResource(link.url)
    }

    private fun loadChatIndicators() {
        scope.launch {
            val prefix = "${pathId}__${nodeId}"
            val ids = chatRepo.existingChatIds(prefix)
            val hasFabHistory = prefix in ids
            val blockIndices = ids
                .filter { it.startsWith("${prefix}__") }
                .mapNotNull { it.removePrefix("${prefix}__").toIntOrNull() }
                .toSet()
            view?.showChatIndicators(hasFabHistory, blockIndices)
        }
    }
}

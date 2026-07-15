package com.example.grasp.ui.feature.subtopic

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.ResourceLink
import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.repository.AiTreeGenerator
import com.example.grasp.data.repository.ChatRepository
import com.example.grasp.data.repository.FakePathRepository
import com.example.grasp.data.repository.FirebaseChatRepository
import com.example.grasp.data.repository.GeneratedPathCache
import com.example.grasp.data.repository.PathRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SubtopicPresenter(
    private val pathId: String,
    private val nodeId: String,
    private val repo: PathRepository = FakePathRepository,
    private val chatRepo: ChatRepository = FirebaseChatRepository(),
    private val generator: AiTreeGenerator = AiTreeGenerator,
) : BasePresenter<SubtopicContract.View>(), SubtopicContract.Presenter {

    private var subtopic: Subtopic? = null
    private var completed: Boolean = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onViewAttached() {
        val cacheKey = "${pathId}__${nodeId}"

        // Already generated for this node — show immediately.
        val cached = GeneratedPathCache.subtopics[cacheKey]
        if (cached != null) {
            show(cached)
            return
        }

        // AI-generated path: generate content lazily now that the node is opened.
        val aiPath = GeneratedPathCache.paths[pathId]
        if (aiPath != null) {
            val node = aiPath.nodes.firstOrNull { it.id == nodeId }
            if (node == null || node.isBranchOut) { view?.showNotFound(); return }
            val nonBranch = aiPath.nodes.filter { !it.isBranchOut }
            val position = nonBranch.indexOfFirst { it.id == nodeId } + 1
            val stepLabel = "Step $position of ${nonBranch.size}"

            scope.launch {
                try {
                    val generated = generator.generateSubtopic(
                        pathTitle = aiPath.title,
                        nodeTitle = node.title,
                        stepLabel = stepLabel,
                        estMinutes = node.estMinutes,
                    )
                    GeneratedPathCache.subtopics[cacheKey] = generated
                    show(generated)
                } catch (e: Exception) {
                    view?.showNotFound()
                }
            }
            return
        }

        // Popular-topic / fake path: use the repo as before.
        val loaded = repo.subtopic(pathId, nodeId)
        if (loaded == null) { view?.showNotFound(); return }
        show(loaded)
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

    private fun show(loaded: Subtopic) {
        subtopic = loaded
        completed = loaded.completed
        view?.showSubtopic(loaded)
        view?.showCompleted(completed)
        loadChatIndicators()
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

package com.example.grasp.ui.feature.tinker

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.model.TinkerStep
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

class TinkerPresenter(
    private val guideId: String,
    private val repo: PathRepository = FakePathRepository,
    private val chatRepo: ChatRepository = FirebaseChatRepository(),
) : BasePresenter<TinkerContract.View>(), TinkerContract.Presenter {

    private var guide: TinkerGuide? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onViewAttached() {
        val loaded = GeneratedPathCache.guides[guideId] ?: repo.tinkerGuide(guideId)
        if (loaded == null) view?.showNotFound() else {
            guide = loaded
            view?.showGuide(loaded)
        }
        scope.launch {
            val hasHistory = chatRepo.existingChatIds("tinker__$guideId").isNotEmpty()
            view?.showChatIndicator(hasHistory)
        }
    }

    override fun detach() {
        scope.cancel()
        super.detach()
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
        view?.openChat(guide?.title ?: "your task", guideId)
    }
}

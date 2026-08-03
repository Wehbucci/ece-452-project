package com.example.grasp.ui.feature.tinker

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.DownloadState
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.model.TinkerStep
import com.example.grasp.data.repository.ChatRepository
import com.example.grasp.data.repository.FakePathRepository
import com.example.grasp.data.repository.FirebasePathRepository
import com.example.grasp.data.repository.FirebaseChatRepository
import com.example.grasp.data.repository.FirebaseUserRepository
import com.example.grasp.data.repository.PathRepository
import com.example.grasp.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TinkerPresenter(
    private val guideId: String,
    private val repo: PathRepository = FirebasePathRepository(),
    private val chatRepo: ChatRepository = FirebaseChatRepository(),
    private val userRepo: UserRepository = FirebaseUserRepository(),
) : BasePresenter<TinkerContract.View>(), TinkerContract.Presenter {

    private var guide: TinkerGuide? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onViewAttached() {
        scope.launch {
            val loaded = repo.tinkerGuide(guideId)
            if (loaded == null) view?.showNotFound() else {
                guide = loaded
                view?.showGuide(loaded)
            }
            loadChatIndicator()
        }
    }

    private suspend fun loadChatIndicator() {
        val hasHistory = chatRepo.existingChatIds("tinker__$guideId").isNotEmpty()
        view?.showChatIndicator(hasHistory)
    }

    override fun detach() {
        scope.cancel()
        super.detach()
    }

    override fun onToggleStep(step: TinkerStep) {
        val current = guide ?: return
        val newDone = !step.done
        val updated = current.copy(
            steps = current.steps.map { if (it.id == step.id) it.copy(done = newDone) else it },
        )
        guide = updated
        view?.showGuide(updated)

        scope.launch {
            repo.updateTinkerStepCompletion(guideId, step.id, newDone)
            // A finished step is worth what a finished lesson is (see `core.progress.Xp`), so it
            // keeps a streak alive too. Un-ticking one does not.
            if (newDone) userRepo.recordStudyToday()
        }
    }

    override fun onAskAi() {
        view?.openChat(guide?.title ?: "your task", guideId)
    }

    override fun onAskAboutStep(step: TinkerStep) {
        // The step's own words name the chat, matching how a tapped lesson paragraph names its own.
        view?.openChat("Step ${step.order}: ${step.instruction.take(50)}", guideId, step.id)
    }

    override fun onChatClosed() {
        scope.launch { loadChatIndicator() }
    }

    override fun onDownloadGuide() {
        val current = guide ?: return
        if (current.downloadState == DownloadState.AVAILABLE) return
        
        scope.launch {
            val success = repo.downloadTopic(guideId)
            if (success) {
                guide = current.copy(downloadState = DownloadState.AVAILABLE)
                guide?.let { view?.showGuide(it) }
            }
        }
    }
}

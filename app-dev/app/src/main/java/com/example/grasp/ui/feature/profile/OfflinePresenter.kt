package com.example.grasp.ui.feature.profile

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.DownloadState
import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.SavedItem
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.repository.FirebasePathRepository
import com.example.grasp.data.repository.PathRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Logic for the Offline Content screen. Specifically fetches only from the local cache
 * to ensure instant loading even when connection is poor or absent.
 */
class OfflinePresenter(
    private val repo: PathRepository = FirebasePathRepository(),
) : BasePresenter<OfflineContract.View>(), OfflineContract.Presenter {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onViewAttached() {
        scope.launch {
            // Force cache for instant offline access.
            val offlineItems = withContext(Dispatchers.IO) { 
                repo.savedItems(forceCache = true).filter { it.downloadState == DownloadState.AVAILABLE }
            }
            view?.showOfflineItems(offlineItems)
        }
    }

    override fun detach() {
        scope.cancel()
        super.detach()
    }

    override fun onItemClicked(item: SavedItem) {
        when (item) {
            is LearningPath -> view?.openLearner(item.id)
            is TinkerGuide -> view?.openTinker(item.id)
        }
    }
}

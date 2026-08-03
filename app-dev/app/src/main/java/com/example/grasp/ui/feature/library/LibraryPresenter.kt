package com.example.grasp.ui.feature.library

import android.util.Log
import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.SavedItem
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.repository.FirebasePathRepository
import com.example.grasp.data.repository.PathRepository
import com.example.grasp.core.util.NetworkMonitor
import com.example.grasp.GraspApp
import com.example.grasp.data.model.DownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Logic for the Library screen. Loads saved items on attach and routes a tapped item to the
 * right screen based on its mode.
 */
class LibraryPresenter(
    private val repo: PathRepository = FirebasePathRepository(),
) : BasePresenter<LibraryContract.View>(), LibraryContract.Presenter {

    private val items = mutableListOf<SavedItem>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onViewAttached() = reload()

    /**
     * Fetches the library, saying so while it happens.
     *
     * The loading flag is raised BEFORE the first suspension point, so the View never gets a frame
     * in which it has no items and no reason given for that.
     */
    private fun reload() {
        view?.showLoading(true)
        val networkMonitor = NetworkMonitor(GraspApp.context)
        
        scope.launch {
            try {
                // A brand-new account gets the starter examples first.
                withContext(Dispatchers.IO) { repo.seedStarterLibrary() }
                
                val saved = withContext(Dispatchers.IO) { repo.savedItems() }
                items.clear()
                items.addAll(saved)
                view?.showSaved(items.toList())
                
                // If offline and no items, show offline empty state
                if (!networkMonitor.isOnline() && saved.isEmpty()) {
                    view?.showOffline(true)
                } else {
                    view?.showOffline(false)
                }
            } catch (e: Exception) {
                Log.e("LibraryPresenter", "reload failed", e)
                view?.showOffline(true)
            } finally {
                view?.showLoading(false)
            }
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

    override fun onDeleteClicked(item: SavedItem) {
        scope.launch {
            repo.deleteTopic(item.id)
            reload()
        }
    }

    override fun onDownloadClicked(item: SavedItem) {
        val networkMonitor = NetworkMonitor(GraspApp.context)
        
        // Optimistic UI: show loading spinner immediately
        updateItemState(item.id, DownloadState.DOWNLOADING)
        
        scope.launch {
            val mobileAllowed = repo.isMobileDataAllowed()
            val online = networkMonitor.isOnline()
            val onWifi = networkMonitor.isOnWifi()

            if (!online) {
                view?.showToast("No internet connection")
                updateItemState(item.id, DownloadState.NONE)
                return@launch
            }
            
            if (!mobileAllowed && !onWifi) {
                view?.showToast("Wi-Fi only enabled. Allow mobile data in Profile > Offline.")
                updateItemState(item.id, DownloadState.NONE)
                return@launch
            }

            view?.showToast("Starting download...")
            val success = repo.downloadTopic(item.id)
            if (success) {
                view?.showToast("Download complete!")
                reload()
            } else {
                view?.showToast("Download failed.")
                updateItemState(item.id, DownloadState.FAILED)
            }
        }
    }

    private fun updateItemState(id: String, state: DownloadState) {
        val index = items.indexOfFirst { it.id == id }
        if (index != -1) {
            val current = items[index]
            val updated = when (current) {
                is LearningPath -> current.copy(downloadState = state)
                is TinkerGuide -> current.copy(downloadState = state)
            }
            items[index] = updated
            view?.showSaved(items.toList())
        }
    }

    override fun onCancelDownloadClicked(item: SavedItem) {
        scope.launch {
            repo.cancelDownload(item.id)
            reload()
        }
    }

    override fun onRemoveDownloadClicked(item: SavedItem) {
        scope.launch {
            repo.removeDownload(item.id)
            reload()
        }
    }
}

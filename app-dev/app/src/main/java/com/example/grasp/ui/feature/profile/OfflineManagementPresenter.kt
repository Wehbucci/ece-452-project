package com.example.grasp.ui.feature.profile

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.DownloadState
import com.example.grasp.data.model.SavedItem
import com.example.grasp.data.repository.FirebasePathRepository
import com.example.grasp.data.repository.PathRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OfflineManagementPresenter(
    private val repo: PathRepository = FirebasePathRepository(),
) : BasePresenter<OfflineManagementContract.View>(), OfflineManagementContract.Presenter {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onViewAttached() {
        loadData()
    }

    private fun loadData() {
        scope.launch {
            val usage = withContext(Dispatchers.IO) { repo.getStorageUsage() }
            view?.showStorageUsage(usage)
            
            val mobileData = withContext(Dispatchers.IO) { repo.isMobileDataAllowed() }
            view?.showMobileDataAllowed(mobileData)
            
            val items = withContext(Dispatchers.IO) { repo.savedItems() }
            val activeSyncs = items.filter { it.downloadState == DownloadState.DOWNLOADING || it.downloadState == DownloadState.PENDING }
            view?.showActiveSyncs(activeSyncs)
        }
    }

    override fun detach() {
        scope.cancel()
        super.detach()
    }

    override fun onMobileDataToggled(enabled: Boolean) {
        scope.launch {
            repo.setMobileDataAllowed(enabled)
            view?.showMobileDataAllowed(enabled)
        }
    }

    override fun onClearAllClicked() {
        scope.launch {
            repo.clearAllDownloads()
            view?.showToast("All downloads cleared")
            loadData()
        }
    }

    override fun onCancelSyncClicked(item: SavedItem) {
        scope.launch {
            repo.cancelDownload(item.id)
            loadData()
        }
    }
}

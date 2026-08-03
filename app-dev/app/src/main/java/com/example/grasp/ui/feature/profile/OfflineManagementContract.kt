package com.example.grasp.ui.feature.profile

import com.example.grasp.core.mvp.MvpPresenter
import com.example.grasp.core.mvp.MvpView
import com.example.grasp.data.model.SavedItem

interface OfflineManagementContract {

    interface View : MvpView {
        fun showStorageUsage(bytes: Long)
        fun showMobileDataAllowed(enabled: Boolean)
        fun showActiveSyncs(items: List<SavedItem>)
        fun showToast(message: String)
    }

    interface Presenter : MvpPresenter<View> {
        fun onMobileDataToggled(enabled: Boolean)
        fun onClearAllClicked()
        fun onCancelSyncClicked(item: SavedItem)
    }
}

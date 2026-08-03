package com.example.grasp.ui.feature.profile

import com.example.grasp.core.mvp.MvpPresenter
import com.example.grasp.core.mvp.MvpView
import com.example.grasp.data.model.SavedItem

/**
 * MVP contract for the Offline Content screen.
 */
interface OfflineContract {

    interface View : MvpView {
        /** Render the filtered list of downloaded items. */
        fun showOfflineItems(items: List<SavedItem>)

        /** Navigate to a roadmap. */
        fun openLearner(id: String)

        /** Navigate to a guide. */
        fun openTinker(id: String)
    }

    interface Presenter : MvpPresenter<View> {
        /** Resume a downloaded item. */
        fun onItemClicked(item: SavedItem)
    }
}

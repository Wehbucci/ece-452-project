package com.example.grasp.ui.feature.library

import com.example.grasp.core.mvp.MvpPresenter
import com.example.grasp.core.mvp.MvpView
import com.example.grasp.data.model.SavedItem

/**
 * MVP contract for the Library, saved topics & guides the user can resume or remove.
 */
interface LibraryContract {

    interface View : MvpView {
        /**
         * Show (or clear) the "fetching your library" state.
         *
         * Its own signal rather than "the list is empty and we haven't been told otherwise",
         * because those two states look identical from the View and are not the same thing at
         * all. Switching to this tab rebuilds the screen with no items, so without this every
         * visit flashed "Your library is empty" at a user whose library is full.
         */
        fun showLoading(loading: Boolean)

        /** Render the saved items (paths + guides together). */
        fun showSaved(items: List<SavedItem>)

        /** Navigate to a saved Learner roadmap. */
        fun openLearner(id: String)

        /** Navigate to a saved Tinkerer guide. */
        fun openTinker(id: String)
    }

    interface Presenter : MvpPresenter<View> {
        /** Resume a saved item. */
        fun onItemClicked(item: SavedItem)

        /** Remove a saved item. */
        fun onDeleteClicked(item: SavedItem)
    }
}

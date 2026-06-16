package com.example.grasp.ui.feature.library

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.SavedItem
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.repository.FakePathRepository
import com.example.grasp.data.repository.PathRepository

/**
 * Logic for the Library screen. Loads saved items on attach and routes a tapped item to the
 * right screen based on its mode.
 *
 * SKELETON behavior: deletion just drops the item from the in-memory list for this session.
 */
class LibraryPresenter(
    private val repo: PathRepository = FakePathRepository,
) : BasePresenter<LibraryContract.View>(), LibraryContract.Presenter {

    private val items = mutableListOf<SavedItem>()

    override fun onViewAttached() {
        items.clear()
        items.addAll(repo.savedItems())
        view?.showSaved(items.toList())
    }

    override fun onItemClicked(item: SavedItem) {
        when (item) {
            is LearningPath -> view?.openLearner(item.id)
            is TinkerGuide -> view?.openTinker(item.id)
        }
    }

    override fun onDeleteClicked(item: SavedItem) {
        items.removeAll { it.id == item.id && it.mode == item.mode }
        view?.showSaved(items.toList())
    }
}

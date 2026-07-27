package com.example.grasp.ui.feature.library

import com.example.grasp.core.mvp.BasePresenter
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
 * Logic for the Library screen. Loads saved items on attach and routes a tapped item to the
 * right screen based on its mode.
 */
class LibraryPresenter(
    private val repo: PathRepository = FirebasePathRepository(),
) : BasePresenter<LibraryContract.View>(), LibraryContract.Presenter {

    private val items = mutableListOf<SavedItem>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onViewAttached() {
        scope.launch {
            // The repository's reads still block (see PathRepository's NOTE) and the Firebase
            // implementation waits on network inside them, so they run off the main thread —
            // otherwise the Library stutters every time it loads or a delete refreshes it.
            val saved = withContext(Dispatchers.IO) { repo.savedItems() }
            items.clear()
            items.addAll(saved)
            view?.showSaved(items.toList())
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
            when (item) {
                is LearningPath -> repo.deleteTopic(item.id)
                is TinkerGuide -> repo.deleteTopic(item.id)
            }
            onViewAttached()
        }
    }
}

package com.example.grasp.ui.feature.profile

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.UserPreferences
import com.example.grasp.data.repository.FirebaseUserRepository
import com.example.grasp.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PreferencesPresenter(
    private val repo: UserRepository = FirebaseUserRepository()
) : BasePresenter<PreferencesContract.View>(), PreferencesContract.Presenter {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onViewAttached() {
        scope.launch {
            val prefs = repo.getPreferences()
            view?.showPreferences(prefs)
        }
    }

    override fun detach() {
        scope.cancel()
        super.detach()
    }

    override fun onSaveClicked(prefs: UserPreferences) {
        scope.launch {
            repo.setPreferences(prefs)
            view?.navigateBack()
        }
    }
}

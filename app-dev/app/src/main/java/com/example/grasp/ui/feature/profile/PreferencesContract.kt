package com.example.grasp.ui.feature.profile

import com.example.grasp.core.mvp.MvpPresenter
import com.example.grasp.core.mvp.MvpView
import com.example.grasp.data.model.UserPreferences

/**
 * MVP contract for the detailed Learning Preferences screen.
 */
interface PreferencesContract {

    interface View : MvpView {
        /** Populates the UI with current cloud settings. */
        fun showPreferences(prefs: UserPreferences)
        
        /** Closes the screen and returns to Profile. */
        fun navigateBack()
    }

    interface Presenter : MvpPresenter<View> {
        /** Saves the modified object to Firestore. */
        fun onSaveClicked(prefs: UserPreferences)
    }
}

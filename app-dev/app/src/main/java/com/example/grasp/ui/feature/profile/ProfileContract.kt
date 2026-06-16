package com.example.grasp.ui.feature.profile

import com.example.grasp.core.mvp.MvpPresenter
import com.example.grasp.core.mvp.MvpView

/**
 * MVP contract for the Profile / account screen.
 * Skeleton shows a demo user and a working "log out" that returns to the login screen.
 */
interface ProfileContract {

    interface View : MvpView {
        /** Render the signed-in user's details. */
        fun showProfile(name: String, email: String)

        /** Logged out — the screen should return to the login flow. */
        fun onLoggedOut()
    }

    interface Presenter : MvpPresenter<View> {
        /** User tapped "Log out". */
        fun onLogout()
    }
}

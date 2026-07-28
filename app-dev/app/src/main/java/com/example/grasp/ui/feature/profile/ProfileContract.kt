package com.example.grasp.ui.feature.profile

import com.example.grasp.core.mvp.MvpPresenter
import com.example.grasp.core.mvp.MvpView

/**
 * MVP contract for the Profile tab — identity, progress summary, settings entry points and
 * sign-out.
 */
interface ProfileContract {

    interface View : MvpView {
        /** Render the signed-in user's display name and email. */
        fun showProfile(name: String, email: String)

        /** Render the level / XP / totals card once the saved paths have been read. */
        fun showStats(stats: ProfileStats)

        /** Auth session ended — the nav layer should return to login. */
        fun onLoggedOut()
    }

    interface Presenter : MvpPresenter<View> {
        fun onLogout()
    }
}

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

        /**
         * Whether the progress numbers are still being read from the cloud.
         *
         * The stats card has no honest zero state: "Level 1 · 0 XP" is what a brand-new account
         * looks like, so showing it while the real numbers are in flight tells a returning user
         * their progress has been wiped. Better to show nothing than to show a wrong number.
         */
        fun showStatsLoading(loading: Boolean)

        /** Navigate to the detailed preferences screen. */
        fun openPreferences()

        /** Auth session ended — the nav layer should return to login. */
        fun onLoggedOut()
    }

    interface Presenter : MvpPresenter<View> {
        /** User wants to edit their learning style/pace. */
        fun onPreferencesClicked()

        fun onLogout()
    }
}

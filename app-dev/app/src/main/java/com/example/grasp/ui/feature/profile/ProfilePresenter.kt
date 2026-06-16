package com.example.grasp.ui.feature.profile

import com.example.grasp.core.mvp.BasePresenter

/**
 * Logic for the Profile screen. Shows a hardcoded demo user for the skeleton; real version
 * reads the signed-in account and calls the auth backend to sign out.
 */
class ProfilePresenter : BasePresenter<ProfileContract.View>(), ProfileContract.Presenter {

    override fun onViewAttached() {
        view?.showProfile(name = "Jordan", email = "jordan@uwaterloo.ca")
    }

    override fun onLogout() {
        // TODO(auth): clear the session/token, then navigate to login.
        view?.onLoggedOut()
    }
}

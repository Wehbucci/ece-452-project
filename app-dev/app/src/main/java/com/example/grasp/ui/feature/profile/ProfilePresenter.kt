package com.example.grasp.ui.feature.profile

import com.example.grasp.core.mvp.BasePresenter
import com.google.firebase.auth.FirebaseAuth

/**
 * Logic for the Profile screen. Shows the signed-in Firebase user; calls the auth
 * backend to sign out.
 */
class ProfilePresenter : BasePresenter<ProfileContract.View>(), ProfileContract.Presenter {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onViewAttached() {
        val user = auth.currentUser
        view?.showProfile(
            name = user?.displayName ?: "User",
            email = user?.email ?: "guest@example.com"
        )
    }

    override fun onLogout() {
        auth.signOut()
        view?.onLoggedOut()
    }
}

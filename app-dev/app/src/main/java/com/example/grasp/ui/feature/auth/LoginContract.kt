package com.example.grasp.ui.feature.auth

import com.example.grasp.core.mvp.MvpPresenter
import com.example.grasp.core.mvp.MvpView

/**
 * MVP contract for the Login screen.
 *
 * Login is REQUIRED because Grasp is cloud-first. For the skeleton the
 * presenter just validates that the fields are non-empty; real auth 
 * plugs in behind [Presenter.onSubmit] later.
 *
 */
interface LoginContract {

    interface View : MvpView {
        /** Show or clear an inline validation/auth error (null clears it). */
        fun showError(message: String?)

        /** Toggle the in-progress state while credentials are being checked. */
        fun showLoading(loading: Boolean)

        /** Auth succeeded — the screen should navigate on to the app. */
        fun onLoggedIn()
    }

    interface Presenter : MvpPresenter<View> {
        /** User tapped "Log in" / "Sign up". */
        fun onSubmit(email: String, password: String)
    }
}

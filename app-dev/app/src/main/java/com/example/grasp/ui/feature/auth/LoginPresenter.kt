package com.example.grasp.ui.feature.auth

import com.example.grasp.core.mvp.BasePresenter

/**
 * Logic for the Login screen. Plain Kotlin, no Android imports — unit-testable with a fake View.
 *
 * SKELETON behavior: treats any non-empty email + password as a successful login. Replace
 * the body of [onSubmit] with a real (coroutine-based) call to the auth backend; surface
 * failures via `view?.showError(...)` so the UI never crashes.
 */
class LoginPresenter : BasePresenter<LoginContract.View>(), LoginContract.Presenter {

    override fun onSubmit(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            view?.showError("Enter your email and password to continue.")
            return
        }
        view?.showError(null)
        // TODO(auth): replace with a real authentication call
        // on a coroutine, then call onLoggedIn() on success.
        view?.showLoading(false)
        view?.onLoggedIn()
    }
}

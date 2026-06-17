package com.example.grasp.ui.feature.auth

import com.example.grasp.core.mvp.BasePresenter
import com.google.firebase.auth.FirebaseAuth

/**
 * Logic for the Login screen. Plain Kotlin, no Android imports — unit-testable with a fake View.
 *
 * SKELETON behavior: treats any non-empty email + password as a successful login. Replace
 * the body of [onSubmit] with a real (coroutine-based) call to the auth backend; surface
 * failures via `view?.showError(...)` so the UI never crashes.
 */
class LoginPresenter : BasePresenter<LoginContract.View>(), LoginContract.Presenter {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onLogin(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            view?.showError("Enter your email and password to continue.")
            return
        }
        view?.showError(null)
        view?.showLoading(true)

        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener {
                view?.showLoading(false)
                view?.onLoggedIn()
            }
            .addOnFailureListener { e ->
                view?.showLoading(false)
                view?.showError(mapError(e, isSignUp = false))
            }
    }

    override fun onSignUp(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            view?.showError("Enter an email and password to create your account.")
            return
        }
        view?.showError(null)
        view?.showLoading(true)

        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener {
                view?.showLoading(false)
                view?.onLoggedIn()
            }
            .addOnFailureListener { e ->
                view?.showLoading(false)
                view?.showError(mapError(e, isSignUp = true))
            }
    }
    
    private fun mapError(e: Exception, isSignUp: Boolean): String {
        return if (isSignUp) {
            "Account creation failed. Please check your email and ensure your password is at least 6 characters."
        } else {
            "Login failed. Please check your email and password and try again."
        }
    }
}

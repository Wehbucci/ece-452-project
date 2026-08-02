package com.example.grasp.ui.feature.auth

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.repository.FirebaseUserRepository
import com.example.grasp.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Logic for the Login screen. Plain Kotlin, no Android imports — unit-testable with a fake View.
 *
 * SKELETON behavior: treats any non-empty email + password as a successful login. Replace
 * the body of [onSubmit] with a real (coroutine-based) call to the auth backend; surface
 * failures via `view?.showError(...)` so the UI never crashes.
 */
class LoginPresenter(
    private val userRepo: UserRepository = FirebaseUserRepository(),
) : BasePresenter<LoginContract.View>(), LoginContract.Presenter {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun detach() {
        scope.cancel()
        super.detach()
    }

    override fun onLogin(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            view?.showError("Enter your email and password to continue.")
            return
        }
        view?.showError(null)
        view?.showLoading(true)

        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null && user.isEmailVerified) {
                    view?.showLoading(false)
                    view?.onLoggedIn()
                } else if (user != null) {
                    auth.signOut()
                    view?.showLoading(false)
                    view?.showError("Please verify your email address before logging in. Check your inbox for a verification link.")
                } else {
                    view?.showLoading(false)
                    view?.showError("Login failed. Please try again.")
                }
            }
            .addOnFailureListener { e ->
                view?.showLoading(false)
                view?.showError(mapError(e, isSignUp = false))
            }
    }

    override fun onSignUp(email: String, password: String, username: String) {
        if (email.isBlank() || password.isBlank()) {
            view?.showError("Enter an email and password to create your account.")
            return
        }
        if (username.isBlank()) {
            view?.showError("Choose a username to create your account.")
            return
        }
        view?.showError(null)
        view?.showLoading(true)

        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    user.sendEmailVerification()
                        .addOnCompleteListener { task ->
                            scope.launch {
                                userRepo.setUsername(username.trim())
                                auth.signOut() // Sign out so they have to log in AFTER verifying.
                                view?.showLoading(false)
                                if (task.isSuccessful) {
                                    view?.showVerificationSent(email.trim())
                                } else {
                                    view?.showError("Account created, but we couldn't send a verification email. Please log in to try again.")
                                }
                            }
                        }
                } else {
                    view?.showLoading(false)
                    view?.showError("Account creation failed. Please try again.")
                }
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

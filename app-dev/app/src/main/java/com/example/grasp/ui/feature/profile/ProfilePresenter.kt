package com.example.grasp.ui.feature.profile

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.repository.FirebaseUserRepository
import com.example.grasp.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ProfilePresenter(
    private val userRepo: UserRepository = FirebaseUserRepository(),
) : BasePresenter<ProfileContract.View>(), ProfileContract.Presenter {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onViewAttached() {
        val user = auth.currentUser
        view?.showProfile(
            name = user?.displayName ?: "User",
            email = user?.email ?: "guest@example.com",
        )
        scope.launch {
            view?.showSkillLevel(userRepo.getSkillLevel())
        }
    }

    override fun detach() {
        scope.cancel()
        super.detach()
    }

    override fun onLogout() {
        auth.signOut()
        view?.onLoggedOut()
    }

    override fun onSkillLevelSelected(level: String) {
        view?.showSkillLevel(level)
        scope.launch { userRepo.setSkillLevel(level) }
    }
}

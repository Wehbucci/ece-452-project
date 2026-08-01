package com.example.grasp.ui.feature.profile

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.core.progress.Xp
import com.example.grasp.data.model.SavedItem
import com.example.grasp.data.repository.FirebasePathRepository
import com.example.grasp.data.repository.FirebaseUserRepository
import com.example.grasp.data.repository.PathRepository
import com.example.grasp.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Logic for the Profile tab: who is signed in, how far they have got, and signing out.
 *
 * The stats are computed HERE (not in the View) from the same saved paths the Library lists, so
 * the player card can never drift from the roadmap: one completed lesson is worth [Xp.PER_LESSON]
 * and [Xp.PER_LEVEL] makes a level — literally the same object the journey HUD reads.
 */
class ProfilePresenter(
    private val repo: PathRepository = FirebasePathRepository(),
    private val userRepo: UserRepository = FirebaseUserRepository(),
) : BasePresenter<ProfileContract.View>(), ProfileContract.Presenter {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onViewAttached() {
        val user = auth.currentUser
        val email = user?.email.orEmpty()

        view?.showStatsLoading(true)
        scope.launch {
            // fallback to the mailbox name for accounts created before usernames existed
            val username = withContext(Dispatchers.IO) { userRepo.getUsername() }
            view?.showProfile(
                name = username?.takeIf { it.isNotBlank() }
                    ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }.ifBlank { "Learner" },
                email = email,
            )

            // Repository reads still block (see PathRepository's NOTE) — keep them off Main.
            val stats = withContext(Dispatchers.IO) { statsFor(repo.savedItems()) }
            view?.showStats(stats)
            view?.showStatsLoading(false)
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

    override fun onPreferencesClicked() {
        view?.openPreferences()
    }

    /**
     * Rolls every saved path/guide up into one player card.
     *
     * Both what counts as a finished lesson ([SavedItem.lessonsMastered]) and what it is worth
     * ([Xp]) come from shared definitions rather than being re-stated here, which is what keeps
     * this card and the roadmap HUD showing the same level for the same account.
     */
    private fun statsFor(items: List<SavedItem>): ProfileStats {
        val lessonsMastered = items.sumOf { it.lessonsMastered }
        val xp = Xp.forLessons(lessonsMastered)
        return ProfileStats(
            pathsStarted = items.size,
            pathsFinished = items.count { it.progress >= 1f },
            lessonsMastered = lessonsMastered,
            level = Xp.levelFor(xp),
            xpInLevel = Xp.inLevel(xp),
            xpPerLevel = Xp.PER_LEVEL,
            xpFraction = Xp.fractionOfLevel(xp),
        )
    }
}

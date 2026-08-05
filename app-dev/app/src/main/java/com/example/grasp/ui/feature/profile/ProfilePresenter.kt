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
 * The stats are computed HERE (not in the View), and lessons-mastered specifically comes from
 * [PathRepository.totalLessonsMastered] rather than being re-summed from [SavedItem.lessonsMastered]
 * over the current library — the same durable account total the roadmap HUD reads, not a fresh
 * count over whatever still exists. This card used to do its own sum, which is exactly how it and
 * the HUD could each survive a deleted roadmap differently: one clawed the XP back, the other
 * (once fixed) didn't, and the two screens told the user a different level for the same account.
 * `pathsStarted`/`pathsFinished` are the opposite case — those SHOULD reflect the current library,
 * since deleting a path is supposed to remove it from that count — so they still come from the
 * live list.
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
            val stats = withContext(Dispatchers.IO) {
                statsFor(repo.savedItems(), repo.totalLessonsMastered())
            }
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

    override fun onOfflineContentClicked() {
        view?.openOfflineContent()
    }

    /**
     * Rolls the account's standing into one player card.
     *
     * [lessonsMastered] is the durable account total (see the class doc); [items] supplies only
     * the two figures that are legitimately about the CURRENT library, not the account's history.
     * What it is worth ([Xp]) comes from the shared definition rather than being re-stated here,
     * which is what keeps this card and the roadmap HUD agreeing on a level for the same account.
     */
    private fun statsFor(items: List<SavedItem>, lessonsMastered: Int): ProfileStats {
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

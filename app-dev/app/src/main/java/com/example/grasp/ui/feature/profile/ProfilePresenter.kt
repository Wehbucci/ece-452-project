package com.example.grasp.ui.feature.profile

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.SavedItem
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.repository.FirebasePathRepository
import com.example.grasp.data.repository.PathRepository
import com.example.grasp.ui.feature.path.PathPresenter
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
 * the player card can never drift from the roadmap: one completed lesson is worth
 * [PathPresenter.XP_PER_LESSON] XP and [PathPresenter.XP_PER_LEVEL] XP makes a level — exactly
 * the rule the journey HUD uses.
 */
class ProfilePresenter(
    private val repo: PathRepository = FirebasePathRepository(),
) : BasePresenter<ProfileContract.View>(), ProfileContract.Presenter {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onViewAttached() {
        val user = auth.currentUser
        val email = user?.email.orEmpty()
        view?.showProfile(
            // Email+password accounts have no display name, so fall back to the mailbox name
            // ("ada@grasp.dev" → "Ada") instead of showing a generic "User".
            name = user?.displayName?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }.ifBlank { "Learner" },
            email = email,
        )

        scope.launch {
            // Repository reads still block (see PathRepository's NOTE) — keep them off Main.
            val stats = withContext(Dispatchers.IO) { statsFor(repo.savedItems()) }
            view?.showStats(stats)
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

    /**
     * Rolls every saved path/guide up into one player card.
     *
     * "Branch out" nodes are affordances rather than lessons, so they are excluded from the
     * count — the same exclusion [PathPresenter] applies when it awards XP.
     */
    private fun statsFor(items: List<SavedItem>): ProfileStats {
        val lessonsMastered = items.sumOf { item ->
            when (item) {
                is LearningPath -> item.nodes.count { it.completed && !it.isBranchOut }
                is TinkerGuide -> item.steps.count { it.done }
            }
        }
        val xp = lessonsMastered * PathPresenter.XP_PER_LESSON
        return ProfileStats(
            pathsStarted = items.size,
            pathsFinished = items.count { it.progress >= 1f },
            lessonsMastered = lessonsMastered,
            level = xp / PathPresenter.XP_PER_LEVEL + 1,
            xpInLevel = xp % PathPresenter.XP_PER_LEVEL,
            xpPerLevel = PathPresenter.XP_PER_LEVEL,
            xpFraction = (xp % PathPresenter.XP_PER_LEVEL).toFloat() / PathPresenter.XP_PER_LEVEL,
        )
    }
}

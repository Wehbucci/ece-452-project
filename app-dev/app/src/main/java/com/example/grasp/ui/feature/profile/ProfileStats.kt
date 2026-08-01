package com.example.grasp.ui.feature.profile

/**
 * The player card: everything the Profile header needs to show, already computed.
 *
 * These are REAL numbers derived from the user's saved paths (see [ProfilePresenter]), not
 * decoration — the level/XP maths comes from [com.example.grasp.core.progress.Xp], the same
 * object the roadmap HUD reads, so a lesson completed on the journey moves this card by the same
 * amount and to the same level.
 *
 * @property pathsStarted how many paths/guides are saved
 * @property pathsFinished how many of those are 100% complete
 * @property lessonsMastered total completed lessons/steps across every saved item
 * @property level current level (1-based)
 * @property xpInLevel XP earned inside the current level
 * @property xpPerLevel XP needed to reach the next level
 * @property xpFraction [xpInLevel] / [xpPerLevel], for the XP bar
 */
data class ProfileStats(
    val pathsStarted: Int,
    val pathsFinished: Int,
    val lessonsMastered: Int,
    val level: Int,
    val xpInLevel: Int,
    val xpPerLevel: Int,
    val xpFraction: Float,
) {
    companion object {
        /**
         * What the card holds before the repository answers.
         *
         * NOT safe to show: it is identical to a real brand-new account, so a returning user
         * would read it as their progress having been wiped. The View gates it behind
         * `showStatsLoading` and shows the loading treatment instead.
         */
        val Empty = ProfileStats(
            pathsStarted = 0,
            pathsFinished = 0,
            lessonsMastered = 0,
            level = 1,
            xpInLevel = 0,
            xpPerLevel = 200,
            xpFraction = 0f,
        )
    }
}

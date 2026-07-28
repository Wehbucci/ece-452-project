package com.example.grasp.ui.feature.profile

/**
 * The player card: everything the Profile header needs to show, already computed.
 *
 * These are REAL numbers derived from the user's saved paths (see [ProfilePresenter]), not
 * decoration — the level/XP maths is the same rule the roadmap HUD uses
 * ([com.example.grasp.ui.feature.path.PathPresenter.XP_PER_LESSON] /
 * `XP_PER_LEVEL`), so a lesson completed on the journey moves this card by the same amount.
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
        /** Placeholder shown before the repository answers (a fresh, level-1 card). */
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

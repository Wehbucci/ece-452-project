package com.example.grasp.core.progress

/**
 * The one place XP is defined (README §Interactions: "+40 XP", 200 XP per level).
 *
 * Every surface that shows a level or an XP bar — the roadmap HUD, the Profile player card, the
 * "+40 XP" on a Mark-complete button — reads its numbers from here. They used to each do the
 * arithmetic themselves, which is how the roadmap could say "Level 1" while the Profile said
 * "Level 3" about the same account.
 *
 * XP is a property of the ACCOUNT, not of a roadmap: it is earned by finishing lessons anywhere
 * and it only ever goes up as long as that work is still there. Opening a second roadmap must
 * never look like starting over, so the HUD reports the account's level, and only the
 * "{n} of {m} lessons mastered" line beside it is about the roadmap on screen.
 *
 * Pure Kotlin, no Android imports — the rules are unit-testable on their own.
 */
object Xp {

    /** XP granted for one completed lesson (Learner) or step (Tinkerer). */
    const val PER_LESSON = 40

    /** XP needed to advance one level. */
    const val PER_LEVEL = 200

    /** Total XP for [lessonsMastered] completed lessons/steps across the whole account. */
    fun forLessons(lessonsMastered: Int): Int = lessonsMastered.coerceAtLeast(0) * PER_LESSON

    /** Levels start at 1, so 0 XP is level 1 and every [PER_LEVEL] XP adds one. */
    fun levelFor(xp: Int): Int = xp.coerceAtLeast(0) / PER_LEVEL + 1

    /** XP earned inside the current level, `0..PER_LEVEL`. */
    fun inLevel(xp: Int): Int = xp.coerceAtLeast(0) % PER_LEVEL

    /** How full the XP bar is, `0f..1f`. */
    fun fractionOfLevel(xp: Int): Float = inLevel(xp).toFloat() / PER_LEVEL
}

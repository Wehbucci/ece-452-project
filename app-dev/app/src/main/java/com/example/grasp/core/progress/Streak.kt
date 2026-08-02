package com.example.grasp.core.progress

import java.time.LocalDate

/**
 * How many days in a row the user has studied.
 *
 * Stored as the length of the run plus the day it last reached, rather than as a list of days: a
 * streak only ever needs to answer "is it still alive, and how long is it", and both fall out of
 * those two numbers.
 *
 * ## What breaks it
 *
 * A day is the unit, not a rolling 24-hour timer. Finish anything on Monday and anything on
 * Tuesday and the streak holds, whatever time of day either happened; let a whole day pass with
 * nothing finished and it is gone.
 *
 * A literal 24-hour clock would mean studying at 9am on Monday and 10am on Tuesday breaks the
 * streak — 25 hours — which punishes ordinary variation in when someone sits down and would push
 * the deadline earlier every day. Days are what people actually mean by "every day", and they are
 * what the flame in the HUD is understood to promise.
 *
 * ## Days are LOCAL
 *
 * [todayEpochDay] reads the device's own calendar, so "today" is the user's today. A streak that
 * turned over at UTC midnight would break in the evening for anyone west of London.
 *
 * Pure Kotlin apart from the clock, which is isolated in [todayEpochDay] precisely so the rules
 * below can be tested without one.
 *
 * @property days length of the run as of [lastStudyDay]. Zero means there is no streak at all —
 *           either nothing has ever been finished, or the record predates the feature.
 * @property lastStudyDay the last day something was finished, as a local epoch day. Meaningless
 *           when [days] is zero.
 */
data class StudyStreak(
    val days: Int,
    val lastStudyDay: Long,
) {
    companion object {
        /** Nothing finished yet. */
        val None = StudyStreak(days = 0, lastStudyDay = 0L)
    }
}

/** Today, on the device's own calendar, as days since the epoch. */
fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

/**
 * The streak as it stands on [today] — which is not always the stored number.
 *
 * A stored streak is a fact about the day it was last extended, and it keeps that value forever.
 * Whether it is still ALIVE is a question about now, so it is answered on every read rather than
 * written down: a user who studied for nine days and then stopped for a week must not open the app
 * to a flame saying nine.
 *
 * Yesterday still counts as alive — the user has the whole of today to keep it going, and telling
 * them at breakfast that they have already lost it would be both wrong and the surest way to make
 * it true.
 */
fun StudyStreak.asOf(today: Long): Int = when {
    days <= 0 -> 0
    // Clock moved backwards, or the user travelled east. Not their doing, so it costs them nothing.
    lastStudyDay > today -> days
    lastStudyDay == today || lastStudyDay == today - 1 -> days
    else -> 0
}

/**
 * The streak after finishing something on [today].
 *
 * Three cases, and the middle one is the whole feature: extending a run that reached yesterday,
 * which is the only way a streak ever grows.
 */
fun StudyStreak.recordingStudy(today: Long): StudyStreak = when {
    // Already counted today. Finishing a second lesson is not a second day.
    days > 0 && lastStudyDay == today -> this
    // Ahead of us on the calendar — leave the record alone rather than rewrite it backwards.
    days > 0 && lastStudyDay > today -> this
    // Reached yesterday, so today continues it.
    days > 0 && lastStudyDay == today - 1 -> StudyStreak(days = days + 1, lastStudyDay = today)
    // Broken, or never started. Today is day one.
    else -> StudyStreak(days = 1, lastStudyDay = today)
}

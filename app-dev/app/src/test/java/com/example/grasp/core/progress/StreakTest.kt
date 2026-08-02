package com.example.grasp.core.progress

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The streak rules, tested against a fixed "today" rather than the clock.
 *
 * That is the whole reason [todayEpochDay] is a separate function: a streak is arithmetic on two
 * numbers, and none of it should need a device, a timezone, or a test that behaves differently at
 * 11pm than at 9am.
 */
class StreakTest {

    /** An arbitrary Tuesday. Only the differences between days matter. */
    private val today = 20_000L

    private fun streak(days: Int, lastStudyDay: Long) = StudyStreak(days, lastStudyDay)

    // ── Is it still alive? ──────────────────────────────────────────────────────────────────

    @Test
    fun `studying today keeps the streak showing`() {
        assertEquals(5, streak(5, today).asOf(today))
    }

    /** The user has the whole of today to keep it going; declaring it dead at breakfast is wrong. */
    @Test
    fun `a streak last extended yesterday is still alive`() {
        assertEquals(5, streak(5, today - 1).asOf(today))
    }

    @Test
    fun `missing a whole day breaks it`() {
        assertEquals(0, streak(5, today - 2).asOf(today))
        assertEquals(0, streak(30, today - 10).asOf(today))
    }

    @Test
    fun `no streak reads as zero`() {
        assertEquals(0, StudyStreak.None.asOf(today))
    }

    /**
     * Travelling east, or a device whose clock was wrong and got corrected, can put the stored day
     * "in the future". That is not the user failing to study.
     */
    @Test
    fun `a record from the future is not punished`() {
        assertEquals(5, streak(5, today + 1).asOf(today))
    }

    // ── Extending it ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the first thing ever finished starts a one-day streak`() {
        val started = StudyStreak.None.recordingStudy(today)
        assertEquals(1, started.days)
        assertEquals(today, started.lastStudyDay)
    }

    @Test
    fun `finishing something the next day extends it`() {
        val extended = streak(5, today - 1).recordingStudy(today)
        assertEquals(6, extended.days)
        assertEquals(today, extended.lastStudyDay)
    }

    /** A second lesson on a Tuesday is not a second Tuesday. */
    @Test
    fun `finishing more on a day already counted changes nothing`() {
        val already = streak(5, today)
        assertEquals(already, already.recordingStudy(today))
        assertEquals(already, already.recordingStudy(today).recordingStudy(today))
    }

    @Test
    fun `studying after a gap starts a new streak at one`() {
        val restarted = streak(30, today - 3).recordingStudy(today)
        assertEquals(1, restarted.days)
        assertEquals(today, restarted.lastStudyDay)
    }

    @Test
    fun `a record from the future is left alone rather than rewritten backwards`() {
        val ahead = streak(5, today + 2)
        assertEquals(ahead, ahead.recordingStudy(today))
    }

    // ── The two rules together ──────────────────────────────────────────────────────────────

    /** Studying every day for a fortnight should read as a fortnight. */
    @Test
    fun `a run of consecutive days counts them all`() {
        var streak = StudyStreak.None
        repeat(14) { day -> streak = streak.recordingStudy(today + day) }

        assertEquals(14, streak.days)
        assertEquals(14, streak.asOf(today + 13))
    }

    /** And one missed day in the middle costs the run, not just the day. */
    @Test
    fun `one missed day resets the count`() {
        var streak = StudyStreak.None
        repeat(5) { day -> streak = streak.recordingStudy(today + day) }
        assertEquals(5, streak.days)

        // Nothing on day 5; back on day 6.
        streak = streak.recordingStudy(today + 6)

        assertEquals(1, streak.days)
    }

    /** The clock only enters here, so this is the one thing that needs the device to be sane. */
    @Test
    fun `today is a plausible epoch day`() {
        // 2020-01-01 is epoch day 18262; anything earlier means the clock is nonsense.
        assert(todayEpochDay() > 18_262L)
    }
}

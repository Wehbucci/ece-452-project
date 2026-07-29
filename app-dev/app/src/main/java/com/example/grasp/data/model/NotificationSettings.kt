package com.example.grasp.data.model

/**
 * The user's notification choices (Profile → Notifications).
 *
 * Kept as one immutable value object so the screen renders a single state and the presenter
 * persists a single object — no per-switch plumbing. Defaults are the "helpful but quiet" set:
 * the nudges that protect a streak are on, marketing-ish digests are off.
 *
 * NOTE: these preferences are stored and honored by the app, but Grasp does not yet SCHEDULE
 * local notifications (no WorkManager/AlarmManager job exists). Wiring delivery is a follow-up;
 * this is deliberately the source of truth it will read from.
 *
 * @property dailyReminder a once-a-day nudge to keep the habit going
 * @property reminderHour hour of day (0-23) for [dailyReminder]
 * @property streakAlerts warn the user when a streak is about to lapse
 * @property milestoneAlerts celebrate level-ups and finished paths
 * @property weeklySummary a weekly recap of what was learned
 */
data class NotificationSettings(
    val dailyReminder: Boolean = true,
    // Must be one of REMINDER_HOURS — the screen offers exactly those chips, so a value outside
    // the list would render with nothing selected.
    val reminderHour: Int = 18,
    val streakAlerts: Boolean = true,
    val milestoneAlerts: Boolean = true,
    val weeklySummary: Boolean = false,
) {
    companion object {
        /** The reminder times offered on the settings screen (24h). */
        val REMINDER_HOURS = listOf(9, 13, 18, 21)
    }
}

/**
 * The individual switches on the notifications screen. Using an enum keeps the contract to one
 * `onToggle` intent instead of one method per switch.
 */
enum class NotificationToggle {
    DAILY_REMINDER,
    STREAK_ALERTS,
    MILESTONES,
    WEEKLY_SUMMARY,
}

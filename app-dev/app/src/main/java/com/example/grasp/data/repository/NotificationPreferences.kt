package com.example.grasp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.grasp.data.model.NotificationSettings

/**
 * Storage boundary for [NotificationSettings] (overview.md §5 — the Model layer owns
 * persistence, presenters only see this interface).
 *
 * These are device-level preferences ("does this phone buzz?"), not account data, so they live
 * in [SharedPreferences] rather than Firestore — they should not follow the user onto a device
 * they never opted in on. Swap in a Firestore-backed implementation behind this same interface
 * if that ever changes; nothing in the UI would move.
 */
interface NotificationPreferences {

    /** Current settings, or the defaults on first run. */
    fun load(): NotificationSettings

    /** Persist [settings] immediately (the screen has no explicit Save button). */
    fun save(settings: NotificationSettings)
}

/**
 * [SharedPreferences]-backed implementation. Cheap, synchronous and safe to call from the main
 * thread for this handful of primitives.
 */
class SharedPrefsNotificationPreferences(context: Context) : NotificationPreferences {

    // applicationContext: this object outlives the Activity that created it.
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): NotificationSettings {
        val defaults = NotificationSettings()
        return NotificationSettings(
            dailyReminder = prefs.getBoolean(KEY_DAILY_REMINDER, defaults.dailyReminder),
            reminderHour = prefs.getInt(KEY_REMINDER_HOUR, defaults.reminderHour),
            streakAlerts = prefs.getBoolean(KEY_STREAK_ALERTS, defaults.streakAlerts),
            milestoneAlerts = prefs.getBoolean(KEY_MILESTONES, defaults.milestoneAlerts),
            weeklySummary = prefs.getBoolean(KEY_WEEKLY_SUMMARY, defaults.weeklySummary),
        )
    }

    override fun save(settings: NotificationSettings) {
        prefs.edit()
            .putBoolean(KEY_DAILY_REMINDER, settings.dailyReminder)
            .putInt(KEY_REMINDER_HOUR, settings.reminderHour)
            .putBoolean(KEY_STREAK_ALERTS, settings.streakAlerts)
            .putBoolean(KEY_MILESTONES, settings.milestoneAlerts)
            .putBoolean(KEY_WEEKLY_SUMMARY, settings.weeklySummary)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "grasp_notification_prefs"
        const val KEY_DAILY_REMINDER = "daily_reminder"
        const val KEY_REMINDER_HOUR = "reminder_hour"
        const val KEY_STREAK_ALERTS = "streak_alerts"
        const val KEY_MILESTONES = "milestone_alerts"
        const val KEY_WEEKLY_SUMMARY = "weekly_summary"
    }
}

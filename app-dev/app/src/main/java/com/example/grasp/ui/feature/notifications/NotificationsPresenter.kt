package com.example.grasp.ui.feature.notifications

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.NotificationSettings
import com.example.grasp.data.model.NotificationToggle
import com.example.grasp.data.repository.NotificationPreferences

/**
 * Logic for the notification preferences screen.
 *
 * There is no Save button by design — each change is applied and persisted immediately, then
 * echoed back to the View, so the screen can never show something that isn't stored.
 *
 * Plain Kotlin (no Android imports): the SharedPreferences detail sits behind
 * [NotificationPreferences], which is what makes this presenter unit-testable with a fake.
 */
class NotificationsPresenter(
    private val prefs: NotificationPreferences,
) : BasePresenter<NotificationsContract.View>(), NotificationsContract.Presenter {

    private var settings: NotificationSettings = NotificationSettings()

    override fun onViewAttached() {
        settings = prefs.load()
        view?.showSettings(settings)
    }

    override fun onToggle(toggle: NotificationToggle, enabled: Boolean) {
        val updated = when (toggle) {
            NotificationToggle.DAILY_REMINDER -> settings.copy(dailyReminder = enabled)
            NotificationToggle.STREAK_ALERTS -> settings.copy(streakAlerts = enabled)
            NotificationToggle.MILESTONES -> settings.copy(milestoneAlerts = enabled)
            NotificationToggle.WEEKLY_SUMMARY -> settings.copy(weeklySummary = enabled)
        }
        apply(updated)
    }

    override fun onReminderHourSelected(hour: Int) {
        // Picking a time is also an intent to be reminded, so it switches the reminder on.
        apply(settings.copy(reminderHour = hour, dailyReminder = true))
    }

    private fun apply(updated: NotificationSettings) {
        if (updated == settings) return
        settings = updated
        prefs.save(updated)
        view?.showSettings(updated)
    }
}

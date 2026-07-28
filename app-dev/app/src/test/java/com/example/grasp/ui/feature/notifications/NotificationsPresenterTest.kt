package com.example.grasp.ui.feature.notifications

import com.example.grasp.data.model.NotificationSettings
import com.example.grasp.data.model.NotificationToggle
import com.example.grasp.data.repository.NotificationPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JUnit tests for [NotificationsPresenter] — it has no Compose/Android imports, so the
 * SharedPreferences store swaps for an in-memory fake and the whole screen's logic runs on the
 * host JVM.
 *
 * They pin the two promises the screen makes: every change is persisted immediately (there is no
 * Save button), and the View always re-renders from what was stored.
 */
class NotificationsPresenterTest {

    /** In-memory stand-in for [com.example.grasp.data.repository.SharedPrefsNotificationPreferences]. */
    private class FakePrefs(var stored: NotificationSettings = NotificationSettings()) : NotificationPreferences {
        var saveCount = 0
        override fun load(): NotificationSettings = stored
        override fun save(settings: NotificationSettings) { stored = settings; saveCount++ }
    }

    private class FakeView : NotificationsContract.View {
        var lastSettings: NotificationSettings? = null
        var renderCount = 0
        override fun showSettings(settings: NotificationSettings) {
            lastSettings = settings
            renderCount++
        }
    }

    private fun attach(
        initial: NotificationSettings = NotificationSettings(),
    ): Triple<NotificationsPresenter, FakeView, FakePrefs> {
        val prefs = FakePrefs(initial)
        val presenter = NotificationsPresenter(prefs)
        val view = FakeView()
        presenter.attach(view)
        return Triple(presenter, view, prefs)
    }

    @Test
    fun `renders stored settings on attach`() {
        val stored = NotificationSettings(dailyReminder = false, reminderHour = 9, weeklySummary = true)
        val (_, view, _) = attach(stored)

        assertEquals(stored, view.lastSettings)
    }

    @Test
    fun `toggling a switch persists it and re-renders`() {
        val (presenter, view, prefs) = attach()

        presenter.onToggle(NotificationToggle.WEEKLY_SUMMARY, true)

        assertTrue(prefs.stored.weeklySummary)
        assertTrue(view.lastSettings!!.weeklySummary)
        assertEquals(1, prefs.saveCount)
    }

    @Test
    fun `toggles are independent`() {
        val (presenter, _, prefs) = attach()

        presenter.onToggle(NotificationToggle.STREAK_ALERTS, false)
        presenter.onToggle(NotificationToggle.MILESTONES, false)

        assertFalse(prefs.stored.streakAlerts)
        assertFalse(prefs.stored.milestoneAlerts)
        // Untouched switches keep their defaults.
        assertTrue(prefs.stored.dailyReminder)
    }

    @Test
    fun `picking a reminder time switches the daily reminder back on`() {
        val (presenter, view, prefs) = attach(NotificationSettings(dailyReminder = false))

        presenter.onReminderHourSelected(9)

        assertEquals(9, prefs.stored.reminderHour)
        assertTrue("choosing a time implies wanting the reminder", prefs.stored.dailyReminder)
        assertTrue(view.lastSettings!!.dailyReminder)
    }

    @Test
    fun `a no-op change writes nothing`() {
        val (presenter, view, prefs) = attach()
        val rendersAfterAttach = view.renderCount

        // Already true by default.
        presenter.onToggle(NotificationToggle.DAILY_REMINDER, true)

        assertEquals(0, prefs.saveCount)
        assertEquals(rendersAfterAttach, view.renderCount)
    }
}

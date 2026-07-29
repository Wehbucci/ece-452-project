package com.example.grasp.ui.feature.notifications

import com.example.grasp.core.mvp.MvpPresenter
import com.example.grasp.core.mvp.MvpView
import com.example.grasp.data.model.NotificationSettings
import com.example.grasp.data.model.NotificationToggle

/**
 * MVP contract for Profile → Notifications.
 *
 * The View is a pure renderer: it never mutates a [NotificationSettings] itself, it reports the
 * switch that moved and re-renders whatever the Presenter hands back. That keeps "what a toggle
 * implies" (e.g. the reminder time only matters while the daily reminder is on) in one place.
 */
interface NotificationsContract {

    interface View : MvpView {
        /** Render the current settings. Called on attach and after every change. */
        fun showSettings(settings: NotificationSettings)
    }

    interface Presenter : MvpPresenter<View> {
        /** A switch was flipped. */
        fun onToggle(toggle: NotificationToggle, enabled: Boolean)

        /** A reminder time chip was picked ([hour] is 0-23). */
        fun onReminderHourSelected(hour: Int)
    }
}

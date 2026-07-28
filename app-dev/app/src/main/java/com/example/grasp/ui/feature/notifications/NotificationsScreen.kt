package com.example.grasp.ui.feature.notifications

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.data.model.NotificationSettings
import com.example.grasp.data.model.NotificationToggle
import com.example.grasp.data.repository.SharedPrefsNotificationPreferences
import com.example.grasp.ui.components.BellGlyph
import com.example.grasp.ui.components.GameCard
import com.example.grasp.ui.components.GameChip
import com.example.grasp.ui.components.GameIconTile
import com.example.grasp.ui.components.GameRowDivider
import com.example.grasp.ui.components.GameSectionHeader
import com.example.grasp.ui.components.GameSettingRow
import com.example.grasp.ui.components.GameTopBar
import com.example.grasp.ui.theme.GameTintAmber
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeBranch
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathNodeCurrentTint
import com.example.grasp.ui.theme.PathNodeDone
import com.example.grasp.ui.theme.PathNodeDoneTint
import com.example.grasp.ui.theme.PathScreenBg
import com.example.grasp.ui.theme.PathXpTrack

/**
 * Profile → Notifications (View): which nudges Grasp is allowed to send.
 *
 * Every change writes through immediately (see [NotificationsPresenter]) — there is no Save
 * button to forget. The reminder-time chips are revealed only while the daily reminder is on,
 * so the screen never offers a setting that would do nothing.
 *
 * MVP wiring matches [com.example.grasp.ui.feature.auth.LoginScreen], with one twist: the
 * SharedPreferences store needs a Context, so [presenterFactory] takes one. Tests pass a fake
 * and ignore it.
 *
 * @param onBack pop back to the Profile tab.
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    presenterFactory: (Context) -> NotificationsContract.Presenter = { context ->
        NotificationsPresenter(SharedPrefsNotificationPreferences(context))
    },
) {
    var uiSettings by remember { mutableStateOf(NotificationSettings()) }

    val context = LocalContext.current
    val presenter = remember(context) { presenterFactory(context) }
    val view = remember {
        object : NotificationsContract.View {
            override fun showSettings(settings: NotificationSettings) { uiSettings = settings }
        }
    }
    DisposableEffect(presenter, view) {
        presenter.attach(view)
        onDispose { presenter.detach() }
    }

    Scaffold(containerColor = PathScreenBg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            GameTopBar(title = "Notifications", onBack = onBack)

            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                IntroCard()
                Spacer(Modifier.height(20.dp))

                GameSectionHeader(text = "Daily practice")
                Spacer(Modifier.height(10.dp))
                GameCard(modifier = Modifier.fillMaxWidth()) {
                    GameSettingRow(
                        title = "Daily reminder",
                        subtitle = "One nudge a day to keep your streak alive",
                        tint = GameTintAmber,
                        glyph = { BellGlyph(PathNodeBranch, Modifier.size(20.dp)) },
                        trailing = {
                            GameSwitch(
                                checked = uiSettings.dailyReminder,
                                accent = PathNodeBranch,
                                onCheckedChange = {
                                    presenter.onToggle(NotificationToggle.DAILY_REMINDER, it)
                                },
                            )
                        },
                    )
                    // The time only matters while the reminder is on, so it slides in with it.
                    AnimatedVisibility(
                        visible = uiSettings.dailyReminder,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column {
                            GameRowDivider()
                            ReminderTimePicker(
                                selectedHour = uiSettings.reminderHour,
                                onSelect = presenter::onReminderHourSelected,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                GameSectionHeader(text = "Progress")
                Spacer(Modifier.height(10.dp))
                GameCard(modifier = Modifier.fillMaxWidth()) {
                    GameSettingRow(
                        title = "Streak alerts",
                        subtitle = "Warn me before I lose a streak",
                        tint = PathNodeCurrentTint,
                        glyph = {
                            Text(text = "🔥", fontSize = 17.sp)
                        },
                        trailing = {
                            GameSwitch(
                                checked = uiSettings.streakAlerts,
                                accent = PathNodeCurrent,
                                onCheckedChange = {
                                    presenter.onToggle(NotificationToggle.STREAK_ALERTS, it)
                                },
                            )
                        },
                    )
                    GameRowDivider()
                    GameSettingRow(
                        title = "Level-ups & milestones",
                        subtitle = "Celebrate finished paths and new levels",
                        tint = PathNodeDoneTint,
                        glyph = {
                            Text(text = "🏆", fontSize = 17.sp)
                        },
                        trailing = {
                            GameSwitch(
                                checked = uiSettings.milestoneAlerts,
                                accent = PathNodeDone,
                                onCheckedChange = {
                                    presenter.onToggle(NotificationToggle.MILESTONES, it)
                                },
                            )
                        },
                    )
                    GameRowDivider()
                    GameSettingRow(
                        title = "Weekly summary",
                        subtitle = "A Sunday recap of what you learned",
                        tint = PathNodeCurrentTint,
                        glyph = {
                            Text(text = "📅", fontSize = 17.sp)
                        },
                        trailing = {
                            GameSwitch(
                                checked = uiSettings.weeklySummary,
                                accent = PathNodeCurrent,
                                onCheckedChange = {
                                    presenter.onToggle(NotificationToggle.WEEKLY_SUMMARY, it)
                                },
                            )
                        },
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Choices are saved on this device as you make them.",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = PathFaint,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Sets the tone of the screen: this is about protecting a habit, not about pinging the user. */
@Composable
private fun IntroCard() {
    GameCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GameIconTile(tint = GameTintAmber) {
                BellGlyph(PathNodeBranch, Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Only what keeps you going",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = PathInk,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Grasp sends nothing you haven't switched on here. Turn any of it off and it stays off.",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = PathMuted,
                )
            }
        }
    }
}

/** The four offered reminder times, as chips. */
@Composable
private fun ReminderTimePicker(selectedHour: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
        Text(
            text = "REMIND ME AT",
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            color = PathMuted,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NotificationSettings.REMINDER_HOURS.forEach { hour ->
                GameChip(
                    label = hour.asClockLabel(),
                    selected = hour == selectedHour,
                    onClick = { onSelect(hour) },
                    accent = PathNodeBranch,
                )
            }
        }
    }
}

/** 24h hour → "9 AM" / "6 PM". Kept local: it is presentation, not data. */
private fun Int.asClockLabel(): String = when {
    this == 0 -> "12 AM"
    this < 12 -> "$this AM"
    this == 12 -> "12 PM"
    else -> "${this - 12} PM"
}

/** Material's Switch, re-tinted so each group keeps its accent color. */
@Composable
private fun GameSwitch(
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = accent,
            checkedBorderColor = accent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = PathXpTrack,
            uncheckedBorderColor = PathXpTrack,
        ),
    )
}

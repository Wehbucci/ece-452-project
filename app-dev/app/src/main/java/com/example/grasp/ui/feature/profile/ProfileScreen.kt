package com.example.grasp.ui.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.ui.components.BellGlyph
import com.example.grasp.ui.components.DownloadGlyph
import com.example.grasp.ui.components.GameCard
import com.example.grasp.ui.components.GameIconTile
import com.example.grasp.ui.components.GameRowDivider
import com.example.grasp.ui.components.GameScreenHeader
import com.example.grasp.ui.components.GameSectionHeader
import com.example.grasp.ui.components.GameStatTile
import com.example.grasp.ui.components.GameTag
import com.example.grasp.ui.components.GameSettingRow
import com.example.grasp.ui.components.GraspBottomBar
import com.example.grasp.ui.components.InfoGlyph
import com.example.grasp.ui.components.LevelBadge
import com.example.grasp.ui.components.SlidersGlyph
import com.example.grasp.ui.components.XpBar
import com.example.grasp.ui.navigation.TopLevelDestination
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.GameDanger
import com.example.grasp.ui.theme.GameDangerTint
import com.example.grasp.ui.theme.GameHeroEnd
import com.example.grasp.ui.theme.GameHeroStart
import com.example.grasp.ui.theme.GameTintAmber
import com.example.grasp.ui.theme.GameTintNeutral
import com.example.grasp.ui.theme.GameTintStreak
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathCard
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeBranch
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathNodeCurrentBevel
import com.example.grasp.ui.theme.PathNodeCurrentTint
import com.example.grasp.ui.theme.PathNodeDone
import com.example.grasp.ui.theme.PathNodeDoneTint
import com.example.grasp.ui.theme.PathScreenBg

/**
 * Profile tab (View) — identity, the progress "player card", settings and sign-out.
 *
 * The header is deliberately the loudest thing in the shell: the same LEVEL badge and XP bar as
 * the roadmap HUD, on the app's hero gradient. Those numbers are real (see [ProfilePresenter]),
 * which is the point — the Profile is where the gamification adds up across every path.
 *
 * Settings are grouped into one card of [GameSettingRow]s. Rows whose feature is not built yet
 * carry a "Soon" tag and are NOT tappable, so nothing on this screen is a dead end.
 *
 * @param onSelectTab switch bottom-nav tabs (handled by the nav layer)
 * @param navigateToLogin return to login after signing out
 * @param navigateToNotifications open notification preferences
 * @param navigateToAbout open the About Grasp screen
 */
@Composable
fun ProfileScreen(
    onSelectTab: (TopLevelDestination) -> Unit,
    onOpenPreferences: () -> Unit,
    navigateToLogin: () -> Unit,
    navigateToNotifications: () -> Unit,
    navigateToAbout: () -> Unit,
    presenterFactory: () -> ProfileContract.Presenter = { ProfilePresenter() },
) {
    var userName by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }
    var playerStats by remember { mutableStateOf(ProfileStats.Empty) }
    var confirmLogout by remember { mutableStateOf(false) }

    val presenter = remember { presenterFactory() }
    val view = remember(navigateToLogin, onOpenPreferences) {
        object : ProfileContract.View {
            override fun showProfile(name: String, email: String) { userName = name; userEmail = email }
            override fun showStats(stats: ProfileStats) { playerStats = stats }
            override fun openPreferences() = onOpenPreferences()
            override fun onLoggedOut() = navigateToLogin()
        }
    }
    DisposableEffect(presenter, view) {
        presenter.attach(view)
        onDispose { presenter.detach() }
    }

    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Scaffold(
        containerColor = PathScreenBg,
        bottomBar = { GraspBottomBar(selected = TopLevelDestination.PROFILE, onSelect = onSelectTab) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            GameScreenHeader(title = "Profile")
            Spacer(Modifier.height(16.dp))

            PlayerCard(name = userName, email = userEmail, stats = playerStats)
            Spacer(Modifier.height(18.dp))

            GameSectionHeader(text = "Your progress")
            Spacer(Modifier.height(10.dp))
            StatsRow(stats = playerStats)
            Spacer(Modifier.height(20.dp))

            GameSectionHeader(text = "Settings")
            Spacer(Modifier.height(10.dp))
            GameCard(modifier = Modifier.fillMaxWidth()) {
                GameSettingRow(
                    title = "Learning preferences",
                    subtitle = "Difficulty, depth and pace",
                    tint = PathNodeCurrentTint,
                    glyph = { SlidersGlyph(PathNodeCurrent, Modifier.size(20.dp)) },
                    onClick = presenter::onPreferencesClicked,
                )
                GameRowDivider()
                GameSettingRow(
                    title = "Offline content",
                    subtitle = "Keep paths available without a connection",
                    tint = PathNodeDoneTint,
                    glyph = { DownloadGlyph(PathNodeDone, Modifier.size(20.dp)) },
                    trailing = { SoonTag() },
                )
                GameRowDivider()
                GameSettingRow(
                    title = "Notifications",
                    subtitle = "Reminders, streaks and celebrations",
                    tint = GameTintAmber,
                    glyph = { BellGlyph(PathNodeBranch, Modifier.size(20.dp)) },
                    onClick = navigateToNotifications,
                )
                GameRowDivider()
                GameSettingRow(
                    title = "About Grasp",
                    subtitle = "What this app does, and who built it",
                    tint = GameTintStreak,
                    glyph = { InfoGlyph(GameDanger, Modifier.size(20.dp)) },
                    onClick = navigateToAbout,
                )
            }

            Spacer(Modifier.height(20.dp))

            GameCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { confirmLogout = true },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = GameDanger,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "Log out",
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = GameDanger,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = if (versionName.isBlank()) "Grasp · Null Pointers" else "Grasp v$versionName · Null Pointers",
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = PathFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmLogout) {
        ConfirmLogoutDialog(
            onConfirm = {
                confirmLogout = false
                presenter.onLogout()
            },
            onDismiss = { confirmLogout = false },
        )
    }
}

/**
 * The hero header: avatar, name, email and the roadmap's own LEVEL badge + XP bar, so progress
 * made on any path shows up here in the same visual language.
 */
@Composable
private fun PlayerCard(name: String, email: String, stats: ProfileStats) {
    GameCard(
        modifier = Modifier.fillMaxWidth(),
        brush = Brush.linearGradient(listOf(GameHeroStart, GameHeroEnd)),
        bevelColor = PathNodeCurrentBevel,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .background(Color.White.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = name.take(1).uppercase().ifBlank { "?" },
                        fontFamily = FredokaFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = Color.White,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                ) {
                    Text(
                        text = name.ifBlank { "Learner" },
                        fontFamily = FredokaFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (email.isNotBlank()) {
                        Text(
                            text = email,
                            fontFamily = NunitoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            color = Color.White.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Same three pieces as the journey HUD's second row: level · bar · numeric XP.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LevelBadge(level = stats.level)
                XpBar(fraction = stats.xpFraction, modifier = Modifier.weight(1f))
                Text(
                    text = "${stats.xpInLevel}/${stats.xpPerLevel} XP",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = Color.White,
                )
            }
        }
    }
}

/** Three real totals rolled up from the user's saved paths. */
@Composable
private fun StatsRow(stats: ProfileStats) {
    GameCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GameStatTile(
                value = stats.lessonsMastered.toString(),
                label = "LESSONS\nMASTERED",
                accent = PathNodeDone,
                tint = PathNodeDoneTint,
                modifier = Modifier.weight(1f),
            )
            GameStatTile(
                value = stats.pathsStarted.toString(),
                label = "PATHS\nSTARTED",
                accent = PathNodeCurrent,
                tint = PathNodeCurrentTint,
                modifier = Modifier.weight(1f),
            )
            GameStatTile(
                value = stats.pathsFinished.toString(),
                label = "PATHS\nFINISHED",
                accent = PathNodeBranch,
                tint = GameTintAmber,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Marks a settings row whose feature is designed but not built yet. */
@Composable
private fun SoonTag() = GameTag(text = "SOON", accent = PathMuted, tint = GameTintNeutral)

@Composable
private fun ConfirmLogoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PathCard,
        shape = RoundedCornerShape(24.dp),
        icon = {
            GameIconTile(tint = GameDangerTint, size = 46.dp) {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = GameDanger,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        title = {
            Text(
                text = "Log out of Grasp?",
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = PathInk,
            )
        },
        text = {
            Text(
                text = "Your paths and progress stay saved to your account — sign back in any time to pick them up.",
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = PathMuted,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Log out",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = GameDanger,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Stay",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = PathMuted,
                )
            }
        },
    )
}

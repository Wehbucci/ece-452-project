@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.grasp.ui.feature.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.data.model.Mode
import com.example.grasp.ui.components.GameCard
import com.example.grasp.ui.components.GameIconTile
import com.example.grasp.ui.components.GameSectionHeader
import com.example.grasp.ui.components.GameTag
import com.example.grasp.ui.components.GameTopBar
import com.example.grasp.ui.components.ModeGlyph
import com.example.grasp.ui.components.SproutGlyph
import com.example.grasp.ui.components.accent
import com.example.grasp.ui.components.tint
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.GameHeroEnd
import com.example.grasp.ui.theme.GameHeroStart
import com.example.grasp.ui.theme.GameTintNeutral
import com.example.grasp.ui.theme.GraspTheme
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathNodeCurrentBevel
import com.example.grasp.ui.theme.PathNodeCurrentTint
import com.example.grasp.ui.theme.PathNodeDone
import com.example.grasp.ui.theme.PathScreenBg

/**
 * Profile → About Grasp: what the app is, how it works, who built it and what it is built on.
 *
 * Deliberately the ONE screen with no Presenter: every value here is either a constant of the
 * product or read straight off the installed package (the version name). Adding an MVP triple
 * for static copy would be ceremony with nothing to test — see
 * [com.example.grasp.ui.feature.notifications.NotificationsScreen] for the wiring used whenever a
 * settings screen does have state.
 *
 * @param onBack pop back to the Profile tab.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Scaffold(containerColor = PathScreenBg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            GameTopBar(title = "About Grasp", onBack = onBack)

            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                AppHeroCard(versionName = versionName)

                Spacer(Modifier.height(22.dp))
                GameSectionHeader(text = "Two ways to start")
                Spacer(Modifier.height(10.dp))
                ModeExplainerCard(
                    mode = Mode.LEARNER,
                    headline = "Learn a topic",
                    body = "Grasp breaks a subject into a branching roadmap of small lessons, ordered so " +
                        "each one builds on the last. Finish a node to unlock what comes next.",
                )
                Spacer(Modifier.height(10.dp))
                ModeExplainerCard(
                    mode = Mode.TINKERER,
                    headline = "Get something done",
                    body = "Tell Grasp the thing you want to make and it returns a flat, ordered " +
                        "checklist — just the steps, in the order you need them.",
                )

                Spacer(Modifier.height(22.dp))
                GameSectionHeader(text = "How it works")
                Spacer(Modifier.height(10.dp))
                GameCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        HowItWorksStep(
                            number = 1,
                            title = "Say what you want",
                            body = "Type a topic or a task, and pick Learn or Tinker.",
                        )
                        HowItWorksStep(
                            number = 2,
                            title = "Grasp builds the path",
                            body = "An AI tutor generates the roadmap, the lesson summaries and the " +
                                "resources to go deeper — and stays available to answer questions on " +
                                "any step.",
                        )
                        HowItWorksStep(
                            number = 3,
                            title = "Play it through",
                            body = "Complete lessons to earn XP, level up and keep your streak. Your " +
                                "progress syncs to your account.",
                            last = true,
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                GameSectionHeader(text = "The team")
                Spacer(Modifier.height(10.dp))
                TeamCard()

                Spacer(Modifier.height(22.dp))
                GameSectionHeader(text = "Built with")
                Spacer(Modifier.height(10.dp))
                GameCard(modifier = Modifier.fillMaxWidth()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BUILT_WITH.forEach { tech ->
                            GameTag(text = tech, accent = PathMuted, tint = GameTintNeutral)
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))
                Text(
                    text = buildString {
                        append("Grasp")
                        if (versionName.isNotBlank()) append(" v$versionName")
                        append(" · ECE 452 · University of Waterloo")
                    },
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = PathFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

/** The product card: mark, name, version and the one-line promise. */
@Composable
private fun AppHeroCard(versionName: String) {
    GameCard(
        modifier = Modifier.fillMaxWidth(),
        brush = Brush.linearGradient(listOf(GameHeroStart, GameHeroEnd)),
        bevelColor = PathNodeCurrentBevel,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .background(Color.White, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                SproutGlyph(
                    stem = PathNodeDone,
                    leaf = PathNodeCurrent,
                    modifier = Modifier.size(42.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Grasp",
                fontFamily = FredokaFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = Color.White,
            )
            if (versionName.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "VERSION $versionName",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Turn anything you want to learn into a path you can actually finish.",
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = Color.White.copy(alpha = 0.92f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** One of the two modes, explained in its own accent color. */
@Composable
private fun ModeExplainerCard(mode: Mode, headline: String, body: String) {
    GameCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GameIconTile(tint = mode.tint) {
                ModeGlyph(mode = mode, tint = mode.accent, size = 24.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = headline,
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = PathInk,
                        modifier = Modifier.weight(1f),
                    )
                    GameTag(text = mode.label, accent = mode.accent, tint = mode.tint)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = body,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = PathMuted,
                )
            }
        }
    }
}

/** A numbered step with the connecting rail, echoing the roadmap's vertical journey. */
@Composable
private fun HowItWorksStep(
    number: Int,
    title: String,
    body: String,
    last: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(PathNodeCurrentTint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number.toString(),
                    fontFamily = FredokaFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = PathNodeCurrent,
                )
            }
            if (!last) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(3.dp)
                        .height(46.dp)
                        .clip(CircleShape)
                        .background(PathNodeCurrentTint),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, bottom = if (last) 0.dp else 12.dp),
        ) {
            Text(
                text = title,
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = PathInk,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = PathMuted,
            )
        }
    }
}

/** Team "Null Pointers" — the six people who built Grasp for ECE 452. */
@Composable
private fun TeamCard() {
    GameCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Null Pointers",
                fontFamily = FredokaFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = PathInk,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "ECE 452 · Software Design & Architecture",
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = PathMuted,
            )
            Spacer(Modifier.height(14.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TEAM.forEach { member -> MemberPill(name = member) }
            }
        }
    }
}

@Composable
private fun MemberPill(name: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(PathScreenBg)
            .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(PathNodeCurrentTint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1),
                fontFamily = FredokaFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = PathNodeCurrent,
            )
        }
        Text(
            text = name,
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            color = PathInk,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** The team, as listed in the repository README. */
private val TEAM = listOf("Hasan", "Leo", "Ali", "Andria", "Ady", "Richard")

/** The stack, kept honest: everything here is actually on the classpath. */
private val BUILT_WITH = listOf(
    "Kotlin",
    "Jetpack Compose",
    "Material 3",
    "MVP architecture",
    "Firebase Auth",
    "Cloud Firestore",
    "Gemini",
)

@Preview(showBackground = true)
@Composable
private fun AboutScreenPreview() {
    GraspTheme {
        AboutScreen(onBack = {})
    }
}

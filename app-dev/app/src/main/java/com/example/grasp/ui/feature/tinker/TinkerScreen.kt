package com.example.grasp.ui.feature.tinker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.model.TinkerStep
import com.example.grasp.ui.components.GameButton
import com.example.grasp.ui.components.GameCard
import com.example.grasp.ui.components.GameProgressBar
import com.example.grasp.ui.components.GameTag
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.GameTintNeutral
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathCard
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeBranch
import com.example.grasp.ui.theme.PathNodeBranchBevel
import com.example.grasp.ui.theme.PathNodeDone
import com.example.grasp.ui.theme.PathNodeDoneBevel
import com.example.grasp.ui.theme.PathScreenBg

/**
 * Tinkerer guide screen (View) — a checklist with a progress HUD.
 *
 * The Learner half of the app got a gamified journey; this is its flat-checklist twin, so it
 * borrows the same furniture: a white HUD header with the back tile and a chunky progress bar,
 * bevelled step cards, and a green "done" state that matches a completed roadmap node. Tapping
 * anywhere on a step toggles it — the whole card is the checkbox.
 *
 * @param guideId navigation argument
 * @param onBack pop the back stack
 * @param onOpenChat open the AI chat with a context string
 */
@Composable
fun TinkerScreen(
    guideId: String,
    onBack: () -> Unit,
    onOpenChat: (context: String, pathId: String) -> Unit,
    presenterFactory: (String) -> TinkerContract.Presenter = { TinkerPresenter(it) },
) {
    var currentGuide by remember { mutableStateOf<TinkerGuide?>(null) }
    var notFound by remember { mutableStateOf(false) }
    var hasChatHistory by remember { mutableStateOf(false) }

    val presenter = remember(guideId) { presenterFactory(guideId) }
    val view = remember(onOpenChat) {
        object : TinkerContract.View {
            override fun showGuide(guide: TinkerGuide) { currentGuide = guide; notFound = false }
            override fun showNotFound() { notFound = true }
            override fun openChat(context: String, pathId: String) = onOpenChat(context, pathId)
            override fun showChatIndicator(hasHistory: Boolean) { hasChatHistory = hasHistory }
        }
    }
    DisposableEffect(presenter, view) {
        presenter.attach(view)
        onDispose { presenter.detach() }
    }

    Scaffold(containerColor = PathScreenBg) { padding ->
        val current = currentGuide
        Column(modifier = Modifier.fillMaxSize()) {
            GuideHud(guide = current, onBack = onBack)

            when {
                notFound -> CenteredMessage("We couldn't load this guide.", padding)
                current == null -> CenteredMessage("Loading…", padding)
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(current.steps, key = { it.id }) { step ->
                            StepCard(step = step, onToggle = { presenter.onToggleStep(step) })
                        }
                    }

                    // The tutor is the one action that isn't a step, so it gets its own bar.
                    Surface(color = PathCard, shadowElevation = 14.dp) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        ) {
                            GameButton(
                                label = if (hasChatHistory) "Continue chat" else "Ask AI",
                                onClick = presenter::onAskAi,
                                accent = PathNodeBranch,
                                bevelColor = PathNodeBranchBevel,
                                contentColor = PathInk,
                                modifier = Modifier.fillMaxWidth(),
                                leading = {
                                    Text(
                                        text = "✦",
                                        fontSize = 16.sp,
                                        color = PathInk,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The fixed header: back tile, title, "2 of 6 done" and the progress bar. */
@Composable
private fun GuideHud(guide: TinkerGuide?, onBack: () -> Unit) {
    Surface(color = PathCard, shadowElevation = 6.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 14.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(PathScreenBg)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PathInk,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(
                        text = guide?.title ?: "Guide",
                        fontFamily = FredokaFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 19.sp,
                        lineHeight = 23.sp,
                        color = PathInk,
                    )
                    if (guide != null) {
                        Text(
                            text = guide.progressLabel,
                            fontFamily = NunitoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = PathMuted,
                        )
                    }
                }
            }
            if (guide != null) {
                Spacer(Modifier.height(14.dp))
                GameProgressBar(
                    fraction = guide.progress,
                    accent = PathNodeDone,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/** One step. The whole card is the toggle; the bubble shows the step number until it's done. */
@Composable
private fun StepCard(step: TinkerStep, onToggle: () -> Unit) {
    GameCard(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StepBubble(order = step.order, done = step.done)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.instruction,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    textDecoration = if (step.done) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (step.done) PathFaint else PathInk,
                )
                if (step.detail.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = step.detail,
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = PathMuted,
                    )
                }
                if (step.estMinutes > 0) {
                    Spacer(Modifier.height(8.dp))
                    GameTag(
                        text = "~${step.estMinutes} MIN",
                        accent = PathMuted,
                        tint = GameTintNeutral,
                    )
                }
            }
        }
    }
}

/** Green check once done, outlined step number before that. */
@Composable
private fun StepBubble(order: Int, done: Boolean) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (done) PathNodeDone else PathCard)
            .then(
                if (done) {
                    Modifier.border(2.5.dp, PathNodeDoneBevel, CircleShape)
                } else {
                    Modifier.border(2.5.dp, PathNodeBranch, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Done",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = order.toString(),
                fontFamily = FredokaFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = PathNodeBranch,
            )
        }
    }
}

@Composable
private fun CenteredMessage(message: String, padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = PathMuted,
        )
    }
}

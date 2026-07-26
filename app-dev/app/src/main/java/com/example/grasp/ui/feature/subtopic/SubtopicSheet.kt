package com.example.grasp.ui.feature.subtopic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.data.model.ResourceLink
import com.example.grasp.data.model.Subtopic
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathChipNeutralBg
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathNodeCurrentBevel
import com.example.grasp.ui.theme.PathNodeCurrentTint
import com.example.grasp.ui.theme.PathNodeDone
import com.example.grasp.ui.theme.PathNodeDoneBevel
import com.example.grasp.ui.theme.PathNodeDoneTint
import com.example.grasp.ui.theme.PathWhyItMattersBg

/**
 * The subtopic-detail body shown inside the journey's Material 3 `ModalBottomSheet`.
 *
 * Stateless: it renders a resolved [Subtopic] and forwards taps. It intentionally lives in the
 * `subtopic/` package (this is the "subtopic detail becomes a bottom sheet" piece) but is driven
 * by `PathPresenter` so that Mark-complete feeds the same XP/unlock/confetti pipeline as the rest
 * of the journey — the whole reason the sheet is hosted by the path Screen.
 *
 * @param completed whether this node is marked done (flips the CTA to "Revisit lesson").
 * @param onMarkComplete complete the lesson (+40 XP) — no-op'd by the caller when already done.
 * @param onAskAi route to the AI chat for this subtopic.
 * @param onAskAboutBlock route to the AI chat about ONE paragraph of the lesson (FR5.2).
 * @param onOpenResource open an optional "explore further" link.
 */
@Composable
fun SubtopicSheetContent(
    subtopic: Subtopic,
    completed: Boolean,
    onMarkComplete: () -> Unit,
    onAskAi: () -> Unit,
    onAskAboutBlock: (index: Int, text: String) -> Unit,
    onOpenResource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // State chip · time pill · step label.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (completed) {
                SheetChip("★ COMPLETED", PathNodeDoneTint, PathNodeDone)
            } else {
                SheetChip("★ YOU'RE HERE", PathNodeCurrentTint, PathNodeCurrent)
            }
            SheetChip("${subtopic.estMinutes} min", PathChipNeutralBg, PathMuted)
            Spacer(Modifier.weight(1f))
            Text(
                text = subtopic.stepLabel.uppercase(),
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                color = PathFaint,
            )
        }

        Text(
            text = subtopic.title,
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            color = PathInk,
        )
        Text(
            text = subtopic.summary,
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = PathInk.copy(alpha = 0.72f),
        )

        // "Why it matters" callout.
        Surface(color = PathWhyItMattersBg, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "WHY IT MATTERS",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = PathNodeCurrent,
                )
                Text(
                    text = subtopic.whyItMatters,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = PathInk.copy(alpha = 0.8f),
                )
            }
        }

        // The lesson itself. Each paragraph is its own block so it can be asked about directly.
        if (subtopic.body.isNotEmpty()) {
            SectionLabel("THE LESSON", modifier = Modifier.padding(top = 4.dp))
            subtopic.body.forEachIndexed { index, block ->
                LessonBlock(text = block, onClick = { onAskAboutBlock(index, block) })
            }
        }

        if (subtopic.resources.isNotEmpty()) {
            SectionLabel("EXPLORE FURTHER", modifier = Modifier.padding(top = 8.dp))
            Text(
                text = "Optional — the lesson above is complete on its own.",
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = PathFaint,
            )
            subtopic.resources.forEach { link ->
                ResourceRow(link) { onOpenResource(link.url) }
            }
        }

        Spacer(Modifier.height(4.dp))

        // CTA row: Mark complete (or Revisit) + square "Ask AI".
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (completed) {
                BeveledButton(
                    text = "✓ Revisit lesson",
                    face = PathNodeDone,
                    bevel = PathNodeDoneBevel,
                    onClick = onMarkComplete, // caller no-ops re-completion; label communicates state
                    modifier = Modifier.weight(1f),
                )
            } else {
                BeveledButton(
                    text = "Mark complete · +40 XP",
                    face = PathNodeCurrent,
                    bevel = PathNodeCurrentBevel,
                    onClick = onMarkComplete,
                    modifier = Modifier.weight(1f),
                )
            }
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PathNodeCurrentTint)
                    .clickable(onClick = onAskAi),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Star, contentDescription = "Ask AI", tint = PathNodeCurrent, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/**
 * What the detail sheet shows while a node's lesson is being written.
 *
 * A node's content is generated the first time it is opened, so this state is real (a few
 * seconds) rather than decorative — naming the subtopic makes the wait feel like progress on the
 * thing the user tapped.
 */
@Composable
fun SubtopicLoadingContent(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SheetChip("✧ WRITING YOUR LESSON", PathNodeCurrentTint, PathNodeCurrent)
        Text(
            text = title,
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            color = PathInk,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = PathNodeCurrent)
            Text(
                text = "Putting this lesson together for you…",
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = PathMuted,
            )
        }
    }
}

/** A small all-caps section heading. */
@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        color = PathFaint,
        modifier = modifier,
    )
}

/**
 * One paragraph of the lesson, as a tappable block (FR4.1/5.2 — select a section of the content
 * to ask about it). The whole block is the tap target, so a learner who is stuck on one idea can
 * open the chat already scoped to it.
 */
@Composable
private fun LessonBlock(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = PathChipNeutralBg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = text,
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = PathInk,
            )
            Text(
                text = "Tap to ask AI about this ›",
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = PathNodeCurrent,
            )
        }
    }
}

/** A small rounded state/time chip. */
@Composable
private fun SheetChip(text: String, bg: Color, fg: Color) {
    Surface(shape = RoundedCornerShape(percent = 50), color = bg, contentColor = fg) {
        Text(
            text = text,
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** A "dive deeper" resource row: kind tile + title + kind sub + chevron. */
@Composable
private fun ResourceRow(link: ResourceLink, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = PathChipNeutralBg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PathNodeCurrentTint),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = link.kind.name.take(1),
                    fontFamily = FredokaFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = PathNodeCurrent,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = link.title,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = PathInk,
                )
                Text(
                    text = link.kind.name.lowercase().replaceFirstChar { it.uppercase() },
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = PathFaint,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = PathFaint)
        }
    }
}

/** A chunky "game" button with a hard 5dp bevel beneath. */
@Composable
private fun BeveledButton(
    text: String,
    face: Color,
    bevel: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.height(54.dp)) {
        // Hard bevel directly beneath the face (no blur — the flat "game" look).
        Box(
            Modifier
                .matchParentSize()
                .offset(y = 5.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bevel),
        )
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp))
                .background(face)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontFamily = FredokaFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Color.White,
            )
        }
    }
}

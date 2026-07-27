package com.example.grasp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.data.model.DiagramItem
import com.example.grasp.data.model.DiagramKind
import com.example.grasp.data.model.LessonBlock
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathCard
import com.example.grasp.ui.theme.PathChipNeutralBg
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeBranch
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathNodeCurrentTint
import com.example.grasp.ui.theme.PathNodeDone

/**
 * Draws a [LessonBlock.Diagram] natively (FR4.4).
 *
 * Every kind renders inside the same card with the same caption treatment, so a lesson with three
 * different diagram kinds still reads as one document. Colours come from the roadmap palette
 * rather than a chart palette for the same reason.
 */
@Composable
fun LessonDiagram(diagram: LessonBlock.Diagram, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PathCard,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (diagram.kind) {
                DiagramKind.FLOW -> FlowDiagram(diagram.items)
                DiagramKind.COMPARE -> CompareDiagram(diagram.items)
                DiagramKind.BAR -> BarDiagram(diagram.items)
            }
            if (diagram.text.isNotBlank()) {
                Text(
                    text = diagram.text,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = PathFaint,
                )
            }
        }
    }
}

/** An ordered sequence: numbered chips joined top to bottom by a connector. */
@Composable
private fun FlowDiagram(items: List<DiagramItem>) {
    Column {
        items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.Top) {
                // The rail: a numbered dot, plus the line down to the next one.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(32.dp).fillMaxHeight(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(PathNodeCurrent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontFamily = FredokaFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color.White,
                        )
                    }
                    if (index < items.lastIndex) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(if (item.detail.isBlank()) 22.dp else 40.dp)
                                .background(PathNodeCurrentTint),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(start = 10.dp, bottom = if (index < items.lastIndex) 10.dp else 0.dp)
                        .weight(1f),
                ) {
                    Text(
                        text = item.label,
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = PathInk,
                    )
                    if (item.detail.isNotBlank()) {
                        Text(
                            text = item.detail,
                            fontFamily = NunitoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = PathMuted,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Two or three things set against each other. Side by side, because that adjacency IS the point —
 * stacking them would just be a list.
 */
@Composable
private fun CompareDiagram(items: List<DiagramItem>) {
    val columns = items.take(3)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        columns.forEachIndexed { index, item ->
            val accent = COMPARE_ACCENTS[index % COMPARE_ACCENTS.size]
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.10f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.label,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    color = accent,
                )
                if (item.detail.isNotBlank()) {
                    Text(
                        text = item.detail,
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = PathInk.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

/** Labelled magnitudes. Bars are drawn relative to the largest value, which is the only honest
 * scale when the AI supplies numbers whose absolute units we can't verify. */
@Composable
private fun BarDiagram(items: List<DiagramItem>) {
    val largest = items.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = item.label,
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = PathInk,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = formatValue(item.value),
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = PathMuted,
                    )
                }
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    val radius = size.height / 2f
                    drawRoundRect(
                        color = PathChipNeutralBg,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                    )
                    val fraction = (item.value / largest).coerceIn(0f, 1f)
                    if (fraction > 0f) {
                        drawRoundRect(
                            color = PathNodeCurrent,
                            size = size.copy(width = size.width * fraction),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                        )
                    }
                }
                if (item.detail.isNotBlank()) {
                    Text(
                        text = item.detail,
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = PathMuted,
                    )
                }
            }
        }
    }
}

/** Whole numbers stay whole; anything else keeps one decimal. */
private fun formatValue(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else String.format("%.1f", value)

/** Distinct hues for compare columns, drawn from the roadmap's own node palette. */
private val COMPARE_ACCENTS = listOf(PathNodeCurrent, PathNodeDone, PathNodeBranch)

/**
 * A code sample, monospaced and horizontally scrollable.
 *
 * Scrolls rather than wraps: a wrapped line of code reads as two statements, which is worse than
 * having to swipe. The language sits in the corner so it never competes with the code itself.
 */
@Composable
fun LessonCode(code: LessonBlock.Code, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = PathInk,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (code.language.isNotBlank()) {
                Text(
                    text = code.language.uppercase(),
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
            Text(
                text = code.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = Color.White.copy(alpha = 0.92f),
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}

/** Centred caption styling shared by diagrams and images. */
@Composable
internal fun VisualCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        textAlign = TextAlign.Center,
        color = PathFaint,
        modifier = modifier,
    )
}

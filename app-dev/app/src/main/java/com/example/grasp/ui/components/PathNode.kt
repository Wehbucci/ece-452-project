package com.example.grasp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.ui.feature.path.PathNodeState
import com.example.grasp.ui.feature.path.PathNodeUi
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathCard
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeBranch
import com.example.grasp.ui.theme.PathNodeBranchBevel
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathNodeCurrentBevel
import com.example.grasp.ui.theme.PathNodeDone
import com.example.grasp.ui.theme.PathNodeDoneBevel
import com.example.grasp.ui.theme.PathNodeLockedBevel
import com.example.grasp.ui.theme.PathNodeLockedFill
import com.example.grasp.ui.theme.PathNodeLockedInk
import com.example.grasp.ui.theme.PathNodeOpenBevel
import com.example.grasp.ui.theme.PathScreenBg

/**
 * Fixed geometry shared by the node layer ([PathNode]), the connector layer (TreeCanvas) and
 * the Screen, so drawing, hit-testing and absolute positioning all agree on where a node is.
 *
 * A node is positioned by its CIRCLE CENTER. Each node occupies a fixed [SlotWidth]×slot with
 * the circle sitting in a [CircleBand] band below a [TagZone] (which holds the "YOU'RE HERE"
 * tag), so the center is a constant offset from the slot's top-left — see [circleCenterXInSlot]
 * / [circleCenterYInSlot]. The Screen offsets each slot by `center - thatConstant`.
 */
object PathLayout {
    /** Design canvas is 340 wide; lanes are expressed in this same coordinate space. */
    val CanvasWidth: Dp = 340.dp

    /** Y of the first row's node center, before any [RegionGap] a row-0 region adds. */
    val TopPadding: Dp = 78.dp

    /** Vertical distance between consecutive rows' node centers. */
    val RowSpacing: Dp = 148.dp

    /**
     * Extra vertical band inserted ABOVE every row that starts a region, reserved for its pill —
     * so the pill never sits behind the row's circle, its "YOU'RE HERE" tag, or the previous
     * row's label.
     */
    val RegionGap: Dp = 56.dp

    /** Slack below the last row so its label and any trailing branch node aren't clipped. */
    val BottomPadding: Dp = 110.dp

    /** Footprint of one node (tag + circle + label share this width). */
    val SlotWidth: Dp = 112.dp

    /** Vertical band reserved for the circle (fits the largest, 82.dp, current node). */
    val CircleBand: Dp = 84.dp

    /** Space above the circle reserved for the "YOU'RE HERE" tag. */
    val TagZone: Dp = 26.dp

    val circleCenterXInSlot: Dp = SlotWidth / 2
    val circleCenterYInSlot: Dp = TagZone + CircleBand / 2

    /** Node center X for a given lane (lanes are already in the 340-wide space). */
    fun centerX(lane: Int): Dp = lane.dp

    /**
     * Node center Y for a given row (graph depth). [regionRows] is the set of rows that start a
     * region — each one at or above [row] pushes this row down by one [RegionGap].
     */
    fun centerY(row: Int, regionRows: Set<Int>): Dp =
        TopPadding + RowSpacing * row + RegionGap * regionRows.count { it <= row }

    /** Total height of the scroll canvas for [rowCount] rows. */
    fun canvasHeight(rowCount: Int, regionRows: Set<Int>): Dp =
        centerY((rowCount - 1).coerceAtLeast(0), regionRows) + BottomPadding

    /** Circle diameter for each visual state (the "chunky game button" sizes). */
    fun circleSize(state: PathNodeState): Dp = when (state) {
        PathNodeState.CURRENT -> 82.dp
        PathNodeState.DONE -> 70.dp
        PathNodeState.OPEN -> 70.dp
        PathNodeState.LOCKED -> 64.dp
        PathNodeState.BRANCH -> 60.dp
    }
}

/**
 * A single tappable node on the journey — the chunky, bevelled "game button".
 *
 * Stateless: everything visual is a function of [node]. The only motion it owns is transient
 * and driven by keys the Screen bumps:
 *  - [shakeKey]  — bump to shake this node left/right (a locked tap "no").
 *  - [enterKey]  — bump to pop/bounce this node in (unlock, or a freshly-grown branch node).
 *
 * The current node additionally shows an infinitely pulsing ring and a bobbing "YOU'RE HERE"
 * tag. Placement (absolute offset by circle center) is the Screen's job via [PathLayout].
 */
@Composable
fun PathNode(
    node: PathNodeUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shakeKey: Int = 0,
    enterKey: Int = 0,
) {
    val density = LocalDensity.current
    val shakeAmplitude = with(density) { 10.dp.toPx() }

    // Pop/bounce on unlock or insert.
    val enterScale = remember { Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(enterKey) {
        if (enterKey > 0) {
            enterScale.snapTo(0.4f)
            enterScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
        }
    }

    // Left/right shake for a locked tap.
    val shakeX = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(shakeKey) {
        if (shakeKey > 0) {
            listOf(-1f, 1f, -0.8f, 0.8f, -0.4f, 0.4f, 0f).forEach { f ->
                shakeX.animateTo(f * shakeAmplitude, tween(durationMillis = 45))
            }
        }
    }

    Column(
        modifier = modifier.width(PathLayout.SlotWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Tag zone (only the current node fills it).
        Box(
            modifier = Modifier
                .height(PathLayout.TagZone)
                .width(PathLayout.SlotWidth),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (node.state == PathNodeState.CURRENT) YoureHereTag()
        }

        // The circle button.
        Box(
            modifier = Modifier
                .size(PathLayout.CircleBand)
                .graphicsLayer {
                    scaleX = enterScale.value
                    scaleY = enterScale.value
                    translationX = shakeX.value
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (node.state == PathNodeState.CURRENT) {
                PulseRing(PathLayout.circleSize(node.state))
            }
            NodeCircle(node.state)
            NodeGlyph(node.state)
        }

        Spacer(Modifier.height(6.dp))
        // Screen-bg backing so connector wires passing under the slot never run through the text.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(PathScreenBg, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = node.title,
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = when (node.state) {
                    PathNodeState.LOCKED -> PathFaint
                    PathNodeState.BRANCH -> PathMuted
                    else -> PathInk
                },
            )
            val sub = when {
                node.state == PathNodeState.BRANCH -> "grow the tree"
                node.estMinutes > 0 -> "${node.estMinutes} min"
                else -> null
            }
            if (sub != null) {
                Text(
                    text = sub,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = PathFaint,
                )
            }
        }
    }
}

/** The "YOU'RE HERE" pill above the current node, gently bobbing. */
@Composable
private fun YoureHereTag() {
    val bob by rememberInfiniteTransition(label = "bob").animateFloatValue(
        from = 0f, to = -3f, durationMillis = 900,
    )
    Surface(
        color = PathInk,
        contentColor = Color.White,
        shape = RoundedCornerShape(percent = 50),
        modifier = Modifier.graphicsLayer { translationY = bob },
    ) {
        Text(
            text = "YOU'RE HERE",
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** The expanding, fading ring that says "act here". */
@Composable
private fun PulseRing(diameter: Dp) {
    val t by rememberInfiniteTransition(label = "pulse").animateFloatValue(
        from = 0f, to = 1f, durationMillis = 1700, easing = FastOutSlowInEasing,
    )
    Canvas(Modifier.size(diameter)) {
        val base = size.minDimension / 2f
        drawCircle(
            color = PathNodeCurrent,
            radius = base * (1f + 0.35f * t),
            style = Stroke(width = 3.dp.toPx()),
            alpha = (1f - t).coerceIn(0f, 1f),
        )
    }
}

/** Draws the state's fill, hard "game bevel", optional border and soft glow. */
@Composable
private fun NodeCircle(state: PathNodeState) {
    val diameter = PathLayout.circleSize(state)
    Canvas(Modifier.size(PathLayout.CircleBand)) {
        val r = diameter.toPx() / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val bevelY = (if (state == PathNodeState.CURRENT) 6.dp else 5.dp).toPx()

        val glow = when (state) {
            PathNodeState.DONE -> PathNodeDone.copy(alpha = 0.20f)
            PathNodeState.CURRENT -> PathNodeCurrent.copy(alpha = 0.22f)
            else -> null
        }
        if (glow != null) drawCircle(glow, radius = r * 1.3f, center = center)

        val (fill, bevel) = when (state) {
            PathNodeState.DONE -> PathNodeDone to PathNodeDoneBevel
            PathNodeState.CURRENT -> PathNodeCurrent to PathNodeCurrentBevel
            PathNodeState.OPEN -> PathCard to PathNodeOpenBevel
            PathNodeState.LOCKED -> PathNodeLockedFill to PathNodeLockedBevel
            PathNodeState.BRANCH -> PathCard to PathNodeBranchBevel
        }
        // Hard (0-blur) bevel directly beneath, then the fill on top.
        drawCircle(bevel, radius = r, center = center.copy(y = center.y + bevelY))
        drawCircle(fill, radius = r, center = center)

        // Borders: solid indigo for OPEN, dashed amber for BRANCH.
        when (state) {
            PathNodeState.OPEN -> drawCircle(
                PathNodeCurrent, radius = r, center = center, style = Stroke(3.dp.toPx()),
            )
            PathNodeState.BRANCH -> drawCircle(
                PathNodeBranch, radius = r, center = center,
                style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 9f))),
            )
            else -> Unit
        }
    }
}

/** The centered glyph: check / star / lesson-book / lock / plus. */
@Composable
private fun NodeGlyph(state: PathNodeState) {
    val glyphSize = PathLayout.circleSize(state) * 0.42f
    when (state) {
        PathNodeState.DONE ->
            Icon(Icons.Filled.Check, contentDescription = "Completed", tint = Color.White, modifier = Modifier.size(glyphSize))
        PathNodeState.CURRENT ->
            Icon(Icons.Filled.Star, contentDescription = "You are here", tint = Color.White, modifier = Modifier.size(glyphSize))
        PathNodeState.LOCKED ->
            Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = PathNodeLockedInk, modifier = Modifier.size(glyphSize))
        PathNodeState.BRANCH ->
            Icon(Icons.Filled.Add, contentDescription = "Grow your path", tint = PathNodeBranch, modifier = Modifier.size(glyphSize))
        PathNodeState.OPEN ->
            LessonBookGlyph(modifier = Modifier.size(glyphSize))
    }
}

/** A simple stroked "open book" lesson glyph (MenuBook isn't in material-icons-core). */
@Composable
private fun LessonBookGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = (w * 0.09f).coerceAtLeast(2f)
        drawRoundRect(
            color = PathNodeCurrent,
            topLeft = Offset(w * 0.16f, h * 0.22f),
            size = Size(w * 0.68f, h * 0.56f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
            style = Stroke(width = sw),
        )
        // Spine down the middle turns the "card" into a "book".
        drawLine(
            color = PathNodeCurrent,
            start = Offset(w * 0.5f, h * 0.22f),
            end = Offset(w * 0.5f, h * 0.78f),
            strokeWidth = sw,
        )
    }
}

// ── tiny infinite-transition helper so the call sites above stay readable ──────────────────
@Composable
private fun androidx.compose.animation.core.InfiniteTransition.animateFloatValue(
    from: Float,
    to: Float,
    durationMillis: Int,
    easing: androidx.compose.animation.core.Easing = FastOutSlowInEasing,
) = animateFloat(
    initialValue = from,
    targetValue = to,
    animationSpec = infiniteRepeatable(tween(durationMillis, easing = easing), RepeatMode.Reverse),
    label = "infFloat",
)

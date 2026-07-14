package com.example.grasp.ui.feature.path

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.grasp.ui.components.PathLayout
import com.example.grasp.ui.theme.PathConnector
import com.example.grasp.ui.theme.PathNodeBranch
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathNodeDone

/**
 * The connector layer that sits BEHIND the node buttons — the "wires" of the skill tree.
 *
 * For every parent→child edge it draws a vertical cubic Bézier `M ax,ay C ax,my bx,my bx,by`
 * (with `my = (ay+by)/2`), which gives the smooth S-curve that reads as a Duolingo path when a
 * child sits in a different lane, and a clean fork/merge at the splits and converges. It shares
 * [PathLayout] with the node layer so a wire always meets a node's exact center.
 *
 * Edges are colored by the two endpoints' states (README §"Node states"):
 *  - both DONE → green · parent DONE → indigo · edge into a BRANCH node → amber dotted · else grey.
 *
 * When a completion unlocks the next node, [fillIntoId] names that newly-current node and the
 * edges arriving into it animate their colored stroke in over the grey base — the "connector
 * fills" moment.
 */
@Composable
fun TreeCanvas(
    nodes: List<PathNodeUi>,
    fillIntoId: String?,
    modifier: Modifier = Modifier,
) {
    val byId = remember(nodes) { nodes.associateBy { it.id } }

    // 0→1 reveal for the edges arriving into the newly-unlocked node.
    val fillProgress = remember { Animatable(1f) }
    LaunchedEffect(fillIntoId) {
        if (fillIntoId != null) {
            fillProgress.snapTo(0f)
            fillProgress.animateTo(1f, tween(durationMillis = 750))
        }
    }

    Canvas(modifier.fillMaxSize()) {
        fun centerOf(n: PathNodeUi) = Offset(
            x = PathLayout.centerX(n.lane).toPx(),
            y = PathLayout.centerY(n.row).toPx(),
        )

        // Pass 1: grey base for every edge, so colored fills reveal on top of a visible track.
        nodes.forEach { child ->
            val childCenter = centerOf(child)
            child.parentIds.forEach { pid ->
                val parent = byId[pid] ?: return@forEach
                drawConnector(centerOf(parent), childCenter, PathConnector, 5f, dotted = false)
            }
        }

        // Pass 2: colored/branch edges on top.
        nodes.forEach { child ->
            val childCenter = centerOf(child)
            child.parentIds.forEach { pid ->
                val parent = byId[pid] ?: return@forEach
                val parentCenter = centerOf(parent)

                if (child.state == PathNodeState.BRANCH) {
                    drawConnector(parentCenter, childCenter, PathNodeBranch, 4f, dotted = true)
                    return@forEach
                }
                val color = when {
                    parent.state == PathNodeState.DONE && child.state == PathNodeState.DONE -> PathNodeDone
                    parent.state == PathNodeState.DONE -> PathNodeCurrent
                    else -> null // stays grey (already drawn in pass 1)
                } ?: return@forEach

                val alpha = if (child.id == fillIntoId) fillProgress.value else 1f
                drawConnector(parentCenter, childCenter, color, 6f, dotted = false, alpha = alpha)
            }
        }
    }
}

/** Draws one vertical cubic-Bézier wire between two node centers. */
private fun DrawScope.drawConnector(
    from: Offset,
    to: Offset,
    color: androidx.compose.ui.graphics.Color,
    widthDp: Float,
    dotted: Boolean,
    alpha: Float = 1f,
) {
    val midY = (from.y + to.y) / 2f
    val path = Path().apply {
        moveTo(from.x, from.y)
        cubicTo(from.x, midY, to.x, midY, to.x, to.y)
    }
    drawPath(
        path = path,
        color = color,
        alpha = alpha,
        style = Stroke(
            width = widthDp * density,
            cap = StrokeCap.Round,
            pathEffect = if (dotted) PathEffect.dashPathEffect(floatArrayOf(2f, 14f)) else null,
        ),
    )
}

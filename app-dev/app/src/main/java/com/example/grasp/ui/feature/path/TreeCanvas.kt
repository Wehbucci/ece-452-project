package com.example.grasp.ui.feature.path

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.data.model.NodeState
import com.example.grasp.data.model.TreeNode
import com.example.grasp.ui.theme.NodeActive
import com.example.grasp.ui.theme.NodeBranchOut
import com.example.grasp.ui.theme.NodeCompleted
import com.example.grasp.ui.theme.NodeUnexplored

/**
 * The visual learning-tree renderer. 
 * Draws the roadmap as a top-down layered graph and reports node taps.
 *
 * Layout: nodes are placed in rows by their DEPTH (longest path from a root); nodes sharing
 * a depth are spread evenly across that row. Positions are stored as fractions of the canvas
 * size so the same math drives both drawing and tap hit-testing.
 *
 * This is a deliberately simple layout good enough for the skeleton; a future version can add
 * smarter spacing, panning/zoom, and curved edges.
 *
 * @param nodes the roadmap nodes
 * @param states pre-computed visual state per node id (see `deriveNodeStates`)
 * @param onNodeClick invoked with the tapped node
 */
@Composable
fun TreeCanvas(
    nodes: List<TreeNode>,
    states: Map<String, NodeState>,
    onNodeClick: (TreeNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (nodes.isEmpty()) return

    // Compute the layered layout once per node-list change.
    val layout = remember(nodes) { computeTreeLayout(nodes) }
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            // One row of vertical space per depth level so deep trees stay readable.
            .height((layout.rowCount * ROW_HEIGHT_DP).dp)
            .pointerInput(layout) {
                detectTapGestures { tap ->
                    val radius = NODE_RADIUS_DP.dp.toPx()
                    layout.placements
                        .firstOrNull { p ->
                            val center = Offset(p.fx * size.width, p.fy * size.height)
                            (tap - center).getDistance() <= radius * 1.25f
                        }
                        ?.let { onNodeClick(it.node) }
                }
            },
    ) {
        val radius = NODE_RADIUS_DP.dp.toPx()
        val centers = layout.placements.associate { p ->
            p.node.id to Offset(p.fx * size.width, p.fy * size.height)
        }

        // 1) Edges first so nodes sit on top.
        layout.placements.forEach { p ->
            val from = centers.getValue(p.node.id)
            p.node.children.forEach { childId ->
                val to = centers[childId] ?: return@forEach
                val leadsToBranchOut = states[childId] == NodeState.BRANCH_OUT
                drawLine(
                    color = NodeUnexplored.copy(alpha = 0.6f),
                    start = from,
                    end = to,
                    strokeWidth = 3f,
                    pathEffect = if (leadsToBranchOut) {
                        PathEffect.dashPathEffect(floatArrayOf(14f, 12f))
                    } else null,
                )
            }
        }

        // 2) Nodes + labels.
        layout.placements.forEach { p ->
            val center = centers.getValue(p.node.id)
            when (states[p.node.id]) {
                NodeState.COMPLETED -> drawCircle(NodeCompleted, radius, center)
                NodeState.ACTIVE -> {
                    drawCircle(NodeActive, radius, center)
                    // A halo to draw the eye to where the user is right now.
                    drawCircle(NodeActive.copy(alpha = 0.25f), radius * 1.5f, center)
                }
                NodeState.BRANCH_OUT -> drawCircle(
                    color = NodeBranchOut,
                    radius = radius,
                    center = center,
                    style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
                )
                else -> drawCircle(NodeUnexplored, radius, center)
            }

            // Label under the node.
            val labelColor = if (states[p.node.id] == NodeState.BRANCH_OUT) NodeBranchOut else Color.Black
            val measured = measurer.measure(
                text = p.node.title,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                ),
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                constraints = Constraints(maxWidth = (radius * 3.4f).toInt().coerceAtLeast(1)),
            )
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = center.x - measured.size.width / 2f,
                    y = center.y + radius + 6.dp.toPx(),
                ),
            )
        }
    }
}

private const val ROW_HEIGHT_DP = 130
private const val NODE_RADIUS_DP = 28

/** A node's normalized position: [fx]/[fy] are 0f..1f fractions of the canvas size. */
private data class NodePlacement(val node: TreeNode, val fx: Float, val fy: Float)

private class TreeLayout(val placements: List<NodePlacement>, val rowCount: Int)

/**
 * Assigns every node a row (by depth) and a horizontal slot within that row. Depth is the
 * longest path from a root, so a node always sits below all of its parents.
 */
private fun computeTreeLayout(nodes: List<TreeNode>): TreeLayout {
    // Build the parent lists so we can compute depth from incoming edges.
    val parents = HashMap<String, MutableList<String>>()
    nodes.forEach { node ->
        if (node.parentId != null) {
            parents.getOrPut(node.id) { mutableListOf() }.add(node.parentId)
        }
        node.children.forEach { childId ->
            parents.getOrPut(childId) { mutableListOf() }.add(node.id)
        }
    }

    val depthCache = HashMap<String, Int>()
    fun depthOf(id: String): Int {
        depthCache[id]?.let { return it }
        val ps = parents[id]
        val d = if (ps.isNullOrEmpty()) 0 else ps.maxOf { depthOf(it) } + 1
        depthCache[id] = d
        return d
    }

    val byDepth = nodes.groupBy { depthOf(it.id) }
    val maxDepth = byDepth.keys.maxOrNull() ?: 0
    val rowCount = maxDepth + 1

    val placements = byDepth.flatMap { (depth, rowNodes) ->
        rowNodes.mapIndexed { index, node ->
            NodePlacement(
                node = node,
                fx = (index + 0.5f) / rowNodes.size,
                fy = (depth + 0.5f) / rowCount,
            )
        }
    }
    return TreeLayout(placements, rowCount)
}

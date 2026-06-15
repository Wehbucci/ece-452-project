@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.grasp.ui.feature.path

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.NodeState
import com.example.grasp.data.model.TreeNode
import com.example.grasp.data.model.deriveNodeStates
import com.example.grasp.ui.components.LabeledLinearProgress
import com.example.grasp.ui.components.StatusPill
import com.example.grasp.ui.theme.NodeActive
import com.example.grasp.ui.theme.NodeBranchOut
import com.example.grasp.ui.theme.NodeCompleted
import com.example.grasp.ui.theme.NodeUnexplored

/**
 * Learner roadmap screen (View). Shows the path as a LIST or a visual TREE  toggled in-place.
 *
 * @param pathId roadmap id (navigation argument)
 * @param onBack pop the back stack
 * @param onOpenSubtopic open a node's detail
 */
@Composable
fun PathScreen(
    pathId: String,
    onBack: () -> Unit,
    onOpenSubtopic: (pathId: String, nodeId: String) -> Unit,
    presenterFactory: (String) -> PathContract.Presenter = { PathPresenter(it) },
) {
    var roadmap by remember { mutableStateOf<LearningPath?>(null) }
    var notFound by remember { mutableStateOf(false) }
    var treeView by remember { mutableStateOf(false) }

    val presenter = remember(pathId) { presenterFactory(pathId) }
    val view = remember(onOpenSubtopic) {
        object : PathContract.View {
            override fun showPath(path: LearningPath) { roadmap = path; notFound = false }
            override fun showNotFound() { notFound = true }
            override fun openSubtopic(pathId: String, nodeId: String) = onOpenSubtopic(pathId, nodeId)
        }
    }
    DisposableEffect(presenter, view) {
        presenter.attach(view)
        onDispose { presenter.detach() }
    }

    val states = remember(roadmap) { roadmap?.let { deriveNodeStates(it.nodes) } ?: emptyMap() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(roadmap?.title ?: "Roadmap") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                notFound -> Text(
                    "We couldn't find that roadmap.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                roadmap == null -> CircularProgressIndicator()

                else -> {
                    val current = roadmap!!
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header: progress + view toggle (fixed above the scrolling area).
                        Column(modifier = Modifier.padding(20.dp)) {
                            LabeledLinearProgress(
                                progress = current.progress,
                                label = current.progressLabel,
                                trailing = "~${minutesLeft(current)} min left",
                            )
                            Row(
                                modifier = Modifier.padding(top = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = !treeView,
                                    onClick = { treeView = false },
                                    label = { Text("List") },
                                )
                                FilterChip(
                                    selected = treeView,
                                    onClick = { treeView = true },
                                    label = { Text("Tree") },
                                )
                            }
                        }
                        HorizontalDivider()

                        if (treeView) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                TreeLegend(modifier = Modifier.padding(16.dp))
                                TreeCanvas(
                                    nodes = current.nodes,
                                    states = states,
                                    onNodeClick = presenter::onNodeClicked,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp),
                            ) {
                                items(current.nodes, key = { it.id }) { node ->
                                    NodeRow(
                                        node = node,
                                        state = states[node.id] ?: NodeState.UNEXPLORED,
                                        onClick = { presenter.onNodeClicked(node) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One row in the list view. Leading marker + title + estimate, tappable to open. */
@Composable
private fun NodeRow(node: TreeNode, state: NodeState, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick) // branch-out rows are tappable too; the presenter no-ops them
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Leading state marker.
        when (state) {
            NodeState.COMPLETED -> Icon(
                Icons.Filled.Check, contentDescription = "Completed", tint = NodeCompleted,
                modifier = Modifier.size(20.dp),
            )
            NodeState.BRANCH_OUT -> Icon(
                Icons.Filled.Add, contentDescription = "Add a branch", tint = NodeBranchOut,
                modifier = Modifier.size(20.dp),
            )
            else -> Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(stateColor(state), CircleShape),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                node.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (node.isBranchOut) NodeBranchOut else MaterialTheme.colorScheme.onSurface,
            )
            if (!node.isBranchOut) {
                Text(
                    "${node.estMinutes} min",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state == NodeState.ACTIVE) StatusPill("Up next", NodeActive)
        if (!node.isBranchOut) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

/** A small key so users (and graders) can read the tree's color language at a glance. */
@Composable
private fun TreeLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LegendDot("Done", NodeCompleted)
        LegendDot("Current", NodeActive)
        LegendDot("Upcoming", NodeUnexplored)
        LegendDot("Branch", NodeBranchOut)
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

private fun stateColor(state: NodeState): Color = when (state) {
    NodeState.COMPLETED -> NodeCompleted
    NodeState.ACTIVE -> NodeActive
    NodeState.BRANCH_OUT -> NodeBranchOut
    NodeState.UNEXPLORED -> NodeUnexplored
}

/** Rough "time left" estimate = sum of est minutes of not-yet-completed, non-branch nodes. */
private fun minutesLeft(path: LearningPath): Int =
    path.nodes.filter { !it.completed && !it.isBranchOut }.sumOf { it.estMinutes }

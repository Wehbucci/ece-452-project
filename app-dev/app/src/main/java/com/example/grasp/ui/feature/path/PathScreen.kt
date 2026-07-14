@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.grasp.ui.feature.path

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.grasp.data.model.Subtopic
import com.example.grasp.ui.components.ConfettiBurst
import com.example.grasp.ui.components.LevelUpRibbon
import com.example.grasp.ui.components.PathHud
import com.example.grasp.ui.components.PathLayout
import com.example.grasp.ui.components.PathNode
import com.example.grasp.ui.components.PathToast
import com.example.grasp.ui.components.RegionLabel
import com.example.grasp.ui.feature.subtopic.SubtopicSheetContent
import com.example.grasp.ui.theme.GraspTheme
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathScreenBg

/**
 * The gamified Learner roadmap (View) — a vertical, Duolingo-meets-skill-tree journey.
 *
 * Wired exactly like every Grasp screen (see `LoginScreen`): the Composable owns small UI state,
 * remembers a [PathContract.Presenter], builds an anonymous [PathContract.View] that only writes
 * that state, and attaches/detaches in a `DisposableEffect`. ALL logic lives in the presenter;
 * this file just paints [PathUiState] and plays the one-shot motion the presenter requests.
 *
 * The subtopic detail and the "grow your path" flows are Material 3 [ModalBottomSheet]s hosted
 * here (not separate routes) so Mark-complete flows straight into the same XP/unlock/confetti
 * pipeline as the board.
 *
 * @param pathId roadmap id (navigation argument).
 * @param onBack pop the back stack.
 * @param onOpenChat route to the existing AI chat ("Ask AI" in the detail sheet).
 */
@Composable
fun PathScreen(
    pathId: String,
    onBack: () -> Unit,
    onOpenChat: (context: String, pathId: String, nodeId: String) -> Unit,
    presenterFactory: (String) -> PathContract.Presenter = { PathPresenter(it) },
) {
    // (1) UI state
    var state by remember { mutableStateOf<PathUiState?>(null) }
    var notFound by remember { mutableStateOf(false) }

    // Sheets
    var sheetSubtopic by remember { mutableStateOf<Subtopic?>(null) }
    var sheetCompleted by remember { mutableStateOf(false) }
    var branchSheet by remember { mutableStateOf(false) }

    // One-shot motion signals
    var confettiTrigger by remember { mutableIntStateOf(0) }
    var levelUp by remember { mutableStateOf<Int?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var fillIntoId by remember { mutableStateOf<String?>(null) }
    var shakeId by remember { mutableStateOf<String?>(null) }
    var shakeNonce by remember { mutableIntStateOf(0) }
    var enterId by remember { mutableStateOf<String?>(null) }
    var enterNonce by remember { mutableIntStateOf(0) }

    val uriHandler = LocalUriHandler.current

    // (2) Presenter
    val presenter = remember(pathId) { presenterFactory(pathId) }

    // (3) View
    val view = remember(onOpenChat) {
        object : PathContract.View {
            override fun showPath(s: PathUiState) { state = s; notFound = false }
            override fun showNotFound() { notFound = true }
            override fun showSubtopicSheet(subtopic: Subtopic, completed: Boolean) {
                branchSheet = false
                sheetSubtopic = subtopic
                sheetCompleted = completed
            }
            override fun showBranchSheet() { sheetSubtopic = null; branchSheet = true }
            override fun dismissSheet() { sheetSubtopic = null; branchSheet = false }
            override fun playUnlock(nodeId: String) { fillIntoId = nodeId; enterId = nodeId; enterNonce++ }
            override fun playPopIn(nodeId: String) { enterId = nodeId; enterNonce++ }
            override fun showConfetti() { confettiTrigger++ }
            override fun showLevelUp(level: Int) { levelUp = level }
            override fun showToast(message: String) { toast = message }
            override fun shakeNode(nodeId: String) { shakeId = nodeId; shakeNonce++ }
            override fun openChat(context: String, pathId: String, nodeId: String) {
                sheetSubtopic = null
                onOpenChat(context, pathId, nodeId)
            }
        }
    }

    // (4) Attach / detach
    DisposableEffect(presenter, view) {
        presenter.attach(view)
        onDispose { presenter.detach() }
    }

    // (5) The dumb UI
    Box(Modifier.fillMaxSize().background(PathScreenBg)) {
        when {
            notFound -> Text(
                "We couldn't find that roadmap.",
                style = MaterialTheme.typography.bodyLarge,
                color = PathMuted,
                modifier = Modifier.align(Alignment.Center),
            )

            state == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            else -> {
                val s = state!!
                Column(Modifier.fillMaxSize()) {
                    PathHud(
                        title = s.title,
                        masteredCount = s.masteredCount,
                        totalLessons = s.totalLessons,
                        streak = s.streak,
                        level = s.level,
                        xpInLevel = s.xpInLevel,
                        xpPerLevel = s.xpPerLevel,
                        xpFraction = s.xpFraction,
                        onBack = onBack,
                    )
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        JourneyBoard(
                            state = s,
                            fillIntoId = fillIntoId,
                            shakeId = shakeId,
                            shakeNonce = shakeNonce,
                            enterId = enterId,
                            enterNonce = enterNonce,
                            onNodeTapped = presenter::onNodeTapped,
                        )
                        // Non-scrolling celebratory overlays.
                        ConfettiBurst(confettiTrigger, Modifier.fillMaxSize())
                        LevelUpRibbon(levelUp, onFinished = { levelUp = null })
                        PathToast(toast, onFinished = { toast = null })
                    }
                }
            }
        }
    }

    // Subtopic detail sheet.
    val subtopic = sheetSubtopic
    if (subtopic != null) {
        ModalBottomSheet(
            onDismissRequest = { sheetSubtopic = null; presenter.onSheetDismissed() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            SubtopicSheetContent(
                subtopic = subtopic,
                completed = sheetCompleted,
                onMarkComplete = { presenter.onMarkComplete(subtopic.nodeId) },
                onAskAi = { presenter.onAskAi(subtopic.nodeId) },
                onOpenResource = { url -> uriHandler.openUri(url) },
            )
        }
    }

    // Branch-out sheet.
    if (branchSheet) {
        ModalBottomSheet(
            onDismissRequest = { branchSheet = false; presenter.onSheetDismissed() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            BranchSheetContent(onGenerate = { presenter.onGenerateBranch(it) })
        }
    }
}

/**
 * The scrolling journey itself: a fixed-width (340dp) canvas with the connector layer behind
 * absolutely-positioned region pills and node buttons. Pure rendering of [state].
 */
@Composable
private fun JourneyBoard(
    state: PathUiState,
    fillIntoId: String?,
    shakeId: String?,
    shakeNonce: Int,
    enterId: String?,
    enterNonce: Int,
    onNodeTapped: (String) -> Unit,
) {
    val canvasHeight = PathLayout.canvasHeight(state.rowCount)
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeight),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.width(PathLayout.CanvasWidth).height(canvasHeight)) {
                // 1) Connectors behind everything.
                TreeCanvas(
                    nodes = state.nodes,
                    fillIntoId = fillIntoId,
                    modifier = Modifier.fillMaxSize(),
                )

                // 2) Region pills, centered, anchored above their region's first row.
                state.regions.forEach { region ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .offset(y = PathLayout.centerY(region.row) - REGION_PILL_GAP),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        RegionLabel(region.label)
                    }
                }

                // 3) Node buttons, positioned by circle center.
                state.nodes.forEach { node ->
                    PathNode(
                        node = node,
                        onClick = { onNodeTapped(node.id) },
                        shakeKey = if (node.id == shakeId) shakeNonce else 0,
                        enterKey = if (node.id == enterId) enterNonce else 0,
                        modifier = Modifier.offset(
                            x = PathLayout.centerX(node.lane) - PathLayout.circleCenterXInSlot,
                            y = PathLayout.centerY(node.row) - PathLayout.circleCenterYInSlot,
                        ),
                    )
                }
            }
        }
    }
}

private val REGION_PILL_GAP = 54.dp

// ── Preview ────────────────────────────────────────────────────────────────────────────────
// A hand-built [PathUiState] so the static preview shows the real journey (the MVP path loads
// its data in a DisposableEffect, which non-interactive previews don't run).
@Preview(showBackground = true, heightDp = 900)
@Composable
private fun PathScreenPreview() {
    fun n(id: String, title: String, est: Int, st: PathNodeState, lane: Int, row: Int, parents: List<String>) =
        PathNodeUi(id, title, est, st, lane, row, parents)

    val sample = PathUiState(
        title = "Machine Learning",
        nodes = listOf(
            n("a", "What is ML?", 5, PathNodeState.DONE, 170, 0, emptyList()),
            n("b", "Types of Learning", 8, PathNodeState.DONE, 108, 1, listOf("a")),
            n("c", "Data Basics", 10, PathNodeState.DONE, 230, 2, listOf("b")),
            n("d", "Supervised", 12, PathNodeState.CURRENT, 96, 3, listOf("c")),
            n("e", "Unsupervised", 12, PathNodeState.OPEN, 244, 3, listOf("c")),
            n("f", "Regression", 10, PathNodeState.LOCKED, 96, 4, listOf("d")),
            n("g", "Clustering", 10, PathNodeState.LOCKED, 244, 4, listOf("e")),
            n("h", "Branch out", 0, PathNodeState.BRANCH, 170, 5, listOf("f")),
        ),
        regions = listOf(
            RegionUi("FOUNDATIONS", 0),
            RegionUi("CORE ML · PICK A TRACK", 3),
        ),
        masteredCount = 3,
        totalLessons = 7,
        streak = 6,
        level = 1,
        xpInLevel = 120,
        xpPerLevel = 200,
        xpFraction = 0.6f,
        rowCount = 6,
    )

    GraspTheme {
        Column(Modifier.fillMaxSize().background(PathScreenBg)) {
            PathHud(
                title = sample.title,
                masteredCount = sample.masteredCount,
                totalLessons = sample.totalLessons,
                streak = sample.streak,
                level = sample.level,
                xpInLevel = sample.xpInLevel,
                xpPerLevel = sample.xpPerLevel,
                xpFraction = sample.xpFraction,
                onBack = {},
            )
            Box(Modifier.weight(1f).fillMaxSize()) {
                JourneyBoard(
                    state = sample,
                    fillIntoId = null,
                    shakeId = null,
                    shakeNonce = 0,
                    enterId = null,
                    enterNonce = 0,
                    onNodeTapped = {},
                )
            }
        }
    }
}

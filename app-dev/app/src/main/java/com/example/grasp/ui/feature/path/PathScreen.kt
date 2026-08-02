@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.grasp.ui.feature.path

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import com.example.grasp.ui.feature.chat.ChatOverlay
import com.example.grasp.ui.feature.chat.ChatRequest
import com.example.grasp.ui.feature.chat.ChatScope
import com.example.grasp.ui.feature.subtopic.SubtopicLoadingContent
import com.example.grasp.ui.feature.subtopic.SectionShape
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
 * here (not separate routes) so Mark-complete flows straight into the same XP/advance/confetti
 * pipeline as the board.
 *
 * @param pathId roadmap id (navigation argument).
 * @param onBack pop the back stack.
 */
@Composable
fun PathScreen(
    pathId: String,
    onBack: () -> Unit,
    presenterFactory: (String) -> PathContract.Presenter = { PathPresenter(it) },
) {
    // (1) UI state
    var state by remember { mutableStateOf<PathUiState?>(null) }
    var notFound by remember { mutableStateOf(false) }

    // Sheets. `sheetLoadingTitle` holds the detail sheet open, showing a spinner, while the node's
    // lesson is generated; `sheetSubtopic` replaces it once the content arrives.
    var sheetLoadingTitle by remember { mutableStateOf<String?>(null) }
    var sheetGenerating by remember { mutableStateOf(false) }
    var sheetSubtopic by remember { mutableStateOf<Subtopic?>(null) }
    var sheetCompleted by remember { mutableStateOf(false) }
    var sheetEditing by remember { mutableStateOf(false) }
    var sheetCanUndo by remember { mutableStateOf(false) }
    var sheetSection by remember { mutableStateOf<SectionShape?>(null) }
    var branchSheet by remember { mutableStateOf(false) }
    var branchFromTitle by remember { mutableStateOf("") }
    var branchSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var branchGenerating by remember { mutableStateOf(false) }

    // The section the board is asking about, between the tap that picked it and the answer.
    var deleteTarget by remember { mutableStateOf<DeleteTarget?>(null) }

    // The tutor, layered over the board rather than replacing it.
    var chat by remember { mutableStateOf<ChatRequest?>(null) }

    // One-shot motion signals
    var confettiTrigger by remember { mutableIntStateOf(0) }
    var levelUp by remember { mutableStateOf<Int?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var fillIntoId by remember { mutableStateOf<String?>(null) }
    var enterId by remember { mutableStateOf<String?>(null) }
    var enterNonce by remember { mutableIntStateOf(0) }

    val uriHandler = LocalUriHandler.current

    // (2) Presenter
    val presenter = remember(pathId) { presenterFactory(pathId) }

    // (3) View
    val view = remember {
        object : PathContract.View {
            override fun showPath(s: PathUiState) { state = s; notFound = false }
            override fun showNotFound() { notFound = true }
            override fun showSubtopicLoading(title: String, generating: Boolean) {
                branchSheet = false
                sheetSubtopic = null
                sheetGenerating = generating
                sheetLoadingTitle = title
            }
            override fun showSubtopicSheet(
                subtopic: Subtopic,
                completed: Boolean,
                editing: Boolean,
                canUndo: Boolean,
                section: SectionShape?,
            ) {
                branchSheet = false
                sheetLoadingTitle = null
                sheetSubtopic = subtopic
                sheetCompleted = completed
                sheetEditing = editing
                sheetCanUndo = canUndo
                sheetSection = section
            }
            override fun showBranchSheet(fromTitle: String) {
                sheetSubtopic = null
                sheetLoadingTitle = null
                branchSuggestions = emptyList()
                branchGenerating = false
                branchFromTitle = fromTitle
                branchSheet = true
            }
            override fun showBranchSuggestions(topics: List<String>) { branchSuggestions = topics }
            override fun showBranchGenerating(generating: Boolean) { branchGenerating = generating }
            override fun confirmDeleteSection(
                nodeId: String,
                title: String,
                hasDescendants: Boolean,
            ) {
                deleteTarget = DeleteTarget(nodeId, title, hasDescendants)
            }
            override fun dismissSheet() {
                sheetSubtopic = null
                sheetLoadingTitle = null
                branchSheet = false
            }
            override fun playAdvance(nodeId: String) { fillIntoId = nodeId; enterId = nodeId; enterNonce++ }
            override fun playPopIn(nodeId: String) { enterId = nodeId; enterNonce++ }
            override fun showConfetti() { confettiTrigger++ }
            override fun showLevelUp(level: Int) { levelUp = level }
            override fun showToast(message: String) { toast = message }
            override fun openChat(context: String, pathId: String, nodeId: String, blockId: String) {
                // The detail sheet still has to close first. A ModalBottomSheet is its own window
                // on some versions and would sit above the overlay, so leaving it open would bury
                // the chat rather than layer it.
                sheetSubtopic = null
                sheetLoadingTitle = null
                chat = ChatRequest(
                    context = context,
                    scope = ChatScope.of(pathId = pathId, nodeId = nodeId, blockId = blockId),
                )
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
                            enterId = enterId,
                            enterNonce = enterNonce,
                            onNodeTapped = presenter::onNodeTapped,
                        )
                        // Non-scrolling celebratory overlays.
                        ConfettiBurst(confettiTrigger, Modifier.fillMaxSize())
                        LevelUpRibbon(levelUp, onFinished = { levelUp = null })
                        PathToast(toast, onFinished = { toast = null })

                        // The tutor for the whole plan rather than one lesson. Stands down while
                        // the board is a picker, so "tap a section" has the screen to itself.
                        if (s.boardMode == BoardMode.BROWSING) {
                            AskRoadmapButton(
                                onClick = presenter::onAskAboutRoadmap,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .navigationBarsPadding()
                                    .padding(24.dp),
                            )
                        }

                        // Reshaping the roadmap: one quiet control, its menu, and the banner for
                        // whichever "tap a section" question is being asked.
                        RoadmapEditBar(
                            mode = s.boardMode,
                            movingTitle = s.movingTitle,
                            onEditRequested = presenter::onEditRoadmapRequested,
                            onAdd = presenter::onAddSectionChosen,
                            onMove = presenter::onMoveSectionChosen,
                            onDelete = presenter::onDeleteSectionChosen,
                            onCancel = presenter::onBoardEditCancelled,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(12.dp),
                        )
                    }
                }
            }
        }
    }

    // Subtopic detail sheet — the same sheet holds the "writing your lesson" state and the lesson.
    val subtopic = sheetSubtopic
    val loadingTitle = sheetLoadingTitle
    if (subtopic != null || loadingTitle != null) {
        ModalBottomSheet(
            onDismissRequest = {
                sheetSubtopic = null
                sheetLoadingTitle = null
                presenter.onSheetDismissed()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            if (subtopic != null) {
                SubtopicSheetContent(
                    subtopic = subtopic,
                    completed = sheetCompleted,
                    onMarkComplete = { presenter.onMarkComplete(subtopic.nodeId) },
                    onAskAi = { presenter.onAskAi(subtopic.nodeId) },
                    onAskAboutBlock = { blockId, text ->
                        presenter.onAskAboutBlock(subtopic.nodeId, text, blockId)
                    },
                    onBranchOut = { presenter.onBranchFromNode(subtopic.nodeId) },
                    onOpenResource = { url -> uriHandler.openUri(url) },
                    editing = sheetEditing,
                    canUndo = sheetCanUndo,
                    onToggleEdit = { presenter.onToggleEditMode() },
                    onEdit = { presenter.onLessonEdit(it) },
                    onUndo = { presenter.onUndoLessonEdit() },
                    section = sheetSection,
                    onRoadmapEdit = { presenter.onRoadmapEdit(it) },
                )
            } else {
                SubtopicLoadingContent(title = loadingTitle!!, generating = sheetGenerating)
            }
        }
    }

    // Deleting a section, asked out on the board where it was picked.
    deleteTarget?.let { target ->
        DeleteSectionDialog(
            target = target,
            onDelete = { withDescendants ->
                deleteTarget = null
                presenter.onDeleteSectionConfirmed(target.nodeId, withDescendants)
            },
            onDismiss = { deleteTarget = null },
        )
    }

    // Branch-out sheet.
    if (branchSheet) {
        ModalBottomSheet(
            onDismissRequest = { branchSheet = false; presenter.onSheetDismissed() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            BranchSheetContent(
                fromTitle = branchFromTitle,
                suggestions = branchSuggestions,
                generating = branchGenerating,
                onGenerate = { presenter.onGenerateBranch(it) },
            )
        }
    }

    ChatOverlay(
        request = chat,
        onDismiss = {
            chat = null
            presenter.onChatClosed()
        },
    )
}

/**
 * The journey itself: one board, exactly as wide and tall as the roadmap needs, which the user
 * pinches to zoom and drags to move around. Pure rendering of [state].
 *
 * The board is a single transformed layer rather than a scrolling list, so zoom applies to the
 * connectors, pills and nodes together and nothing has to be re-laid-out mid-gesture.
 *
 * The camera is a plain `screen = offset + scale × board` mapping: the board is pinned at the
 * viewport's top-left and scaled about that same corner, so [BoardCamera.offset] is literally where
 * the board's origin lands on screen. Every earlier attempt at framing the root failed because it
 * leaned on something implicit instead — Box alignment centring an oversized child, a pivot in the
 * middle of the layer — and one wrong assumption in that chain put the root off screen with nothing
 * in the arithmetic to show it. With the corner as the origin there is nothing left to assume:
 * [openingCamera] solves the offset directly from the root's own position.
 *
 * Until the board is touched it frames itself, and that frame is derived rather than stored, so no
 * measure pass can leave a stale one behind. The first gesture takes ownership, after which growing
 * a branch, opening a lesson or any other state change never moves the camera, and it may travel in
 * every direction until only [KeepOnScreen] of the board is left in view.
 */
@Composable
private fun JourneyBoard(
    state: PathUiState,
    fillIntoId: String?,
    enterId: String?,
    enterNonce: Int,
    onNodeTapped: (String) -> Unit,
) {
    val regionRows = remember(state.regions) { state.regions.map { it.row }.toSet() }
    val boardWidth = PathLayout.boardWidth(state.columnSpan)
    val boardHeight = PathLayout.boardHeight(state.rowCount, regionRows)

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val density = LocalDensity.current
        val keepOnScreen = with(density) { KeepOnScreen.toPx() }

        // Live geometry. Read through `rememberUpdatedState` so the gesture handler below can be
        // started once and never restarted: re-keying `pointerInput` every time the board grew was
        // tearing the detector down mid-drag, which is what made panning feel like it seized up.
        val viewport by rememberUpdatedState(
            Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()),
        )
        val content by rememberUpdatedState(
            with(density) { Size(boardWidth.toPx(), boardHeight.toPx()) },
        )

        /**
         * The frame the board opens on: aimed at the ROOT — the top of the tree. Aiming at the
         * "YOU'RE HERE" node instead meant a large roadmap with progress opened deep in the tree
         * with its root scrolled clean off the top, so the roadmap read as starting nowhere. The
         * root must be on screen every time the board appears, whether the roadmap was just
         * generated or reopened years in; everything else is one drag away.
         *
         * RE-DERIVED every composition rather than remembered. That matters: `BoxWithConstraints`
         * composes during measure, so a first pass whose height isn't final yet computes a bad frame
         * — and freezing that pass's answer in a `remember` froze the mistake. Deriving it means a
         * bad measure cannot outlive itself.
         */
        val focus = state.nodes.minByOrNull { it.row }
        val opening = rememberUpdatedState(
            openingCamera(
                viewport = viewport,
                content = content,
                focusCentre = with(density) {
                    Offset(
                        PathLayout.centerX(focus?.column ?: 0f).toPx(),
                        PathLayout.centerY(focus?.row ?: 0, regionRows).toPx(),
                    )
                },
                marginPx = with(density) { BoardMargin.toPx() },
                // A share of the visible board rather than a fixed distance, so the focused node
                // lands well down the screen on any device instead of tucked against the header.
                focusInsetPx = (viewport.height * FocusViewportFraction)
                    .coerceAtLeast(with(density) { FocusMinInset.toPx() }),
                minZoom = MinFitZoom,
            ),
        )

        // The moment the user touches the board the camera becomes theirs, and from then on nothing
        // — a grown branch, an opened lesson, a re-measure — is allowed to move it.
        val moved = remember { mutableStateOf<BoardCamera?>(null) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val camera = moved.value ?: opening.value
                        val next = (camera.scale * zoom).coerceIn(MinZoom, MaxZoom)
                        // Keep whatever is under the fingers under the fingers. With the corner as
                        // the origin, holding the centroid still is just this — no pivot to subtract.
                        val factor = next / camera.scale
                        moved.value = BoardCamera(
                            scale = next,
                            offset = clampOffset(
                                candidate = centroid * (1f - factor) + camera.offset * factor + pan,
                                scale = next,
                                viewport = viewport,
                                content = content,
                                keepOnScreenPx = keepOnScreen,
                            ),
                        )
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    // Measured UNBOUNDED and pinned to the viewport's top-left corner. Without
                    // this, a board bigger than the viewport violates its incoming constraints,
                    // and Compose responds by silently CENTRING the oversized child — an implicit
                    // translation of (viewport - board)/2 the camera arithmetic knew nothing
                    // about, growing with the board, which is exactly what shoved the root of
                    // every large roadmap off the top of the screen.
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    // REQUIRED, not `width`/`height`: a board wider than the screen must keep its
                    // real width. `width` is clamped by the parent's constraints, which squashed a
                    // wide roadmap to viewport width and put its centre in the wrong place.
                    .requiredWidth(boardWidth)
                    .requiredHeight(boardHeight)
                    // Both cameras are read HERE, inside the layer block, so panning and zooming
                    // cost a redraw and never a recomposition of the board.
                    .graphicsLayer {
                        val camera = moved.value ?: opening.value
                        scaleX = camera.scale
                        scaleY = camera.scale
                        translationX = camera.offset.x
                        translationY = camera.offset.y
                        // Top-LEFT corner, so the offset above is exactly where the board's origin
                        // lands on screen and the framing maths has no pivot term to get wrong.
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
            ) {
                // 1) Connectors behind everything.
                TreeCanvas(
                    nodes = state.nodes,
                    regionRows = regionRows,
                    fillIntoId = fillIntoId,
                    modifier = Modifier.fillMaxSize(),
                )

                // 2) Region pills, centered in the RegionGap band above their first row.
                state.regions.forEach { region ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .offset(y = PathLayout.centerY(region.row, regionRows) - REGION_PILL_GAP),
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
                        enterKey = if (node.id == enterId) enterNonce else 0,
                        mode = state.boardMode,
                        modifier = Modifier.offset(
                            x = PathLayout.centerX(node.column) - PathLayout.circleCenterXInSlot,
                            y = PathLayout.centerY(node.row, regionRows) - PathLayout.circleCenterYInSlot,
                        ),
                    )
                }
            }
        }
    }
}

/** How far the board may be pinched by hand. Out far enough for an overview of a wide tree. */
private const val MinZoom = 0.35f
private const val MaxZoom = 2.5f

/**
 * The furthest the board will zoom out ON ITS OWN to fit the screen.
 *
 * A roadmap several branches wide is far wider than a phone, and fitting all of it would shrink the
 * 13sp node titles to nothing — which reads as the titles having vanished rather than as a board
 * that needs panning. So a wide board opens readable and overhanging, and the user pinches out to
 * [MinZoom] for the overview if they want it.
 */
private const val MinFitZoom = 0.7f

/** Breathing room left either side of the widest row when the board is first fitted to the screen. */
private val BoardMargin = 24.dp

/**
 * How far down the visible board the root sits when the board frames itself.
 *
 * Roughly a quarter of the way, which puts it clear of the HUD above the board and leaves room for
 * its "YOU'RE HERE" tag on a fresh roadmap, while still showing what comes next below it.
 */
private const val FocusViewportFraction = 0.26f

/** Floor for the above, for a board area too short for a quarter of it to be much. */
private val FocusMinInset = 108.dp

/**
 * How much of the board must stay in view, and therefore how far it can be dragged.
 *
 * The only thing panning is stopped from doing is losing the board altogether. Anything tighter
 * either pins a small board in place or leaves one direction dead, both of which read as the drag
 * being broken rather than as a limit.
 */
private val KeepOnScreen = 120.dp

/**
 * Pill-top distance above its row's node center: past the circle band (42) and tag zone (26)
 * into the RegionGap, leaving ~4dp above the "YOU'RE HERE" tag.
 */
private val REGION_PILL_GAP = 100.dp

// ── Preview ────────────────────────────────────────────────────────────────────────────────
// A hand-built [PathUiState] so the static preview shows the real journey (the MVP path loads
// its data in a DisposableEffect, which non-interactive previews don't run).
@Preview(showBackground = true, heightDp = 900)
@Composable
private fun PathScreenPreview() {
    fun n(id: String, title: String, est: Int, st: PathNodeState, column: Float, row: Int, parents: List<String>) =
        PathNodeUi(id, title, est, st, column, row, parents)

    // Three strands off the root, laid out the way `layoutBoard` would: leaves take consecutive
    // columns and each parent sits at the midpoint of its own children.
    val sample = PathUiState(
        title = "Machine Learning",
        nodes = listOf(
            // States as the presenter would derive them for this shape: a lesson is open once its
            // parents are done, so the two strands the root leads to are available and everything
            // behind an unfinished lesson is locked.
            n("a", "What is ML?", 5, PathNodeState.DONE, 1f, 0, emptyList()),
            n("b", "Supervised", 12, PathNodeState.DONE, 0f, 1, listOf("a")),
            n("e", "Regression", 10, PathNodeState.CURRENT, 0f, 2, listOf("b")),
            n("h", "Overfitting", 9, PathNodeState.LOCKED, 0f, 3, listOf("e")),
            n("c", "Unsupervised", 12, PathNodeState.OPEN, 1f, 1, listOf("a")),
            n("f", "Clustering", 10, PathNodeState.LOCKED, 1f, 2, listOf("c")),
            n("d", "Model Evaluation", 9, PathNodeState.OPEN, 2f, 1, listOf("a")),
            n("g", "Cross-validation", 11, PathNodeState.LOCKED, 2f, 2, listOf("d")),
            n("i", "Neural Networks", 15, PathNodeState.LOCKED, 2f, 3, listOf("g")),
        ),
        regions = listOf(
            RegionUi("FOUNDATIONS", 0),
            RegionUi("CORE ML · PICK A TRACK", 1),
        ),
        masteredCount = 2,
        totalLessons = 9,
        streak = 6,
        level = 1,
        xpInLevel = 80,
        xpPerLevel = 200,
        xpFraction = 0.4f,
        rowCount = 4,
        columnSpan = 2f,
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
                    enterId = null,
                    enterNonce = 0,
                    onNodeTapped = {},
                )
            }
        }
    }
}

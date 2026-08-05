package com.example.grasp.ui.feature.library

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.GraspApp
import com.example.grasp.core.util.NetworkMonitor
import com.example.grasp.data.model.DownloadState
import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.Mode
import com.example.grasp.data.model.SavedItem
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.ui.components.GameButton
import com.example.grasp.ui.components.GameCard
import com.example.grasp.ui.components.GameChip
import com.example.grasp.ui.components.GameEmptyState
import com.example.grasp.ui.components.GameIconTile
import com.example.grasp.ui.components.GameLoadingState
import com.example.grasp.ui.components.GameProgressBar
import com.example.grasp.ui.components.GameScreenHeader
import com.example.grasp.ui.components.GameTag
import com.example.grasp.ui.components.GraspBottomBar
import com.example.grasp.ui.components.MiniPathArt
import com.example.grasp.ui.components.ModeGlyph
import com.example.grasp.ui.components.PathToast
import com.example.grasp.ui.components.accent
import com.example.grasp.ui.components.tint
import com.example.grasp.ui.navigation.TopLevelDestination
import com.example.grasp.ui.theme.GameDanger
import com.example.grasp.ui.theme.GameDangerTint
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathCard
import com.example.grasp.ui.theme.PathConnector
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathNodeDone
import com.example.grasp.ui.theme.PathScreenBg
import kotlin.math.roundToInt

/**
 * Library screen (View) — resume or remove saved paths & guides.
 */
@Composable
fun LibraryScreen(
    onSelectTab: (TopLevelDestination) -> Unit,
    onOpenLearner: (String) -> Unit,
    onOpenTinker: (String) -> Unit,
    onOpenOfflineContent: () -> Unit,
    presenterFactory: () -> LibraryContract.Presenter = { LibraryPresenter() },
) {
    var savedItems by remember { mutableStateOf<List<SavedItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf<Mode?>(null) }
    var pendingDelete by remember { mutableStateOf<SavedItem?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var isOffline by remember { mutableStateOf(false) }

    val presenter = remember { presenterFactory() }
    
    val view = remember(onOpenLearner, onOpenTinker, onOpenOfflineContent) {
        object : LibraryContract.View {
            override fun showLoading(loading: Boolean) { isLoading = loading }
            override fun showSaved(items: List<SavedItem>) { savedItems = items }
            override fun openLearner(id: String) = onOpenLearner(id)
            override fun openTinker(id: String) = onOpenTinker(id)
            override fun showToast(message: String) { toastMessage = message }
            override fun showOffline(offline: Boolean) { isOffline = offline }
        }
    }
    DisposableEffect(presenter, view) {
        presenter.attach(view)
        onDispose { presenter.detach() }
    }

    val visibleItems = savedItems.filter { filter == null || it.mode == filter }
    val finishedCount = savedItems.count { it.progress >= 1f }

    Scaffold(
        containerColor = PathScreenBg,
        bottomBar = { GraspBottomBar(selected = TopLevelDestination.LIBRARY, onSelect = onSelectTab) },
    ) { padding ->
        if (isOffline && savedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                GameEmptyState(
                    title = "You're offline",
                    message = "Check your connection to see your full library, or use your downloaded content.",
                    art = {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(100.dp),
                            tint = PathMuted
                        )
                    },
                    action = {
                        GameButton(
                            label = "View offline content",
                            onClick = onOpenOfflineContent,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
            }
        } else if (isLoading && savedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                GameLoadingState(
                    title = "Fetching your library",
                    message = "Getting your roadmaps and guides from the cloud…",
                )
            }
        } else if (savedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                GameEmptyState(
                    title = "Your library is empty",
                    message = "Start a topic or a task and it lands here — with your progress saved.",
                    art = {
                        MiniPathArt(
                            doneColor = PathNodeDone,
                            activeColor = PathNodeCurrent,
                            trackColor = PathConnector,
                            modifier = Modifier.size(120.dp),
                        )
                    },
                    action = {
                        GameButton(
                            label = "Find a topic",
                            onClick = { onSelectTab(TopLevelDestination.HOME) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    GameScreenHeader(
                        title = "Your library",
                        subtitle = buildString {
                            append("${savedItems.size} saved")
                            if (finishedCount > 0) append(" · $finishedCount finished")
                        },
                    )
                }

                item {
                    Row(
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GameChip(
                            label = "All",
                            selected = filter == null,
                            onClick = { filter = null },
                        )
                        Mode.entries.forEach { mode ->
                            GameChip(
                                label = mode.label,
                                selected = filter == mode,
                                onClick = { filter = mode },
                                accent = mode.accent,
                            )
                        }
                    }
                }

                if (visibleItems.isEmpty()) {
                    item {
                        Text(
                            text = "Nothing in ${filter?.label} mode yet.",
                            fontFamily = NunitoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = PathMuted,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else {
                    items(visibleItems, key = { "${it.mode}-${it.id}" }) { item ->
                        SavedItemCard(
                            item = item,
                            onClick = { presenter.onItemClicked(item) },
                            onDelete = { pendingDelete = item },
                            onDownload = { presenter.onDownloadClicked(item) },
                            onCancelDownload = { presenter.onCancelDownloadClicked(item) },
                            onRemoveDownload = { presenter.onRemoveDownloadClicked(item) }
                        )
                    }
                }
            }
        }
        
        PathToast(message = toastMessage, onFinished = { toastMessage = null })
    }

    // Removing a path deletes its progress too, so it always asks first.
    pendingDelete?.let { item ->
        ConfirmRemoveDialog(
            item = item,
            onConfirm = {
                presenter.onDeleteClicked(item)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** One saved path/guide: mode glyph, title, state tag, and its progress bar. */
@Composable
private fun SavedItemCard(
    item: SavedItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRemoveDownload: () -> Unit
) {
    val percent = (item.progress * 100).roundToInt()
    val finished = item.progress >= 1f
    val barColor = if (finished) PathNodeDone else item.mode.accent

    GameCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameIconTile(tint = item.mode.tint) {
                    ModeGlyph(mode = item.mode, tint = item.mode.accent, size = 24.dp)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                ) {
                    Text(
                        text = item.title,
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        color = PathInk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        GameTag(
                            text = item.mode.label,
                            accent = item.mode.accent,
                            tint = item.mode.tint,
                        )
                        StateTag(progress = item.progress)
                    }
                }
                
                // Multi-state Download Controls
                DownloadActionIcon(
                    state = item.downloadState,
                    isStarter = item.isStarter,
                    onDownload = onDownload,
                    onCancel = onCancelDownload,
                    onRemove = onRemoveDownload
                )
                
                Spacer(Modifier.width(4.dp))

                // Ghost icon button: quiet by default, since it is the destructive action.
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onDelete)
                        .padding(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove ${item.title}",
                        tint = PathFaint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            GameProgressBar(fraction = item.progress, accent = barColor)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.completionLabel,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = PathMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$percent%",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = barColor,
                )
            }
        }
    }
}

/**
 * The per-item download control.
 *
 * [isStarter] items get the finished tick and nothing else. Their lessons ship inside the APK, so
 * there is no download to start and — more to the point — no download to remove: Firestore cannot
 * evict single documents from its cache, and the app binary is not going anywhere either, so a
 * "Remove" here would free nothing and change nothing while looking like it had done both.
 */
@Composable
private fun DownloadActionIcon(
    state: DownloadState,
    isStarter: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit
) {
    if (isStarter) {
        Box(modifier = Modifier.padding(8.dp)) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Included with the app — available offline",
                tint = PathNodeDone,
                modifier = Modifier.size(22.dp),
            )
        }
        return
    }

    when (state) {
        DownloadState.NONE -> {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDownload)
                    .padding(8.dp),
            ) {
                Icon(
                    Icons.Filled.ArrowDownward,
                    contentDescription = "Download",
                    tint = PathMuted,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        DownloadState.PENDING, DownloadState.DOWNLOADING -> {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onCancel)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = PathNodeCurrent
                )
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = PathNodeCurrent,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        DownloadState.AVAILABLE -> {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onRemove)
                    .padding(8.dp),
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Remove download",
                    tint = PathNodeDone,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        DownloadState.FAILED -> {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDownload)
                    .padding(8.dp),
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Retry download",
                    tint = GameDanger,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** Green when finished, indigo while in flight, grey before the first lesson. */
@Composable
private fun StateTag(progress: Float) = when {
    progress >= 1f -> GameTag("MASTERED", PathNodeDone, com.example.grasp.ui.theme.PathNodeDoneTint)
    progress > 0f -> GameTag("IN PROGRESS", PathNodeCurrent, com.example.grasp.ui.theme.PathNodeCurrentTint)
    else -> GameTag("NOT STARTED", PathMuted, com.example.grasp.ui.theme.GameTintNeutral)
}

/**
 * "3 of 8 complete" / "2 of 6 done" — both concrete types expose it, but [SavedItem] itself does
 * not, so it is read through a `when`. Named differently from the members it reads to avoid the
 * shadowing trap that would make this recurse.
 */
private val SavedItem.completionLabel: String
    get() = when (this) {
        is LearningPath -> progressLabel
        is TinkerGuide -> progressLabel
    }

@Composable
private fun ConfirmRemoveDialog(
    item: SavedItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PathCard,
        shape = RoundedCornerShape(24.dp),
        icon = {
            GameIconTile(tint = GameDangerTint, size = 46.dp) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = GameDanger,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        title = {
            Text(
                text = "Remove “${item.title}”?",
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = PathInk,
            )
        },
        text = {
            Text(
                text = "Your progress on this path will be deleted too. This can't be undone.",
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = PathMuted,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Remove",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = GameDanger,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Keep",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = PathMuted,
                )
            }
        },
    )
}

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import com.example.grasp.ui.components.accent
import com.example.grasp.ui.components.tint
import com.example.grasp.ui.navigation.TopLevelDestination
import com.example.grasp.ui.theme.GameDanger
import com.example.grasp.ui.theme.GameDangerTint
import com.example.grasp.ui.theme.GameTintNeutral
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathCard
import com.example.grasp.ui.theme.PathConnector
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathNodeCurrentTint
import com.example.grasp.ui.theme.PathNodeDone
import com.example.grasp.ui.theme.PathNodeDoneTint
import com.example.grasp.ui.theme.PathScreenBg
import kotlin.math.roundToInt

/**
 * Library screen (View) — resume or remove saved paths & guides. Top-level destination,
 * so it hosts the bottom nav bar. MVP wiring matches
 * [com.example.grasp.ui.feature.auth.LoginScreen].
 *
 * The list is the user's trophy shelf, so every card leads with progress: a chunky bar, a
 * "3 of 8 complete" read-out and a state tag that turns green the moment a path is finished.
 * Removal is destructive and permanent, so it goes through a confirmation dialog rather than
 * firing straight off a small icon.
 */
@Composable
fun LibraryScreen(
    onSelectTab: (TopLevelDestination) -> Unit,
    onOpenLearner: (String) -> Unit,
    onOpenTinker: (String) -> Unit,
    presenterFactory: () -> LibraryContract.Presenter = { LibraryPresenter() },
) {
    var savedItems by remember { mutableStateOf<List<SavedItem>>(emptyList()) }
    // Starts true: this screen is rebuilt from scratch every time the tab is selected, and the
    // library it is about to show lives in the cloud. Assuming "empty" until told otherwise is
    // what made switching to this tab flash "Your library is empty" at everyone.
    var isLoading by remember { mutableStateOf(true) }
    // null = "All"; otherwise only that mode's items are listed.
    var filter by remember { mutableStateOf<Mode?>(null) }
    var pendingDelete by remember { mutableStateOf<SavedItem?>(null) }

    val presenter = remember { presenterFactory() }
    val view = remember(onOpenLearner, onOpenTinker) {
        object : LibraryContract.View {
            override fun showLoading(loading: Boolean) { isLoading = loading }
            override fun showSaved(items: List<SavedItem>) { savedItems = items }
            override fun openLearner(id: String) = onOpenLearner(id)
            override fun openTinker(id: String) = onOpenTinker(id)
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
        if (isLoading && savedItems.isEmpty()) {
            // Only when there is nothing on screen yet. A REFRESH (after a delete) keeps the list
            // visible and updates it in place — replacing a library the user is looking at with a
            // spinner would be a worse flicker than the one this whole state exists to remove.
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
                        )
                    }
                }
            }
        }
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
private fun SavedItemCard(item: SavedItem, onClick: () -> Unit, onDelete: () -> Unit) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameTag(
                            text = item.mode.label,
                            accent = item.mode.accent,
                            tint = item.mode.tint,
                        )
                        StateTag(progress = item.progress)
                    }
                }
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

/** Green when finished, indigo while in flight, grey before the first lesson. */
@Composable
private fun StateTag(progress: Float) = when {
    progress >= 1f -> GameTag("MASTERED", PathNodeDone, PathNodeDoneTint)
    progress > 0f -> GameTag("IN PROGRESS", PathNodeCurrent, PathNodeCurrentTint)
    else -> GameTag("NOT STARTED", PathMuted, GameTintNeutral)
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

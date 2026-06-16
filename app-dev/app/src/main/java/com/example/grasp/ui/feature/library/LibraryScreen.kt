@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.grasp.ui.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.grasp.data.model.Mode
import com.example.grasp.data.model.SavedItem
import com.example.grasp.ui.components.GraspBottomBar
import com.example.grasp.ui.components.LabeledLinearProgress
import com.example.grasp.ui.components.StatusPill
import com.example.grasp.ui.navigation.TopLevelDestination
import com.example.grasp.ui.theme.NodeActive
import com.example.grasp.ui.theme.StreakAccent
import kotlin.math.roundToInt

/**
 * Library screen (View) — resume or remove saved paths & guides. Top-level destination,
 * so it hosts the bottom nav bar. MVP wiring matches
 * [com.example.grasp.ui.feature.auth.LoginScreen].
 */
@Composable
fun LibraryScreen(
    onSelectTab: (TopLevelDestination) -> Unit,
    onOpenLearner: (String) -> Unit,
    onOpenTinker: (String) -> Unit,
    presenterFactory: () -> LibraryContract.Presenter = { LibraryPresenter() },
) {
    var savedItems by remember { mutableStateOf<List<SavedItem>>(emptyList()) }

    val presenter = remember { presenterFactory() }
    val view = remember(onOpenLearner, onOpenTinker) {
        object : LibraryContract.View {
            override fun showSaved(items: List<SavedItem>) { savedItems = items }
            override fun openLearner(id: String) = onOpenLearner(id)
            override fun openTinker(id: String) = onOpenTinker(id)
        }
    }
    DisposableEffect(presenter, view) {
        presenter.attach(view)
        onDispose { presenter.detach() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Library") }) },
        bottomBar = { GraspBottomBar(selected = TopLevelDestination.LIBRARY, onSelect = onSelectTab) },
    ) { padding ->
        if (savedItems.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nothing saved yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Topics and guides you start will show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(savedItems, key = { "${it.mode}-${it.id}" }) { item ->
                    SavedItemCard(
                        item = item,
                        onClick = { presenter.onItemClicked(item) },
                        onDelete = { presenter.onDeleteClicked(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedItemCard(item: SavedItem, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        item.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    text = item.mode.label,
                    accent = if (item.mode == Mode.LEARNER) NodeActive else StreakAccent,
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove")
                }
            }
            LabeledLinearProgress(
                progress = item.progress,
                label = "${(item.progress * 100).roundToInt()}% complete",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

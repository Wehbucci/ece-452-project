@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.grasp.ui.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.grasp.data.model.Mode
import com.example.grasp.data.model.TopicSuggestion
import com.example.grasp.ui.components.GraspBottomBar
import com.example.grasp.ui.components.SectionHeader
import com.example.grasp.ui.components.StatusPill
import com.example.grasp.ui.navigation.TopLevelDestination
import com.example.grasp.ui.theme.GraspTheme
import com.example.grasp.ui.theme.NodeActive
import com.example.grasp.ui.theme.StreakAccent

/**
 * Home / topic-entry screen (View). Top-level destination, so it hosts the bottom nav bar.
 *
 * @param onSelectTab switch bottom-nav tabs (handled by the nav layer)
 * @param onOpenLearner open a Learner roadmap by id
 * @param onOpenTinker open a Tinkerer guide by id
 */
@Composable
fun HomeScreen(
    onSelectTab: (TopLevelDestination) -> Unit,
    onOpenLearner: (String) -> Unit,
    onOpenTinker: (String) -> Unit,
    presenterFactory: () -> HomeContract.Presenter = { HomePresenter() },
) {
    var popularTopics by remember { mutableStateOf<List<TopicSuggestion>>(emptyList()) }
    var mode by remember { mutableStateOf(Mode.LEARNER) }
    var query by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val presenter = remember { presenterFactory() }
    val view = remember(onOpenLearner, onOpenTinker) {
        object : HomeContract.View {
            override fun showPopularTopics(topics: List<TopicSuggestion>) { popularTopics = topics }
            override fun showGenerating() { isGenerating = true }
            override fun showGenerateError(message: String) { isGenerating = false; errorMessage = message }
            override fun openLearner(pathId: String) { isGenerating = false; onOpenLearner(pathId) }
            override fun openTinker(pathId: String) { isGenerating = false; onOpenTinker(pathId) }
        }
    }
    DisposableEffect(presenter, view) {
        presenter.attach(view)
        onDispose { presenter.detach() }
    }

    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        errorMessage = null
    }

    Scaffold(
        bottomBar = {
            GraspBottomBar(selected = TopLevelDestination.HOME, onSelect = onSelectTab)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Hi there 👋", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = if (mode == Mode.LEARNER) "What do you want to learn?"
                    else "What do you want to make?",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            // Mode picker — Learn vs Tinker (overview.md §3).
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { if (!isGenerating) mode = m },
                            label = { Text(m.label) },
                        )
                    }
                }
                Text(
                    text = mode.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Free-text topic/task entry.
            item {
                val submit = {
                    if (!isGenerating) {
                        presenter.onSubmitTopic(query, mode)
                        query = ""
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { if (!isGenerating) query = it },
                    label = { Text("Type a topic or task") },
                    singleLine = true,
                    enabled = !isGenerating,
                    trailingIcon = {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(onClick = submit) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Generate")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isGenerating) {
                    Text(
                        text = "Building your ${if (mode == Mode.LEARNER) "roadmap" else "guide"}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            item { SectionHeader("Popular topics") }

            items(popularTopics, key = { it.id }) { topic ->
                TopicCard(topic = topic, onClick = { presenter.onPopularTopicClicked(topic) })
            }
        }
    }
}

/** A tappable popular-topic card showing its subtopic count and which mode it starts. */
@Composable
private fun TopicCard(topic: TopicSuggestion, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(topic.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${topic.subtopicCount} subtopics",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(
                text = topic.mode.label,
                accent = if (topic.mode == Mode.LEARNER) NodeActive else StreakAccent,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    GraspTheme {
        HomeScreen(onSelectTab = {}, onOpenLearner = {}, onOpenTinker = {})
    }
}

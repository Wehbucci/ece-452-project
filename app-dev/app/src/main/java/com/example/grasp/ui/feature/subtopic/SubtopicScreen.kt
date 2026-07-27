@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.grasp.ui.feature.subtopic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.example.grasp.data.model.LessonBlock
import com.example.grasp.data.model.ResourceLink
import com.example.grasp.data.model.Subtopic
import com.example.grasp.ui.components.LessonCode
import com.example.grasp.ui.components.LessonDiagram
import com.example.grasp.ui.components.LessonImage
import com.example.grasp.ui.components.SectionHeader
import com.example.grasp.ui.components.StatusPill
import com.example.grasp.ui.theme.NodeActive
import com.example.grasp.ui.theme.NodeCompleted

/**
 * Subtopic detail screen (View). Renders the resolved node content and the "Ask AI" entry.
 *
 * @param pathId / nodeId navigation arguments identifying which content to load
 * @param onBack pop the back stack
 * @param onOpenChat open the AI chat with a context string
 */
@Composable
fun SubtopicScreen(
    pathId: String,
    nodeId: String,
    onBack: () -> Unit,
    onOpenChat: (context: String, pathId: String, nodeId: String, blockIndex: Int) -> Unit,
    presenterFactory: (String, String) -> SubtopicContract.Presenter = { p, n -> SubtopicPresenter(p, n) },
) {
    var content by remember { mutableStateOf<Subtopic?>(null) }
    var isComplete by remember { mutableStateOf(false) }
    var notFound by remember { mutableStateOf(false) }
    var fabHasHistory by remember { mutableStateOf(false) }
    var blocksWithHistory by remember { mutableStateOf(emptySet<Int>()) }

    val uriHandler = LocalUriHandler.current
    val presenter = remember(pathId, nodeId) { presenterFactory(pathId, nodeId) }
    val view = remember(onOpenChat) {
        object : SubtopicContract.View {
            override fun showSubtopic(subtopic: Subtopic) { content = subtopic; notFound = false }
            override fun showNotFound() { notFound = true }
            override fun showCompleted(completed: Boolean) { isComplete = completed }
            override fun openChat(context: String, pathId: String, nodeId: String, blockIndex: Int) =
                onOpenChat(context, pathId, nodeId, blockIndex)
            override fun showChatIndicators(hasFabHistory: Boolean, blockHistoryIndices: Set<Int>) {
                fabHasHistory = hasFabHistory
                blocksWithHistory = blockHistoryIndices
            }
            override fun openResource(url: String) = uriHandler.openUri(url)
        }
    }
    DisposableEffect(presenter, view) {
        presenter.attach(view)
        onDispose { presenter.detach() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(content?.title ?: "Subtopic") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // let users manually edit generated content (placeholder for now).
                    IconButton(onClick = { /* TODO(edit): open inline content editor */ }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit content")
                    }
                },
            )
        },
        floatingActionButton = {
            if (content != null) {
                ExtendedFloatingActionButton(
                    text = { Text(if (fabHasHistory) "Continue chat" else "Ask AI") },
                    icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                    onClick = presenter::onAskAi,
                )
            }
        },
    ) { padding ->
        val current = content
        when {
            notFound -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("We couldn't load this content.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            current == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(current.sectionLabel, NodeActive)
                    StatusPill("${current.estMinutes} min", MaterialTheme.colorScheme.onSurfaceVariant)
                }

                SectionHeader("Summary")
                Text(current.summary, style = MaterialTheme.typography.bodyLarge)

                SectionHeader("Why it matters")
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Text(
                        current.whyItMatters,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(14.dp),
                    )
                }

                SectionHeader("Learn")
                // Headings structure the lesson; each paragraph is tappable to ask the AI about it.
                current.body.forEachIndexed { index, block ->
                    when (block) {
                        is LessonBlock.Heading -> Text(
                            text = block.text,
                            style = if (block.level <= 1) MaterialTheme.typography.titleMedium
                            else MaterialTheme.typography.titleSmall,
                            color = if (block.level <= 1) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp),
                        )

                        is LessonBlock.Paragraph -> ContentBlock(
                            text = block.text,
                            hasHistory = index in blocksWithHistory,
                            onClick = { presenter.onBlockClicked(block.text, index) },
                        )

                        is LessonBlock.Code -> LessonCode(block)

                        is LessonBlock.Diagram -> LessonDiagram(block)

                        is LessonBlock.Image -> LessonImage(
                            image = block,
                            onOpenSource = { url -> uriHandler.openUri(url) },
                        )
                    }
                }

                SectionHeader("Dive deeper")
                current.resources.forEach { link ->
                    ResourceRow(link = link, onClick = { presenter.onResourceClicked(link) })
                }

                Spacer(Modifier.height(8.dp))

                // Mark complete. Filled when not done, outlined+green once done.
                if (isComplete) {
                    OutlinedButton(
                        onClick = presenter::onToggleComplete,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Completed ✓", color = NodeCompleted) }
                } else {
                    Button(
                        onClick = presenter::onToggleComplete,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Mark as complete") }
                }

                Spacer(Modifier.height(72.dp)) // clear the FAB
            }
        }
    }
}

@Composable
private fun ContentBlock(text: String, hasHistory: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (hasHistory) "Continue chat about this ›" else "Tap to ask AI about this",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun ResourceRow(link: ResourceLink, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(link.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusPill(link.kind.name.lowercase().replaceFirstChar { it.uppercase() }, MaterialTheme.colorScheme.primary)
        }
    }
}

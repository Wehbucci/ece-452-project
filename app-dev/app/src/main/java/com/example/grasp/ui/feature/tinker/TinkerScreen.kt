@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.grasp.ui.feature.tinker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.model.TinkerStep
import com.example.grasp.ui.components.LabeledLinearProgress

/**
 * Tinkerer guide screen (View) — a checklist with a linear progress bar.
 *
 * @param guideId navigation argument
 * @param onBack pop the back stack
 * @param onOpenChat open the AI chat with a context string
 */
@Composable
fun TinkerScreen(
    guideId: String,
    onBack: () -> Unit,
    onOpenChat: (context: String) -> Unit,
    presenterFactory: (String) -> TinkerContract.Presenter = { TinkerPresenter(it) },
) {
    var currentGuide by remember { mutableStateOf<TinkerGuide?>(null) }
    var notFound by remember { mutableStateOf(false) }

    val presenter = remember(guideId) { presenterFactory(guideId) }
    val view = remember(onOpenChat) {
        object : TinkerContract.View {
            override fun showGuide(guide: TinkerGuide) { currentGuide = guide; notFound = false }
            override fun showNotFound() { notFound = true }
            override fun openChat(context: String) = onOpenChat(context)
        }
    }
    DisposableEffect(presenter, view) {
        presenter.attach(view)
        onDispose { presenter.detach() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentGuide?.title ?: "Guide") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (currentGuide != null) {
                ExtendedFloatingActionButton(
                    text = { Text("Ask AI") },
                    icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                    onClick = presenter::onAskAi,
                )
            }
        },
    ) { padding ->
        val current = currentGuide
        when {
            notFound -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("We couldn't load this guide.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            current == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                LabeledLinearProgress(
                    progress = current.progress,
                    label = current.progressLabel,
                    modifier = Modifier.padding(20.dp),
                )
                HorizontalDivider()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(current.steps, key = { it.id }) { step ->
                        StepRow(step = step, onToggle = { presenter.onToggleStep(step) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: TinkerStep, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = step.done, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${step.order}. ${step.instruction}",
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (step.done) TextDecoration.LineThrough else TextDecoration.None,
                color = if (step.done) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            if (step.detail.isNotBlank()) {
                Text(
                    step.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (step.estMinutes > 0) {
                Text(
                    "~${step.estMinutes} min",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

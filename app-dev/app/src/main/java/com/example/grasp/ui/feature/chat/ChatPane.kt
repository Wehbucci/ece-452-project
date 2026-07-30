package com.example.grasp.ui.feature.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.example.grasp.data.model.ChatMessage
import com.example.grasp.ui.components.MarkdownText

/**
 * The conversation itself: the message list and the composer, and nothing that knows where it is
 * being shown.
 *
 * Split out from the chrome so the tutor can be hosted as a panel over a lesson without the
 * message rendering being duplicated per host. It holds no presenter and no state of its own —
 * whoever shows it owns both.
 */
@Composable
internal fun ChatPane(
    history: List<ChatMessage>,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Stay pinned to the bottom as new messages arrive and as streaming text grows.
    LaunchedEffect(history.size, history.lastOrNull()?.text) {
        if (history.isNotEmpty()) listState.animateScrollToItem(history.lastIndex)
    }

    Column(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(history, key = { it.id }) { message -> MessageBubble(message) }
        }
        ChatInputBar(
            value = input,
            onValueChange = onInputChange,
            onSend = onSend,
            onAttach = onAttach,
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.author == ChatMessage.Author.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.imageUri != null) {
                    Box(
                        modifier = Modifier
                            .size(width = 180.dp, height = 120.dp)
                            .padding(bottom = if (message.text.isNotBlank()) 8.dp else 0.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Box(contentAlignment = Alignment.Center) { Text("📷 Photo") }
                        }
                    }
                }
                when {
                    message.pending && message.text.isEmpty() -> TypingIndicator()
                    message.text.isNotBlank() -> MarkdownText(
                        message.text,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 400, delayMillis = i * 133, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Text("●", modifier = Modifier.alpha(alpha), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onAttach) {
                Icon(Icons.Filled.Add, contentDescription = "Attach photo")
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Ask about your material…") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            FilledIconButton(onClick = onSend, enabled = value.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

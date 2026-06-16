@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.grasp.ui.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.grasp.data.model.ChatMessage

/**
 * Multi-modal AI chat screen (View). MVP wiring matches
 * [com.example.grasp.ui.feature.auth.LoginScreen].
 *
 * @param chatContext what we're chatting about (shown in the header, sent to the AI later)
 * @param onBack pop the back stack
 */
@Composable
fun ChatScreen(
    chatContext: String,
    onBack: () -> Unit,
    presenterFactory: (String) -> ChatContract.Presenter = { ChatPresenter(it) },
) {
    var history by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }

    val presenter = remember(chatContext) { presenterFactory(chatContext) }
    val view = remember {
        object : ChatContract.View {
            override fun showMessages(messages: List<ChatMessage>) { history = messages }
        }
    }
    DisposableEffect(presenter, view) {
        presenter.attach(view)
        onDispose { presenter.detach() }
    }

    val listState = rememberLazyListState()
    // Keep the newest message in view as the conversation grows.
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) listState.animateScrollToItem(history.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ask AI", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "About: $chatContext",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            ChatInputBar(
                value = input,
                onValueChange = { input = it },
                onSend = {
                    presenter.onSend(input)
                    input = ""
                },
                onAttach = presenter::onAttachImage,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(history, key = { it.id }) { message -> MessageBubble(message) }
        }
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
                    // Placeholder for an attached photo (no real image loading in the skeleton).
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
                if (message.text.isNotBlank()) {
                    Text(message.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
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

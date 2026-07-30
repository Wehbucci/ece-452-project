package com.example.grasp.ui.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.grasp.data.model.ChatMessage

/**
 * What a host screen holds while the tutor is open over it.
 *
 * [context] is the display title — a lesson title, a step, the first words of a tapped paragraph.
 * [scope] is what the tutor is actually told, and what keys the stored history. The two travel
 * together because a chat with the wrong one of them is worse than no chat.
 */
data class ChatRequest(val context: String, val scope: ChatScope)

/**
 * The AI tutor, shown as a panel over whatever the user was reading.
 *
 * It used to be a full-screen navigation destination, which meant asking a question about a
 * paragraph replaced the paragraph. The material and the answer about it belong on screen at the
 * same time, so the panel stops short of the top and the host stays visible behind the scrim.
 *
 * Being a sibling of the host rather than a route also means the chat's scope is passed as a value
 * instead of being flattened into a query string and parsed back out — the whole class of bug
 * where a lesson title containing a `?` cut the route in half is gone with it.
 *
 * Renders nothing at all when there is no request and none is animating away, so a host can call
 * this unconditionally without it costing a layout slot.
 *
 * @param request the open chat, or null when closed.
 * @param onDismiss asked for by the back button, the close button and the scrim alike.
 */
@Composable
fun ChatOverlay(
    request: ChatRequest?,
    onDismiss: () -> Unit,
    presenterFactory: (String, ChatScope) -> ChatContract.Presenter = { ctx, s -> ChatPresenter(ctx, s) },
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = request != null

    // Fully closed and not moving: contribute nothing to the host's layout.
    if (!visibility.currentState && !visibility.targetState) return

    // The request has to outlive its own dismissal — the panel is still on screen sliding out
    // after the host has already set it to null, and it should not blank out on the way.
    var showing by remember { mutableStateOf(request) }
    if (request != null) showing = request

    BackHandler(enabled = request != null, onBack = onDismiss)

    // One transition for scrim and panel together, so `visibility.currentState` is a truthful
    // answer to "has it finished leaving" — which is what the early return above depends on. The
    // panel's own slide rides the same transition via `animateEnterExit` rather than a second
    // AnimatedVisibility, because two of them writing to one MutableTransitionState is a race.
    AnimatedVisibility(
        visibleState = visibility,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = ScrimAlpha))
                    // Also what stops a drag from reaching the roadmap board underneath:
                    // `clickable` installs a press gesture that consumes the down event, not
                    // just the tap.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )

            showing?.let { open ->
                // Keyed so that opening a different chat starts a genuinely new conversation:
                // presenter, transcript and half-typed message all reset together.
                key(open) {
                    ChatPanel(
                        request = open,
                        onDismiss = onDismiss,
                        presenterFactory = presenterFactory,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .animateEnterExit(
                                enter = slideInVertically { it },
                                exit = slideOutVertically { it },
                            ),
                    )
                }
            }
        }
    }
}

/** The panel proper: header, conversation, composer. Only composed while there is a chat. */
@Composable
private fun ChatPanel(
    request: ChatRequest,
    onDismiss: () -> Unit,
    presenterFactory: (String, ChatScope) -> ChatContract.Presenter,
    modifier: Modifier = Modifier,
) {
    var history by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }

    val presenter = remember { presenterFactory(request.context, request.scope) }
    val view = remember {
        object : ChatContract.View {
            override fun showMessages(messages: List<ChatMessage>) { history = messages }
        }
    }
    DisposableEffect(presenter, view) {
        presenter.attach(view)
        onDispose { presenter.detach() }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = PanelCorner, topEnd = PanelCorner),
        shadowElevation = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .statusBarsPadding()
            // The gap that keeps the host in view. Tapping it is another way to dismiss.
            .padding(top = HostPeek),
    ) {
        Column(Modifier.fillMaxSize()) {
            ChatPanelHeader(context = request.context, onDismiss = onDismiss)
            ChatPane(
                history = history,
                input = input,
                onInputChange = { input = it },
                onSend = {
                    presenter.onSend(input)
                    input = ""
                },
                onAttach = presenter::onAttachImage,
                onRetry = presenter::onRetry,
                onAcceptProposal = presenter::onAcceptProposal,
                onRejectProposal = presenter::onRejectProposal,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ChatPanelHeader(context: String, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        // Grab handle. Purely a signal that this is a layer over something, not a screen.
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Ask AI", style = MaterialTheme.typography.titleLarge)
                Text(
                    "About: $context",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close chat")
            }
        }
    }
}

/** How much of the host stays uncovered below the status bar — enough to read as a layer. */
private val HostPeek = 44.dp

private val PanelCorner = 28.dp

private const val ScrimAlpha = 0.32f

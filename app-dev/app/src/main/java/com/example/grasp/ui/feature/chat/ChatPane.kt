package com.example.grasp.ui.feature.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.data.model.ChatMessage
import com.example.grasp.ui.components.MarkdownText
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.GameCardBevel
import com.example.grasp.ui.theme.GameDanger
import com.example.grasp.ui.theme.GameDangerTint
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathCard
import com.example.grasp.ui.theme.PathChipNeutralBg
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathNodeCurrentBevel
import com.example.grasp.ui.theme.PathNodeCurrentTint
import com.example.grasp.ui.theme.PathScreenBg

/**
 * The conversation itself: the message list and the composer, and nothing that knows where it is
 * being shown.
 *
 * Split out from the chrome so the tutor can be hosted as a panel over a lesson without the
 * message rendering being duplicated per host. It holds no presenter and no state of its own —
 * whoever shows it owns both.
 *
 * Dressed in the same materials as the rest of the app rather than in stock Material 3: the lilac
 * board background, white cards on hard un-blurred bevels, Fredoka for anything that announces
 * itself and Nunito for anything to be read. The tutor is reached from inside a lesson, so a chat
 * styled as a different product made the app feel like two apps stitched together.
 */
@Composable
internal fun ChatPane(
    history: List<ChatMessage>,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onRetry: (String) -> Unit,
    onAcceptProposal: (String) -> Unit,
    onRejectProposal: (String) -> Unit,
    isSending: Boolean = false,
    isCircuitBroken: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Stay pinned to the bottom as new messages arrive and as streaming text grows.
    LaunchedEffect(history.size, history.lastOrNull()?.text) {
        if (history.isNotEmpty()) listState.animateScrollToItem(history.lastIndex)
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // The same surface the roadmap and every tab sit on, so the bubbles read as cards
                // on the board rather than as a panel borrowed from somewhere else.
                .background(PathScreenBg),
        ) {
            if (history.isEmpty()) {
                EmptyConversation(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(history, key = { it.id }) { message ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MessageBubble(message, onRetry = { onRetry(message.id) })
                            // Under the words that introduced them, not in a bubble of their own:
                            // the sentence explaining the change and the change itself are one
                            // reply.
                            message.proposal?.let { proposal ->
                                ProposalCards(
                                    proposal = proposal,
                                    outcome = message.proposalOutcome,
                                    onAccept = { onAcceptProposal(message.id) },
                                    onReject = { onRejectProposal(message.id) },
                                    modifier = Modifier.padding(start = AvatarGutter),
                                )
                            }
                        }
                    }
                }
            }
        }
        ChatInputBar(
            value = input,
            onValueChange = onInputChange,
            onSend = onSend,
            onAttach = onAttach,
            enabled = !isSending && !isCircuitBroken,
            isCircuitBroken = isCircuitBroken,
        )
    }
}

/**
 * What a fresh chat looks like before anything has been said.
 *
 * The pane used to open as a blank rectangle, which reads as a screen that failed to load rather
 * than as an invitation. One mark, one line, no fake "suggested questions" — the user opened this
 * from a specific paragraph and already knows what they want to ask.
 */
@Composable
private fun EmptyConversation(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(PathNodeCurrentTint, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("✦", fontSize = 30.sp, color = PathNodeCurrent)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Ask me anything",
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 19.sp,
            color = PathInk,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "I can see what you're reading, so you can just say “explain this differently” " +
                "or “give me an example”.",
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            color = PathMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * One message.
 *
 * The assistant's replies carry a small mark and hang off the left margin; the user's sit on the
 * right in the app's indigo with no mark at all. Only one side needs identifying — the user knows
 * which words are theirs — and giving both an avatar doubled the furniture for no information.
 */
@Composable
private fun MessageBubble(message: ChatMessage, onRetry: () -> Unit) {
    val isUser = message.author == ChatMessage.Author.USER
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            TutorMark()
            Spacer(Modifier.width(AvatarGap))
        }

        val (face, bevel, ink) = when {
            message.failed -> Triple(GameDangerTint, GameDanger.copy(alpha = 0.22f), GameDanger)
            isUser -> Triple(PathNodeCurrent, PathNodeCurrentBevel, Color.White)
            else -> Triple(PathCard, GameCardBevel, PathInk)
        }

        BubbleSurface(
            face = face,
            bevelColor = bevel,
            // The square-ish corner points back at whoever is speaking, which is what makes a
            // column of bubbles readable at a glance without labelling every one of them.
            shape = RoundedCornerShape(
                topStart = if (isUser) BubbleCorner else BubbleTail,
                topEnd = if (isUser) BubbleTail else BubbleCorner,
                bottomStart = BubbleCorner,
                bottomEnd = BubbleCorner,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                if (message.imageUri != null) {
                    AttachedPhoto(
                        onDark = isUser,
                        modifier = Modifier.padding(
                            bottom = if (message.text.isNotBlank()) 8.dp else 0.dp,
                        ),
                    )
                }
                when {
                    // Nothing has arrived yet: the dots are the whole message.
                    message.pending && message.text.isEmpty() -> TypingIndicator()

                    message.failed -> FailedReply(message.text, onRetry)

                    message.text.isNotBlank() -> {
                        MarkdownText(
                            text = message.text,
                            style = TextStyle(
                                fontFamily = NunitoFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                color = ink,
                            ),
                        )
                        // Text has started but the stream is still open. Without this the reply
                        // looks finished the moment the first chunk lands, and a user who reads
                        // fast enough sees a sentence cut off mid-thought and assumes it broke.
                        if (message.pending) {
                            Spacer(Modifier.height(8.dp))
                            TypingIndicator()
                        }
                    }
                }
            }
        }
    }
}

/** The tutor's mark beside its replies — the same sparkle the "Ask AI" affordances carry. */
@Composable
private fun TutorMark() {
    Box(
        modifier = Modifier
            .size(AvatarSize)
            .background(PathNodeCurrentTint, RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("✦", fontSize = 15.sp, color = PathNodeCurrent)
    }
}

/**
 * A bubble on the app's hard bevel.
 *
 * Not [com.example.grasp.ui.components.GameCard], which stretches its content to full width — a
 * chat bubble has to hug its text or a two-word reply spans the panel. Same construction though:
 * a second copy of the shape painted [BubbleBevel] lower, zero blur.
 */
@Composable
private fun BubbleSurface(
    face: Color,
    bevelColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.padding(bottom = BubbleBevel)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = BubbleBevel)
                .background(bevelColor, shape),
        )
        Column(Modifier.clip(shape).background(face), content = content)
    }
}

/** Placeholder for an attached photo until real image loading lands. */
@Composable
private fun AttachedPhoto(onDark: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 180.dp, height = 120.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (onDark) Color.White.copy(alpha = 0.18f) else PathChipNeutralBg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "📷 Photo",
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (onDark) Color.White else PathMuted,
        )
    }
}

/** A reply that never arrived. Says so plainly and offers the one action that helps. */
@Composable
private fun FailedReply(text: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = text,
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = GameDanger,
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(GameDanger.copy(alpha = 0.12f))
                .clickable(onClick = onRetry)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                tint = GameDanger,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Try again",
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.5.sp,
                color = GameDanger,
            )
        }
    }
}

/** Three dots breathing in turn, in the app's accent rather than as bullet characters. */
@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            val scale by transition.animateFloat(
                initialValue = 0.55f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 420,
                        delayMillis = i * 140,
                        easing = FastOutSlowInEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(7.dp)
                    .background(PathNodeCurrent.copy(alpha = scale), CircleShape),
            )
        }
    }
}

/**
 * The composer: the same borderless-field-in-a-bevelled-card as Home's topic prompt, so asking the
 * tutor a question and asking the app for a roadmap feel like the same gesture.
 */
@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    enabled: Boolean = true,
    isCircuitBroken: Boolean = false,
) {
    Surface(color = PathCard, shadowElevation = 14.dp) {
        Column {
            if (isCircuitBroken) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GameDangerTint)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Chat is unavailable due to repeated failures.",
                        style = MaterialTheme.typography.labelSmall,
                        color = GameDanger,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // The MAX of the two, not the sum. Stacking `navigationBarsPadding()` and
                    // `imePadding()` added both, and since the IME's inset already spans the gesture
                    // bar it sat the composer a nav-bar's height above the keyboard.
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AttachButton(onClick = onAttach, enabled = enabled)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(PathScreenBg)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        textStyle = TextStyle(
                            color = if (enabled) PathInk else PathMuted,
                            fontFamily = NunitoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                        ),
                        cursorBrush = SolidColor(PathNodeCurrent),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (value.isEmpty()) {
                        Text(
                            text = if (isCircuitBroken) "Unavailable" else "Ask about your material…",
                            fontFamily = NunitoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = PathFaint,
                        )
                    }
                }

                SendButton(enabled = enabled && value.isNotBlank(), onClick = onSend)
            }
        }
    }
}

/** Quiet, square, and never the thing your eye lands on — the send button is. */
@Composable
private fun AttachButton(onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .padding(bottom = ControlBevel)
            .size(ControlSize)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) PathChipNeutralBg else PathFaint)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = "Attach photo",
            tint = if (enabled) PathMuted else PathFaint,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** The round bevelled "go" button, with the same press-into-its-shadow physics as Home's. */
@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val sink by animateDpAsState(
        targetValue = if (pressed && enabled) ControlBevel else 0.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "sendSink",
    )
    val alpha = if (enabled) 1f else 0.3f

    Box(modifier = Modifier.padding(bottom = ControlBevel)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = ControlBevel)
                .background(PathNodeCurrentBevel.copy(alpha = alpha), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(ControlSize)
                .offset(y = sink)
                .clip(CircleShape)
                .background(PathNodeCurrent.copy(alpha = alpha))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** The tutor's mark, and the gap between it and the bubble it belongs to. */
private val AvatarSize = 30.dp
private val AvatarGap = 8.dp

/** How far a proposal card is inset so it lines up with the reply that introduced it. */
private val AvatarGutter = AvatarSize + AvatarGap

/** Bubble geometry: generous everywhere except the one corner that points at the speaker. */
private val BubbleCorner = 20.dp
private val BubbleTail = 6.dp

/** Shallower than a card's 5dp — a bubble is small, and the full bevel reads as a drop shadow. */
private val BubbleBevel = 3.dp

/** Composer controls: both buttons share a size so the row reads as one strip. */
private val ControlSize = 46.dp
private val ControlBevel = 4.dp

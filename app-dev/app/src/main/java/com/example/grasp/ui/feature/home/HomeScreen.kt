package com.example.grasp.ui.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.data.model.Mode
import com.example.grasp.data.model.SavedItem
import com.example.grasp.ui.components.GameCard
import com.example.grasp.ui.components.GameIconTile
import com.example.grasp.ui.components.GameModeToggle
import com.example.grasp.ui.components.GameScreenHeader
import com.example.grasp.ui.components.GameSectionHeader
import com.example.grasp.ui.components.GraspBottomBar
import com.example.grasp.ui.components.ModeGlyph
import com.example.grasp.ui.components.accent
import com.example.grasp.ui.components.bevel
import com.example.grasp.ui.navigation.TopLevelDestination
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.GameDanger
import com.example.grasp.ui.theme.GameDangerTint
import com.example.grasp.ui.theme.GameHeroEnd
import com.example.grasp.ui.theme.GameHeroStart
import com.example.grasp.ui.theme.GraspTheme
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathNodeCurrentBevel
import com.example.grasp.ui.theme.PathNodeCurrentTint
import com.example.grasp.ui.theme.PathScreenBg
import kotlin.math.roundToInt

/**
 * Home / topic-entry screen (View). Top-level destination, so it hosts the bottom nav bar.
 *
 * Visually this is the "front door" of the gamified shell: the lilac journey background, one
 * chunky mode selector, a single prominent prompt box, and — when the user has something in
 * flight — a hero banner that resumes it in one tap. Everything is built from the shared
 * `Game*` components so it stays in step with the roadmap screen.
 *
 * Follows the MVP wiring documented in
 * [com.example.grasp.ui.feature.auth.LoginScreen] — UI state + presenter + anonymous View
 * + DisposableEffect attach/detach.
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
    var resumable by remember { mutableStateOf<SavedItem?>(null) }
    var mode by remember { mutableStateOf(Mode.LEARNER) }
    var query by remember { mutableStateOf("") }
    // Non-null while the roadmap and its lessons are being written (that whole job runs before
    // navigation, so Home would otherwise just sit there looking frozen).
    var generatingTopic by remember { mutableStateOf<String?>(null) }
    var generationFailed by remember { mutableStateOf(false) }

    val presenter = remember { presenterFactory() }
    val view = remember(onOpenLearner, onOpenTinker) {
        object : HomeContract.View {
            override fun showContinue(item: SavedItem?) { resumable = item }
            override fun showGenerating(topic: String?) {
                generatingTopic = topic
                if (topic != null) generationFailed = false
            }
            override fun showGenerationFailed() { generationFailed = true }
            override fun openLearner(pathId: String) = onOpenLearner(pathId)
            override fun openTinker(pathId: String) = onOpenTinker(pathId)
        }
    }
    DisposableEffect(presenter, view) {
        presenter.attach(view)
        onDispose { presenter.detach() }
    }

    Scaffold(
        containerColor = PathScreenBg,
        bottomBar = {
            GraspBottomBar(selected = TopLevelDestination.HOME, onSelect = onSelectTab)
        },
    ) { padding ->
        // Generation writes the roadmap AND every lesson on it before navigating, so the whole
        // screen hands over to a progress state rather than appearing to have hung.
        val generating = generatingTopic
        if (generating != null) {
            GeneratingState(topic = generating, modifier = Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                GameScreenHeader(
                    eyebrow = "Hi there 👋",
                    title = if (mode == Mode.LEARNER) "What do you want to learn?"
                    else "What do you want to make?",
                )
            }

            // Mode picker — Learn vs Tinker (overview.md §3). The tagline under it explains what
            // the app will actually produce, so the choice never feels arbitrary.
            item {
                Column {
                    GameModeToggle(selected = mode, onSelect = { mode = it })
                    Text(
                        text = mode.tagline,
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = PathMuted,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
            }

            // Free-text topic/task entry — the single most important control on the screen —
            // and, underneath it, the "jump back in" banner. They share one item so the banner
            // can fade in when the presenter finds an unfinished path without leaving a gap in
            // the layout while there is nothing to resume.
            item {
                Column {
                    TopicPrompt(
                        query = query,
                        mode = mode,
                        onQueryChange = { query = it },
                        onSubmit = {
                            presenter.onSubmitTopic(query, mode)
                            query = ""
                        },
                    )
                    if (generationFailed) {
                        GenerationFailedCard(modifier = Modifier.padding(top = 10.dp))
                    }
                    AnimatedVisibility(
                        visible = resumable != null,
                        enter = fadeIn() + expandVertically(),
                    ) {
                        resumable?.let { saved ->
                            ContinueCard(
                                item = saved,
                                onClick = { presenter.onContinueClicked(saved) },
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                }
            }

            item {
                GameSectionHeader(
                    text = "New here?",
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
            }

            item {
                ExamplesCard(onClick = { onSelectTab(TopLevelDestination.LIBRARY) })
            }
        }
    }
}

/**
 * Full-screen progress while a topic is generated.
 *
 * Names both things being built, because the wait covers the roadmap AND every lesson on it — a
 * bare spinner would read as the app having hung.
 */
@Composable
private fun GeneratingState(topic: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = PathNodeCurrent, strokeWidth = 4.dp)
        Text(
            text = "Building your roadmap",
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = PathInk,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Breaking “$topic” into subtopics and writing every lesson. This takes a " +
                "moment, and then it's yours to keep, even offline.",
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = PathMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** Generation failed — the user never left Home, so this just explains and invites a retry. */
@Composable
private fun GenerationFailedCard(modifier: Modifier = Modifier) {
    GameCard(
        modifier = modifier.fillMaxWidth(),
        color = GameDangerTint,
        bevelColor = GameDanger.copy(alpha = 0.25f),
    ) {
        Text(
            text = "We couldn't build that roadmap. Check your connection and try again.",
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = GameDanger,
            modifier = Modifier.padding(14.dp),
        )
    }
}

/**
 * The prompt box: a bevelled card holding a borderless field and a chunky circular submit
 * button that takes the current mode's color, so the box itself signals what will be generated.
 * The button dims until there is something to send.
 */
@Composable
private fun TopicPrompt(
    query: String,
    mode: Mode,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val canSubmit = query.isNotBlank()
    val submit = {
        if (canSubmit) {
            keyboard?.hide()
            onSubmit()
        }
    }

    GameCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = PathInk,
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    ),
                    cursorBrush = SolidColor(mode.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.isEmpty()) {
                    Text(
                        text = if (mode == Mode.LEARNER) "Type a topic, e.g. Machine learning"
                        else "Type a task, e.g. Make an omelette",
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = PathFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            SubmitButton(mode = mode, enabled = canSubmit, onClick = submit)
        }
    }
}

/** Round, bevelled "go" button — the same press-into-its-shadow physics as a roadmap node. */
@Composable
private fun SubmitButton(
    mode: Mode,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val bevel = 4.dp
    val sink by animateDpAsState(
        targetValue = if (pressed && enabled) bevel else 0.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "submitSink",
    )
    val alpha = if (enabled) 1f else 0.35f

    Box(modifier = Modifier.padding(bottom = bevel)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = bevel)
                .background(mode.bevel.copy(alpha = alpha), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(46.dp)
                .offset(y = sink)
                .clip(CircleShape)
                .background(mode.accent.copy(alpha = alpha))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Generate",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * "Jump back in" hero banner — the indigo gradient card that resumes the path the user is
 * closest to finishing (chosen by [HomePresenter]).
 */
@Composable
private fun ContinueCard(
    item: SavedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val percent = (item.progress * 100).roundToInt()
    GameCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        brush = Brush.linearGradient(listOf(GameHeroStart, GameHeroEnd)),
        bevelColor = PathNodeCurrentBevel,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "JUMP BACK IN",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$percent%",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.title,
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 19.sp,
                lineHeight = 24.sp,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            // White-on-indigo variant of the shared bar so the hero keeps one flat color story.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(item.progress.coerceIn(0.02f, 1f))
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.subtitle,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Continue",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = Color.White,
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * The way in to the ready-made examples, which live in the Library rather than here.
 *
 * This replaced a list of "popular topics". Those were only ever suggestions — each one pointed
 * at a path id that had never been generated, so tapping one opened a roadmap with nothing in it.
 * The examples are real saved items now (see `StarterLibrary`), already sitting in the Library
 * alongside the user's own work, which is where a thing you can open, finish and delete belongs.
 * So Home links to them instead of pretending to hold its own copies.
 */
@Composable
private fun ExamplesCard(onClick: () -> Unit) {
    GameCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GameIconTile(tint = PathNodeCurrentTint) {
                ModeGlyph(mode = Mode.LEARNER, tint = PathNodeCurrent, size = 24.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Check out the ready-made examples",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    color = PathInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "A couple of roadmaps and a guide are already in your library — " +
                        "open one to see how this works.",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = PathMuted,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = PathFaint,
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

package com.example.grasp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.data.model.Mode
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.GameCardBevel
import com.example.grasp.ui.theme.GameTintAmber
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathCard
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeBranch
import com.example.grasp.ui.theme.PathNodeBranchBevel
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathNodeCurrentBevel
import com.example.grasp.ui.theme.PathNodeCurrentTint
import com.example.grasp.ui.theme.PathNodeDone
import com.example.grasp.ui.theme.PathXpTrack

/**
 * The interactive pieces of the game shell: chunky buttons, pills, chips, the Learn/Tinker
 * toggle and the progress bar. All are dumb and stateless — they render what they are given
 * and report taps.
 *
 * Every one of them re-uses the same two ideas as the roadmap nodes: a hard bevel instead of a
 * blurred shadow, and one accent color per meaning.
 */

// ---------------------------------------------------------------------------
// Mode styling — Learn is indigo, Tinker is amber. App-wide, no exceptions.
// ---------------------------------------------------------------------------
/** Full-strength accent for a [Mode] (pill text, node fill, selected segment). */
val Mode.accent: Color
    get() = when (this) {
        Mode.LEARNER -> PathNodeCurrent
        Mode.TINKERER -> PathNodeBranch
    }

/** The faint container that pairs with [accent] (pill background, icon tile). */
val Mode.tint: Color
    get() = when (this) {
        Mode.LEARNER -> PathNodeCurrentTint
        Mode.TINKERER -> GameTintAmber
    }

/** The hard bevel that pairs with [accent] (chunky buttons in this mode's color). */
val Mode.bevel: Color
    get() = when (this) {
        Mode.LEARNER -> PathNodeCurrentBevel
        Mode.TINKERER -> PathNodeBranchBevel
    }

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

/**
 * The primary "game button": a solid accent slab on a hard bevel that presses down on tap.
 * Used for every committing action in the shell (Generate, Continue, Log out …).
 *
 * @param accent fill color; [bevelColor] should be its darker shade.
 * @param leading optional glyph drawn before the label.
 */
@Composable
fun GameButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = PathNodeCurrent,
    bevelColor: Color = PathNodeCurrentBevel,
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    leading: @Composable (RowScope.() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val bevel = 5.dp
    val sink by animateDpAsState(
        targetValue = if (pressed && enabled) bevel else 0.dp,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "buttonSink",
    )
    val shape = RoundedCornerShape(18.dp)
    val alpha = if (enabled) 1f else 0.45f

    Box(modifier = modifier.padding(bottom = bevel)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = bevel)
                .background(bevelColor.copy(alpha = alpha), shape),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = sink)
                .clip(shape)
                .background(accent.copy(alpha = alpha))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (leading != null) {
                leading()
            }
            Text(
                text = label,
                color = contentColor,
                fontFamily = FredokaFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Pills & chips
// ---------------------------------------------------------------------------

/**
 * A small status/label pill — the shell's version of [StatusPill], typed to the game palette so
 * the tint is chosen deliberately instead of derived with an alpha.
 */
@Composable
fun GameTag(
    text: String,
    accent: Color,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tint)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            color = accent,
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            letterSpacing = 0.4.sp,
        )
    }
}

/**
 * A selectable filter chip (Library's All / Learn / Tinker row). Selected = solid accent;
 * unselected = white card with a hairline border.
 */
@Composable
fun GameChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = PathNodeCurrent,
) {
    val background by animateColorAsState(
        targetValue = if (selected) accent else PathCard,
        animationSpec = tween(180),
        label = "chipBg",
    )
    val content by animateColorAsState(
        targetValue = if (selected) Color.White else PathMuted,
        animationSpec = tween(180),
        label = "chipFg",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .then(
                if (selected) Modifier else Modifier.border(1.5.dp, GameCardBevel, RoundedCornerShape(percent = 50)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            color = content,
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// Mode toggle
// ---------------------------------------------------------------------------

/**
 * The Learn / Tinker switch on Home — two chunky segments in one tray.
 *
 * The selected segment fills with that mode's [accent] and shows its glyph in white; the other
 * stays flat. Colors animate so switching modes feels like flipping a physical selector.
 */
@Composable
fun GameModeToggle(
    selected: Mode,
    onSelect: (Mode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PathCard)
            .border(1.5.dp, GameCardBevel, RoundedCornerShape(20.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Mode.entries.forEach { mode ->
            ModeSegment(
                mode = mode,
                selected = mode == selected,
                onClick = { onSelect(mode) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModeSegment(
    mode: Mode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (selected) mode.accent else Color.Transparent,
        animationSpec = tween(200),
        label = "segmentBg",
    )
    val content by animateColorAsState(
        targetValue = if (selected) Color.White else PathMuted,
        animationSpec = tween(200),
        label = "segmentFg",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeGlyph(mode = mode, tint = content, size = 17.dp)
        Text(
            text = mode.label,
            color = content,
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Progress
// ---------------------------------------------------------------------------

/**
 * The shell's progress bar: a chunky rounded track with an animated gradient fill, matching the
 * roadmap's XP bar. Completed (100%) bars are drawn in the same green as a finished node, so
 * "green means done" holds everywhere.
 *
 * @param fraction 0f..1f (clamped).
 * @param accent fill color; the gradient runs from a lightened accent into it.
 */
@Composable
fun GameProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    accent: Color = PathNodeDone,
    height: Dp = 12.dp,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 650),
        label = "progressFill",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(PathXpTrack),
    ) {
        Box(
            modifier = Modifier
                // A hair of width even at 0% so an untouched path still reads as "a bar".
                .fillMaxWidth(animated.coerceAtLeast(0.02f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(percent = 50))
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.75f), accent),
                    ),
                ),
        )
    }
}

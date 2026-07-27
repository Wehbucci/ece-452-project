package com.example.grasp.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.grasp.ui.theme.GameCardBevel
import com.example.grasp.ui.theme.PathCard

/**
 * The app-shell counterpart of the roadmap's chunky node button: a card that sits on a hard,
 * un-blurred bevel and *presses into* it when tapped.
 *
 * Why not [androidx.compose.material3.Card]? Material's elevation is a soft, blurred shadow —
 * the exact thing the gamified design avoids (see `design_handoff_gamified_path/README.md`,
 * "game bevel"). Here the bevel is a second copy of the same [shape] painted [bevel] dp lower
 * in [bevelColor]; on press the top layer slides down by that same amount, so the card visually
 * "bottoms out" like a physical key. That single detail is what makes Home / Library / Profile
 * feel like the same toy as the path screen.
 *
 * Layout note: the bevel is drawn OUTSIDE the content bounds, so the composable reserves [bevel]
 * dp of bottom padding for it. Callers can therefore space cards normally and the shadow never
 * collides with the next row.
 *
 * @param onClick tap handler; pass `null` for a non-interactive (display-only) card, which also
 *   disables the press animation.
 * @param brush optional gradient fill; when set it wins over [color] (used by hero banners).
 * @param bevel thickness of the hard bottom edge. 5.dp matches the roadmap nodes.
 */
@Composable
fun GameCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(22.dp),
    color: Color = PathCard,
    brush: Brush? = null,
    bevelColor: Color = GameCardBevel,
    bevel: Dp = 5.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val sink by animateDpAsState(
        targetValue = if (pressed && onClick != null) bevel else 0.dp,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "cardSink",
    )

    Box(modifier = modifier.padding(bottom = bevel)) {
        // The hard edge. Same shape, painted one bevel-height lower, zero blur.
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = bevel)
                .background(bevelColor, shape),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = sink)
                .clip(shape)
                .then(if (brush != null) Modifier.background(brush) else Modifier.background(color))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            // No ripple: the sink animation IS the press feedback.
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ),
            content = content,
        )
    }
}

package com.example.grasp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.grasp.ui.theme.PathXpFillEnd
import com.example.grasp.ui.theme.PathXpFillStart
import com.example.grasp.ui.theme.PathXpTrack

/**
 * The HUD XP bar: a rounded track whose amber gradient fill animates its width toward
 * [fraction] (0f..1f) with a springy overshoot when the user earns XP.
 *
 * Dumb/stateless: [fraction] comes from [com.example.grasp.ui.feature.path.PathUiState]; the
 * only "state" here is the transient tween on width, which is a pure presentation concern.
 */
@Composable
fun XpBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700),
        label = "xpFill",
    )
    Box(
        modifier = modifier
            .height(14.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(PathXpTrack),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(RoundedCornerShape(percent = 50))
                .background(Brush.horizontalGradient(listOf(PathXpFillStart, PathXpFillEnd))),
        )
    }
}

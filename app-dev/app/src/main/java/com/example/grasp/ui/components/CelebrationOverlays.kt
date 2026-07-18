package com.example.grasp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathNodeBranch
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathNodeDone
import com.example.grasp.ui.theme.PathStreak
import com.example.grasp.ui.theme.PathXpFillEnd
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Celebratory, one-shot overlays for the journey. Each is dumb and driven by a signal the
 * Screen flips: a bumped [Int] key (confetti), a nullable value (ribbon / toast). They render
 * nothing when idle, so they can sit unconditionally in the Screen's overlay Box.
 */

private val ConfettiColors = listOf(PathNodeDone, PathNodeCurrent, PathNodeBranch, PathXpFillEnd, PathStreak)
private const val ConfettiPieces = 34

/**
 * A radial confetti burst played once whenever [trigger] increases (a completion). ~34 pieces
 * fan out from the upper third and fall with a little gravity, fading as they go.
 */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    var active by remember { mutableStateOf(false) }

    LaunchedEffect(trigger) {
        if (trigger > 0) {
            active = true
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 1100, easing = LinearOutSlowInEasing))
            active = false
        }
    }

    if (!active) return
    Canvas(modifier.fillMaxSize()) {
        val originX = size.width / 2f
        val originY = size.height * 0.28f
        val p = progress.value
        val alpha = (1f - p).coerceIn(0f, 1f)
        repeat(ConfettiPieces) { i ->
            val angle = (i / ConfettiPieces.toFloat()) * (2f * PI).toFloat()
            val speed = 220f + (i * 37 % 140)
            val x = originX + cos(angle) * speed * p
            val y = originY + sin(angle) * speed * p + 420f * p * p // gravity
            rotate(degrees = angle * 57f + p * 320f, pivot = Offset(x, y)) {
                drawRect(
                    color = ConfettiColors[i % ConfettiColors.size].copy(alpha = alpha),
                    topLeft = Offset(x - 5f, y - 3f),
                    size = Size(10f, 6f),
                )
            }
        }
    }
}

/**
 * The "LEVEL UP" ribbon that crosses the screen once when [level] becomes non-null (crossing a
 * 200-XP multiple). It slides in, holds, slides out, then calls [onFinished] so the Screen can
 * clear the signal.
 */
@Composable
fun LevelUpRibbon(level: Int?, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    if (level == null) return
    val progress = remember(level) { Animatable(0f) }
    LaunchedEffect(level) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 1900))
        onFinished()
    }
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val p = progress.value
        // in (0–0.22) · hold (0.22–0.72) · out (0.72–1).
        val factor = when {
            p < 0.22f -> -1f + (p / 0.22f)
            p > 0.72f -> (p - 0.72f) / 0.28f
            else -> 0f
        }
        Surface(
            color = PathNodeBranch,
            contentColor = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = maxWidth * factor),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(
                    text = "  LEVEL UP!  ·  Lv $level",
                    fontFamily = FredokaFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

/**
 * A transient bottom toast (locked tap, "branch added", …). Shows for ~2s then calls
 * [onFinished]. Kept as an in-Compose overlay (not android.widget.Toast) so it's preview-safe
 * and the presenter can stay free of Android imports.
 */
@Composable
fun PathToast(message: String?, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf("") }
    LaunchedEffect(message) {
        if (message != null) {
            shown = message
            delay(2000)
            onFinished()
        }
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut(),
        ) {
            Surface(
                color = PathInk,
                contentColor = Color.White,
                shape = RoundedCornerShape(percent = 50),
                shadowElevation = 6.dp,
                modifier = Modifier.padding(bottom = 28.dp),
            ) {
                Text(
                    text = shown,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

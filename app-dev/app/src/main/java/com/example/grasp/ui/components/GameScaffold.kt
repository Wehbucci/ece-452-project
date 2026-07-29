package com.example.grasp.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.GameDivider
import com.example.grasp.ui.theme.GameSkeleton
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathCard
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted

/**
 * Page furniture for the game shell: screen titles, section headers, settings rows, stat tiles,
 * empty states and loading skeletons.
 *
 * These exist so the three tab screens share one rhythm (same title size, same 20.dp gutter,
 * same row height) instead of each re-inventing a header. All dumb and stateless.
 */

/**
 * The big screen title. Fredoka, generous size, with an optional eyebrow above and trailing
 * slot (streak pill, count …) on the right.
 */
@Composable
fun GameScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(
                    text = eyebrow,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.3.sp,
                    color = PathMuted,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = title,
                fontFamily = FredokaFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 27.sp,
                lineHeight = 32.sp,
                letterSpacing = (-0.4).sp,
                color = PathInk,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = PathMuted,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/**
 * Header for a deep (non-tab) screen: a back tile and a centered-left title, matching the back
 * chevron of the roadmap HUD so "going deeper" always looks the same.
 */
@Composable
fun GameTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(PathCard)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = PathInk,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = title,
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 19.sp,
            color = PathInk,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

/** A group label inside a screen ("Popular topics", "Your stats"), with an optional right slot. */
@Composable
fun GameSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            color = PathInk,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
}

/**
 * One row of a settings group (Profile). Icon tile · title (+ optional subtitle) · trailing slot
 * or chevron. Rows live inside a single [GameCard], separated by [GameRowDivider].
 *
 * @param onClick `null` renders the row as non-interactive — used for rows whose feature is not
 *   built yet, which pair it with a "Soon" [GameTag] instead of a chevron that goes nowhere.
 */
@Composable
fun GameSettingRow(
    title: String,
    tint: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    glyph: @Composable () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameIconTile(tint = tint, size = 42.dp, corner = 14.dp) { glyph() }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = title,
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = PathInk,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = PathMuted,
                )
            }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = PathFaint,
            )
        }
    }
}

/** Hairline separator between [GameSettingRow]s inside one card. */
@Composable
fun GameRowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 72.dp)
            .height(1.dp)
            .background(GameDivider),
    )
}

/**
 * A single number + label tile, used for the profile's progress summary. The value is the loud
 * part (Fredoka, accent-colored); the label stays quiet underneath.
 */
@Composable
fun GameStatTile(
    value: String,
    label: String,
    accent: Color,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(tint)
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = accent,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            letterSpacing = 0.3.sp,
            textAlign = TextAlign.Center,
            color = PathMuted,
        )
    }
}

/**
 * The "nothing here yet" state: an illustration, a friendly line, and (optionally) the one
 * action that fixes it. Empty screens are where an app feels unfinished, so this one gets art.
 */
@Composable
fun GameEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    art: @Composable (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (art != null) {
            art()
            Spacer(Modifier.height(20.dp))
        }
        Text(
            text = title,
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            color = PathInk,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = PathMuted,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(22.dp))
            action()
        }
    }
}

/**
 * A pulsing placeholder card shown while a list is still loading, so the screen has shape
 * before it has data (no blank flash on cold start).
 */
@Composable
fun GameSkeletonCard(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    GameCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .alpha(pulse),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(GameSkeleton, RoundedCornerShape(15.dp)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBar(width = 150.dp)
                SkeletonBar(width = 90.dp, height = 9.dp)
            }
        }
    }
}

@Composable
private fun SkeletonBar(width: Dp, height: Dp = 12.dp) {
    Box(
        Modifier
            .width(width)
            .height(height)
            .background(GameSkeleton, RoundedCornerShape(percent = 50)),
    )
}

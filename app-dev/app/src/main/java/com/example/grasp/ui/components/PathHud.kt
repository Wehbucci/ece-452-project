package com.example.grasp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathCard
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathScreenBg

/**
 * The fixed, non-scrolling HUD header of the journey.
 *
 * Row 1: back chevron · title + "{n} of {total} lessons mastered" · streak pill.
 * Row 2: LEVEL badge · XP bar · "{xpIn}/{target} XP" label.
 *
 * Stateless: every number comes from [com.example.grasp.ui.feature.path.PathUiState]; it just
 * forwards the [onBack] tap. Composed from [LevelBadge], [XpBar] and [StreakPill].
 */
@Composable
fun PathHud(
    title: String,
    masteredCount: Int,
    totalLessons: Int,
    streak: Int,
    level: Int,
    xpInLevel: Int,
    xpPerLevel: Int,
    xpFraction: Float,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = PathCard,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp),
                    color = PathScreenBg,
                    contentColor = PathInk,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontFamily = FredokaFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 19.sp,
                        color = PathInk,
                    )
                    Text(
                        text = "$masteredCount of $totalLessons lessons mastered",
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = PathMuted,
                    )
                }
                StreakPill(count = streak)
            }

            // Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LevelBadge(level = level)
                XpBar(fraction = xpFraction, modifier = Modifier.weight(1f))
                Text(
                    text = "$xpInLevel/$xpPerLevel XP",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = PathMuted,
                )
            }
        }
    }
}

package com.example.grasp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathChipNeutralBg
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathStreak

/**
 * The HUD "streak" pill: a flame plus the number of days in a row the user has studied (e.g. 🔥 6).
 *
 * Dumb/stateless — the count is passed in from [com.example.grasp.ui.feature.path.PathUiState],
 * where it is a real figure derived from `core.progress.StudyStreak`.
 *
 * At zero the pill goes grey and the flame dims rather than disappearing. Hiding it would shift
 * the HUD's layout the moment a streak lapsed, and it would take away the one thing that says what
 * a person is a day away from having; a cold flame reading "0" is the honest version of both.
 *
 * We keep the 🔥 emoji (the design explicitly allows it in the HUD) so we don't depend on the
 * material-icons-extended artifact, which isn't on the classpath.
 */
@Composable
fun StreakPill(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val alight = count > 0
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = if (alight) PathStreak.copy(alpha = 0.12f) else PathChipNeutralBg,
        contentColor = if (alight) PathStreak else PathFaint,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // The unlit flame is the same glyph at low opacity, so a lapsed streak reads as the
            // same thing gone cold rather than as a different badge.
            Text("🔥", fontSize = 14.sp, modifier = Modifier.alpha(if (alight) 1f else 0.45f))
            Text(
                text = count.toString(),
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
            )
        }
    }
}

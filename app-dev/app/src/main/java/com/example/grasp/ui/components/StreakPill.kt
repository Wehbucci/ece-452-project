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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathStreak

/**
 * The HUD "streak" pill: a flame plus the current streak count (e.g. 🔥 6).
 *
 * Dumb/stateless — the count is passed in from [com.example.grasp.ui.feature.path.PathUiState].
 * We keep the 🔥 emoji (the design explicitly allows it in the HUD) so we don't depend on the
 * material-icons-extended artifact, which isn't on the classpath.
 */
@Composable
fun StreakPill(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = PathStreak.copy(alpha = 0.12f),
        contentColor = PathStreak,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("🔥", fontSize = 14.sp)
            Text(
                text = count.toString(),
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
            )
        }
    }
}

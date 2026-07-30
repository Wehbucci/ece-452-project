package com.example.grasp.ui.feature.path

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathNodeBranch

/**
 * Ask the tutor about the roadmap itself.
 *
 * Its own control, and not the "Ask AI" that already lives inside a lesson, because the two ask
 * genuinely different questions: that one is about the material in front of you, this one is about
 * the shape of the whole plan — what to do next, what an early section is for, whether anything is
 * missing. Answering those needs the tree, which a lesson-scoped chat never sees.
 *
 * It sits opposite "Edit roadmap" as its quiet peer, and the board hides it while it is being used
 * as a picker so that nothing competes with "tap a section".
 *
 * Unlike the other three entry points this one carries no "Continue chat" state: the board's
 * presenter has no chat repository to ask, and giving it one for a label was not worth it.
 */
@Composable
fun AskRoadmapButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = Color.White,
        contentColor = PathInk,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "✦", fontSize = 16.sp, color = PathNodeBranch)
            Text(
                // Short, because it shares the bottom of the board with "Edit roadmap" and a
                // narrow phone has room for both only if neither spells itself out.
                text = "Ask AI",
                fontFamily = FredokaFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
        }
    }
}

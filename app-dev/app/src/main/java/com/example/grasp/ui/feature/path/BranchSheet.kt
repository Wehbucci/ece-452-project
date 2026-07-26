package com.example.grasp.ui.feature.path

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeBranch
import com.example.grasp.ui.theme.PathNodeBranchBevel
import com.example.grasp.ui.theme.PathNodeCurrentTint
import com.example.grasp.ui.theme.PathRegionPill

/**
 * The "grow your path" body shown inside a Material 3 `ModalBottomSheet`.
 *
 * Mostly stateless — it owns only the ephemeral text/chip selection for the pending branch name
 * and hands the final name to [onGenerate]; all graph mutation happens in `PathPresenter`.
 *
 * @param suggestions starter chips for THIS path, generated from the node the branch grows off.
 *        Empty while they load, or if they couldn't be produced — the text field is the real input.
 * @param generating true while the AI is building the branch: the button locks so a second tap
 *        can't grow two branches from the same spot.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BranchSheetContent(
    suggestions: List<String>,
    generating: Boolean,
    onGenerate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(shape = RoundedCornerShape(percent = 50), color = PathNodeBranch.copy(alpha = 0.16f), contentColor = PathNodeBranch) {
            Text(
                text = "＋ GROW YOUR PATH",
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        Text(
            text = "Branch out",
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            color = PathInk,
        )
        Text(
            text = "Spin off a new direction from where you are. Pick a starter or describe your own — " +
                "we'll grow a fresh branch on your tree.",
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = PathMuted,
        )

        if (suggestions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { suggestion ->
                    val selected = name == suggestion
                    Surface(
                        onClick = { name = suggestion },
                        shape = RoundedCornerShape(percent = 50),
                        color = if (selected) PathNodeCurrentTint else PathRegionPill,
                        contentColor = PathInk,
                    ) {
                        Text(
                            text = suggestion,
                            fontFamily = NunitoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name your branch") },
            singleLine = true,
            enabled = !generating,
            modifier = Modifier.fillMaxWidth(),
        )

        // Amber "generate" bevel button.
        val enabled = name.isNotBlank() && !generating
        Box(modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Box(
                Modifier
                    .matchParentSize()
                    .offset(y = 5.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PathNodeBranchBevel),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (enabled) PathNodeBranch else PathNodeBranch.copy(alpha = 0.55f))
                    .clickable(enabled = enabled) { onGenerate(name) },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (generating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    }
                    Text(
                        text = if (generating) "Growing your branch…" else "✧ Generate this branch",
                        fontFamily = FredokaFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

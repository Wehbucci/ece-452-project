package com.example.grasp.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathRegionPill

/**
 * A small centered "region" pill that groups the journey into tiers — e.g. FOUNDATIONS,
 * CORE ML · PICK A TRACK, MASTERY, YOUR BRANCHES.
 *
 * Dumb/stateless: the caller places it (the Screen anchors it above the region's first node).
 */
@Composable
fun RegionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = PathRegionPill,
        contentColor = PathMuted,
    ) {
        Text(
            text = text.uppercase(),
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

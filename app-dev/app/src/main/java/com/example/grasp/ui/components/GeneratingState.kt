package com.example.grasp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeCurrent

/**
 * Full-screen progress while a topic is generated.
 *
 * Names both things being built, because the wait covers the roadmap AND every lesson on it — a
 * bare spinner would read as the app having hung.
 */
@Composable
fun GeneratingState(topic: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = PathNodeCurrent, strokeWidth = 4.dp)
        Text(
            text = "Building your roadmap",
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = PathInk,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Breaking “$topic” into subtopics and writing every lesson. This takes a " +
                "moment, and then it's yours to keep, even offline.",
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = PathMuted,
            textAlign = TextAlign.Center,
        )
    }
}

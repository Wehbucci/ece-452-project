package com.example.grasp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.grasp.ui.theme.NodeCompleted

/**
 * Small, reusable building blocks shared across screens. Keeping them here avoids copy-paste
 * and keeps the per-screen Composables focused on layout. All are dumb/stateless.
 */

/**
 * A labelled linear progress bar — the standard way Grasp shows completion. 
 * Uses the green "growth" color to reinforce progress.
 * 
 * @param progress 0f..1f (values outside are clamped)
 * @param label leading caption, e.g. "3 of 8 complete"
 * @param trailing optional right-aligned caption, e.g. "~25 min left"
 */
@Composable
fun LabeledLinearProgress(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (trailing != null) {
                Text(
                    trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            color = NodeCompleted,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

/**
 * A small rounded status pill, e.g. node state ("Active"), a mode tag, or an estimate
 * ("5 min"). [accent] tints both the background (faint) and the text (full strength).
 */
@Composable
fun StatusPill(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.14f),
        contentColor = accent,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** A lightweight section header used to break long screens into scannable groups. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

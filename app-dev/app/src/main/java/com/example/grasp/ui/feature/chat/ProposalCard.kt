package com.example.grasp.ui.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.grasp.core.edit.EditProposal
import com.example.grasp.core.edit.ProposedChange
import com.example.grasp.data.model.ProposalOutcome

/**
 * The changes the tutor offered, and the only way they ever reach the lesson (FR5.4).
 *
 * Shown as before/after cards rather than as a sentence describing what the assistant would do,
 * because the two ask completely different things of the user. "I'll tighten that up for you" can
 * only be answered by trusting the assistant; the paragraph as it stands set against the paragraph
 * as it would be can be answered by reading. Nothing here applies on its own, and there is no
 * "always allow" — every batch is a fresh decision.
 *
 * @param outcome null while the user still has to decide; a decision replaces the buttons with
 *        what happened, so the transcript still reads correctly when scrolled back to.
 */
@Composable
internal fun ProposalCards(
    proposal: EditProposal,
    outcome: ProposalOutcome?,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (proposal.changes.isNotEmpty()) {
                Label(
                    if (proposal.changes.size == 1) "SUGGESTED CHANGE"
                    else "${proposal.changes.size} SUGGESTED CHANGES",
                )
                proposal.changes.forEach { ChangeRow(it) }
            }

            // What it wanted to do and wasn't allowed to. Never silently dropped: a tutor that
            // offers three fixes and delivers two without saying so is the thing this whole flow
            // exists to prevent.
            proposal.declined.forEach { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Nothing to decide on when every change was refused — the reasons above are the
            // whole message, and a pair of buttons under them would be asking about nothing.
            if (proposal.changes.isNotEmpty()) {
                when (outcome) {
                    null -> Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                            Text("No thanks")
                        }
                        Button(
                            // Anything that writes over the user's own words is asked twice.
                            onClick = {
                                if (proposal.overwritesUserWork) confirming = true else onAccept()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (proposal.changes.size == 1) "Make it" else "Make them")
                        }
                    }

                    else -> Text(
                        text = outcome.settledText(),
                        style = MaterialTheme.typography.labelLarge,
                        color = when (outcome) {
                            ProposalOutcome.ACCEPTED -> MaterialTheme.colorScheme.primary
                            ProposalOutcome.REJECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                            ProposalOutcome.FAILED -> MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }
    }

    if (confirming) {
        OverwriteConfirmation(
            changes = proposal.changes.filter { it.overwritesUserWork },
            onConfirm = { confirming = false; onAccept() },
            onDismiss = { confirming = false },
        )
    }
}

/** One change: what it is called, what is there now, and what would be there instead. */
@Composable
private fun ChangeRow(change: ProposedChange) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = change.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (change.overwritesUserWork) {
                Text(
                    text = "· YOUR WORDS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        // A removal shows only what goes, an addition only what arrives. Printing an empty "after"
        // box under a deletion says the paragraph becomes blank, which is not what happens.
        if (change.before.isNotBlank()) {
            DiffLine(
                text = change.before,
                tint = MaterialTheme.colorScheme.error,
                struckThrough = change.after.isBlank(),
            )
        }
        if (change.after.isNotBlank()) {
            DiffLine(text = change.after, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** One side of the comparison, marked by a coloured bar rather than by a +/− the user must decode. */
@Composable
private fun DiffLine(text: String, tint: Color, struckThrough: Boolean = false) {
    Row(
        // The bar has no height of its own, so the row is measured from the text and the bar then
        // fills it. Without this it collapses to nothing and the diff loses its only colour cue.
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(tint.copy(alpha = 0.6f)),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textDecoration = if (struckThrough) TextDecoration.LineThrough else null,
        )
    }
}

/**
 * The second ask, for a change that would replace something the user wrote themselves.
 *
 * Deliberately names what would be lost rather than asking "are you sure?": the answer to "are you
 * sure" is always yes, and the answer to "this paragraph you wrote will be replaced" sometimes
 * isn't. Undo exists either way, but a change you didn't notice is one you never undo.
 */
@Composable
private fun OverwriteConfirmation(
    changes: List<ProposedChange>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace your own writing?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (changes.size == 1) "This change replaces something you wrote yourself:"
                    else "These changes replace things you wrote yourself:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                changes.forEach { change ->
                    Text(
                        text = "• ${change.before.ifBlank { change.title }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "You can undo it afterwards from the lesson's edit mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Replace") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep mine") } },
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun ProposalOutcome.settledText(): String = when (this) {
    ProposalOutcome.ACCEPTED -> "✓ Done — the change is in"
    ProposalOutcome.REJECTED -> "Left as it was"
    ProposalOutcome.FAILED -> "⚠️ Couldn't make that change — the material has moved on since"
}

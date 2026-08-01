package com.example.grasp.ui.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.core.edit.EditProposal
import com.example.grasp.core.edit.ProposedChange
import com.example.grasp.data.model.ProposalOutcome
import com.example.grasp.ui.components.GameButton
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.GameCardBevel
import com.example.grasp.ui.theme.GameDanger
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathCard
import com.example.grasp.ui.theme.PathChipNeutralBg
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathNodeCurrentBevel
import com.example.grasp.ui.theme.PathNodeDone

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

    // A white card on the app's hard bevel, matching the reply bubble it hangs under — the
    // proposal is part of that reply, so it is built from the same material rather than being
    // dropped in as a dialog-shaped foreign object.
    Box(modifier = modifier.fillMaxWidth().padding(bottom = CardBevel)) {
        Box(
            Modifier
                .matchParentSize()
                // OFFSET, not padding: the bevel has to escape the content bounds into the space
                // the wrapper reserved below. Padding shrinks it inwards instead, leaving it
                // covered by the card face and the bevel invisible.
                .offset(y = CardBevel)
                .background(GameCardBevel, CardShape),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(PathCard)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = PathMuted,
                )
            }

            // Nothing to decide on when every change was refused — the reasons above are the
            // whole message, and a pair of buttons under them would be asking about nothing.
            if (proposal.changes.isNotEmpty()) {
                when (outcome) {
                    null -> Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Declining is quiet and accepting is the accent slab: the offer is the
                        // tutor's, so saying no should cost nothing and take no persuading.
                        QuietButton(
                            label = "No thanks",
                            onClick = onReject,
                            modifier = Modifier.weight(1f),
                        )
                        GameButton(
                            label = if (proposal.changes.size == 1) "Make it" else "Make them",
                            // Anything that writes over the user's own words is asked twice.
                            onClick = {
                                if (proposal.overwritesUserWork) confirming = true else onAccept()
                            },
                            height = 46.dp,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    else -> Text(
                        text = outcome.settledText(),
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = when (outcome) {
                            ProposalOutcome.ACCEPTED -> PathNodeDone
                            ProposalOutcome.REJECTED -> PathMuted
                            ProposalOutcome.FAILED -> GameDanger
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = change.title,
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                color = PathInk,
            )
            if (change.overwritesUserWork) {
                Text(
                    text = "· YOUR WORDS",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp,
                    color = GameDanger,
                )
            }
        }

        // A removal shows only what goes, an addition only what arrives. Printing an empty "after"
        // box under a deletion says the paragraph becomes blank, which is not what happens.
        if (change.before.isNotBlank()) {
            DiffLine(
                text = change.before,
                tint = GameDanger,
                struckThrough = change.after.isBlank(),
            )
        }
        if (change.after.isNotBlank()) {
            DiffLine(text = change.after, tint = PathNodeDone)
        }
    }
}

/** One side of the comparison, marked by a coloured bar rather than by a +/− the user must decode. */
@Composable
private fun DiffLine(text: String, tint: Color, struckThrough: Boolean = false) {
    Row(
        // The bar has no height of its own, so the row is measured from the text and the bar then
        // fills it. Without this it collapses to nothing and the diff loses its only colour cue.
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .background(PathChipNeutralBg),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(tint),
        )
        Text(
            text = text,
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = PathInk,
            textDecoration = if (struckThrough) TextDecoration.LineThrough else null,
            modifier = Modifier.padding(top = 9.dp, bottom = 9.dp, end = 10.dp),
        )
    }
}

/**
 * The secondary half of the accept/decline pair.
 *
 * Flat and unbevelled on purpose: the bevel in this design system means "this is the thing to
 * press", and declining an offer nobody asked for should not be competing for that.
 */
@Composable
private fun QuietButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            // Reserves the same strip [GameButton] leaves for its bevel, so the pair line up
            // instead of this one floating a few dp above its neighbour.
            .padding(bottom = ButtonBevelReserve)
            .height(46.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(PathChipNeutralBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = PathMuted,
            textAlign = TextAlign.Center,
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
        containerColor = PathCard,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Replace your own writing?",
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = PathInk,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (changes.size == 1) "This change replaces something you wrote yourself:"
                    else "These changes replace things you wrote yourself:",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = PathMuted,
                )
                changes.forEach { change ->
                    Text(
                        text = "• ${change.before.ifBlank { change.title }}",
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = PathInk,
                    )
                }
                Text(
                    text = "You can undo it afterwards from the lesson's edit mode.",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = PathFaint,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Replace",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = GameDanger,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Keep mine",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = PathMuted,
                )
            }
        },
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Black,
        fontSize = 10.5.sp,
        letterSpacing = 1.sp,
        color = PathNodeCurrent,
    )
}

/** Matches the reply bubble it hangs under, one step larger because it is a card, not a bubble. */
private val CardShape = RoundedCornerShape(18.dp)
private val CardBevel = 4.dp

/** [GameButton]'s own bevel thickness, which the flat button beside it has to match. */
private val ButtonBevelReserve = 5.dp

private fun ProposalOutcome.settledText(): String = when (this) {
    ProposalOutcome.ACCEPTED -> "✓ Done — the change is in"
    ProposalOutcome.REJECTED -> "Left as it was"
    ProposalOutcome.FAILED -> "⚠️ Couldn't make that change — the material has moved on since"
}

package com.example.grasp.ui.feature.path

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeBranch
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathStreak

/**
 * Reshaping the roadmap, done on the roadmap.
 *
 * One control — "Edit roadmap" — opens a short menu of the three things that change the SHAPE of
 * the tree, and each of them turns the board itself into the picker. That is the whole design:
 * adding, moving and deleting a section are all "which one?" questions, and the board is the only
 * place where the answer is visible. Naming a section from a dropdown inside some other section's
 * lesson asked the user to rearrange a tree they couldn't see.
 *
 * It also keeps the resting board clean, which is the proposal's Conflict 3 (UI simplicity, NFR
 * 2.2, over customization): nothing here is on screen until the user asks to edit.
 *
 * Stateless apart from the delete dialog's own checkbox — [PathContract.Presenter] owns the mode.
 */
@Composable
fun RoadmapEditBar(
    mode: BoardMode,
    movingTitle: String?,
    onEditRequested: () -> Unit,
    onAdd: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        // Full width so the banner has a bound to fit inside; everything in it still hugs the
        // right-hand edge where the button lives.
        modifier = modifier.fillMaxWidth().padding(12.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The menu grows out of the button rather than covering the board, so the roadmap the user
        // is about to change stays in sight while they choose what to do to it.
        AnimatedVisibility(
            visible = mode == BoardMode.MENU,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                shadowElevation = 8.dp,
            ) {
                Column(Modifier.padding(6.dp)) {
                    MenuItem(Icons.Filled.Add, "Add a section", PathNodeBranch, onAdd)
                    val move = Icons.AutoMirrored.Filled.ArrowForward
                    MenuItem(move, "Move a section", PathNodeCurrent, onMove)
                    MenuItem(Icons.Filled.Delete, "Delete a section", PathStreak, onDelete)
                }
            }
        }

        when (mode) {
            BoardMode.BROWSING, BoardMode.MENU -> EditRoadmapButton(
                open = mode == BoardMode.MENU,
                onClick = onEditRequested,
            )

            // Every picker looks the same on purpose: say what to tap, and always offer the way out
            // that isn't "pick a section you didn't mean".
            BoardMode.PICK_ADD_PARENT ->
                PickBanner("Tap a section to branch from", PathNodeBranch, onCancel)

            BoardMode.PICK_DELETE ->
                PickBanner("Tap the section to delete", PathStreak, onCancel)

            BoardMode.PICK_MOVE ->
                PickBanner("Tap the section to move", PathNodeCurrent, onCancel)

            BoardMode.PICK_MOVE_PARENT -> PickBanner(
                // Naming it matters here: this is the only step where the tap means something
                // different from the section under the finger.
                text = "Where should “${movingTitle.orEmpty()}” go?",
                tint = PathNodeCurrent,
                onCancel = onCancel,
            )
        }
    }
}

/** The one resting control: quiet, and the only way into any of this. */
@Composable
private fun EditRoadmapButton(open: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = if (open) PathInk else Color.White,
        contentColor = if (open) Color.White else PathInk,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = if (open) "Done" else "Edit roadmap",
                fontFamily = FredokaFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, tint: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Text(
                text = text,
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = PathInk,
            )
        }
    }
}

/** Replaces the button while the board is a picker: what to tap, and how to stop. */
@Composable
private fun PickBanner(text: String, tint: Color, onCancel: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = tint,
        contentColor = Color.White,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                fontFamily = FredokaFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Shrinks rather than pushing Cancel off the screen: a long section title in
                // "Where should “…” go?" is exactly the case that would.
                modifier = Modifier.weight(1f, fill = false),
            )
            Surface(
                onClick = onCancel,
                shape = RoundedCornerShape(percent = 50),
                color = Color.White.copy(alpha = 0.22f),
                contentColor = Color.White,
            ) {
                Text(
                    text = "Cancel",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** The section the board is asking about, held by the Screen between tap and answer. */
data class DeleteTarget(val nodeId: String, val title: String, val hasDescendants: Boolean)

/**
 * Deleting a section, with the one question that actually matters spelled out: whether what grew
 * beyond it goes too. The default keeps it — a user cutting one section rarely means "and the six
 * after it" — and for a section with nothing below it the question isn't asked at all.
 */
@Composable
fun DeleteSectionDialog(
    target: DeleteTarget,
    onDelete: (withDescendants: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var withDescendants by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete “${target.title}”?",
                fontFamily = FredokaFamily,
                fontWeight = FontWeight.SemiBold,
                color = PathInk,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (target.hasDescendants) {
                        "Its lesson goes with it. What comes after it moves up to take its place."
                    } else {
                        "Its lesson goes with it."
                    },
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = PathMuted,
                )
                if (target.hasDescendants) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Checkbox(
                            checked = withDescendants,
                            onCheckedChange = { withDescendants = it },
                        )
                        Text(
                            text = "Also delete everything that branches off it",
                            fontFamily = NunitoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = PathInk,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(withDescendants) }) {
                Text("Delete", color = PathStreak)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

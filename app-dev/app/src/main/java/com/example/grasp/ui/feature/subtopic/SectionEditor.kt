package com.example.grasp.ui.feature.subtopic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.core.edit.RoadmapEdit
import com.example.grasp.data.model.TreeNode
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathChipNeutralBg
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeBranch

/**
 * The roadmap facts about the open section that edit mode can change.
 *
 * Separate from [com.example.grasp.data.model.Subtopic] because these live on the roadmap NODE,
 * not on its lesson: renaming a section renames it on the board, and moving it re-shapes the tree.
 *
 * @param canDelete false for the root, which is the roadmap itself rather than a section of it.
 * @param possibleParents every other section this one could hang from, already filtered of the
 *        ones that would make a loop.
 */
data class SectionShape(
    val nodeId: String,
    val title: String,
    val estMinutes: Int,
    val tier: String?,
    val canDelete: Boolean,
    val possibleParents: List<TreeNode>,
)

/**
 * Editing the section itself rather than the words in it (FR4.5).
 *
 * Sits at the bottom of edit mode, below the lesson, because it is the rarer of the two jobs and
 * the more consequential: renaming a section is a small thing, but deleting one or moving it
 * changes the shape of the whole roadmap. Everything destructive here asks first.
 */
@Composable
fun SectionEditorPanel(
    section: SectionShape,
    onRoadmapEdit: (RoadmapEdit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<SectionAction?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "THIS SECTION ON THE ROADMAP",
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = PathFaint,
            modifier = Modifier.padding(top = 10.dp),
        )

        SectionField("Name", section.title) { dialog = SectionAction.Rename }
        SectionField("Time", "${section.estMinutes} min") { dialog = SectionAction.Retime }
        SectionField("Region", section.tier ?: "None") { dialog = SectionAction.Retier }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionButton("Add a section after this") { dialog = SectionAction.AddChild }
            if (section.possibleParents.isNotEmpty()) {
                SectionButton("Move under…") { dialog = SectionAction.Reparent }
            }
        }
        if (section.canDelete) {
            SectionButton("Delete this section", tint = PathNodeBranch) {
                dialog = SectionAction.Delete
            }
        }
    }

    when (dialog) {
        null -> Unit

        SectionAction.Rename -> TextPromptDialog(
            title = "Rename section",
            label = "Name",
            initial = section.title,
            onSave = { dialog = null; onRoadmapEdit(RoadmapEdit.RenameNode(section.nodeId, it)) },
            onDismiss = { dialog = null },
        )

        SectionAction.Retime -> TextPromptDialog(
            title = "Estimated time",
            label = "Minutes",
            initial = section.estMinutes.toString(),
            onSave = {
                dialog = null
                it.trim().toIntOrNull()?.let { minutes ->
                    onRoadmapEdit(RoadmapEdit.RetimeNode(section.nodeId, minutes))
                }
            },
            onDismiss = { dialog = null },
        )

        SectionAction.Retier -> TextPromptDialog(
            title = "Region",
            label = "Region label (empty for none)",
            initial = section.tier.orEmpty(),
            allowEmpty = true,
            onSave = { dialog = null; onRoadmapEdit(RoadmapEdit.RetierNode(section.nodeId, it)) },
            onDismiss = { dialog = null },
        )

        SectionAction.AddChild -> TextPromptDialog(
            title = "New section",
            label = "Name",
            initial = "",
            onSave = { name ->
                dialog = null
                onRoadmapEdit(
                    RoadmapEdit.AddNode(
                        parentId = section.nodeId,
                        // Its lesson is written the first time the section is opened, the same
                        // way one that failed up-front generation is.
                        node = TreeNode(id = newSectionId(name), title = name, contentReady = false),
                    ),
                )
            },
            onDismiss = { dialog = null },
        )

        SectionAction.Reparent -> PickParentDialog(
            candidates = section.possibleParents,
            onPick = {
                dialog = null
                onRoadmapEdit(RoadmapEdit.ReparentNode(section.nodeId, it.id))
            },
            onDismiss = { dialog = null },
        )

        SectionAction.Delete -> DeleteSectionDialog(
            title = section.title,
            onDelete = { withDescendants ->
                dialog = null
                onRoadmapEdit(RoadmapEdit.DeleteNode(section.nodeId, withDescendants))
            },
            onDismiss = { dialog = null },
        )
    }
}

private enum class SectionAction { Rename, Retime, Retier, AddChild, Reparent, Delete }

/**
 * An id for a hand-made section.
 *
 * Slug plus a random tail: the slug keeps a Firestore document readable when someone is looking at
 * the console, and the tail is what actually guarantees two sections named "Practice" don't
 * collide.
 */
private fun newSectionId(title: String): String {
    val slug = title.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(24)
    val tail = java.util.UUID.randomUUID().toString().take(6)
    return if (slug.isEmpty()) "section-$tail" else "$slug-$tail"
}

@Composable
private fun PickParentDialog(
    candidates: List<TreeNode>,
    onPick: (TreeNode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogHeading("Move under which section?") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Everything below this section moves with it.",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = PathMuted,
                )
                candidates.forEach { candidate ->
                    Surface(
                        onClick = { onPick(candidate) },
                        shape = RoundedCornerShape(12.dp),
                        color = PathChipNeutralBg,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = candidate.title,
                            fontFamily = NunitoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = PathInk,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Deleting a section, with the one question that actually matters spelled out: whether what grew
 * beyond it goes too. The default keeps it — a user cutting one section rarely means "and the six
 * after it".
 */
@Composable
private fun DeleteSectionDialog(
    title: String,
    onDelete: (withDescendants: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var withDescendants by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogHeading("Delete “$title”?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Its lesson goes with it. This can't be undone from the roadmap.",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = PathMuted,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Checkbox(checked = withDescendants, onCheckedChange = { withDescendants = it })
                    Text(
                        text = "Also delete everything that branches off it",
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = PathInk,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(withDescendants) }) {
                Text("Delete", color = PathNodeBranch)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TextPromptDialog(
    title: String,
    label: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    allowEmpty: Boolean = false,
) {
    var value by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogHeading(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label, fontFamily = NunitoFamily, fontSize = 12.sp) },
                textStyle = TextStyle(fontFamily = NunitoFamily, fontSize = 15.sp),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = allowEmpty || value.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SectionField(label: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = PathChipNeutralBg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                color = PathFaint,
            )
            Text(
                text = value,
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = PathInk,
            )
        }
    }
}

@Composable
private fun SectionButton(
    text: String,
    tint: androidx.compose.ui.graphics.Color = PathMuted,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = tint.copy(alpha = 0.12f),
        contentColor = tint,
    ) {
        Text(
            text = text,
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun DialogHeading(text: String) {
    Text(text = text, fontFamily = FredokaFamily, fontWeight = FontWeight.SemiBold, color = PathInk)
}

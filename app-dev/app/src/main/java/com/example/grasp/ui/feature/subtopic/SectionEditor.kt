package com.example.grasp.ui.feature.subtopic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathChipNeutralBg
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted

/**
 * The roadmap facts about the open section that edit mode can change.
 *
 * Separate from [com.example.grasp.data.model.Subtopic] because these live on the roadmap NODE,
 * not on its lesson: renaming a section renames it on the board too — they are the same name.
 *
 * Names and numbers only, on purpose. Adding a section, moving one and deleting one all change the
 * SHAPE of the roadmap, and those belong on the board where the shape is visible — see
 * `ui.feature.path.RoadmapEditBar`. Choosing a new parent from a list inside one lesson meant
 * rearranging the tree while looking at exactly one node of it.
 */
data class SectionShape(
    val nodeId: String,
    val title: String,
    val estMinutes: Int,
    val tier: String?,
)

/**
 * Editing the section itself rather than the words in it (FR4.5).
 *
 * Sits at the bottom of edit mode, below the lesson, because it is the rarer of the two jobs.
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

        Text(
            text = "Add, move and delete sections from the roadmap itself — “Edit roadmap” there.",
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = PathMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
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
    }
}

private enum class SectionAction { Rename, Retime, Retier }

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
private fun DialogHeading(text: String) {
    Text(text = text, fontFamily = FredokaFamily, fontWeight = FontWeight.SemiBold, color = PathInk)
}

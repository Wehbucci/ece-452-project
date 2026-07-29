package com.example.grasp.ui.feature.subtopic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.core.edit.LessonEdit
import com.example.grasp.core.edit.LessonField
import com.example.grasp.core.edit.RoadmapEdit
import com.example.grasp.data.model.BlockSource
import com.example.grasp.data.model.DiagramItem
import com.example.grasp.data.model.DiagramKind
import com.example.grasp.data.model.LessonBlock
import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.model.newBlockId
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathChipNeutralBg
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeBranch
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathNodeCurrentTint

/**
 * The lesson as an editable document (FR4.5).
 *
 * This is a SEPARATE view of the same lesson rather than controls sprinkled through the reading
 * one, which is the proposal's Conflict 3 resolved in favour of UI simplicity (NFR 2.2): a learner
 * who never wants to edit anything should never have to look at a delete button. You get here by
 * turning edit mode on, and everything about the surface says you are in it.
 *
 * Emits [LessonEdit]s and stores nothing. Every change — including one the assistant later
 * proposes — travels the same road out of here, so what a user can do by hand and what the AI can
 * offer to do stay the same set of things by construction.
 */
@Composable
fun LessonEditorBody(
    subtopic: Subtopic,
    onEdit: (LessonEdit) -> Unit,
    modifier: Modifier = Modifier,
    section: SectionShape? = null,
    onRoadmapEdit: (RoadmapEdit) -> Unit = {},
) {
    var dialog by remember { mutableStateOf<EditorAction?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EditorNote()

        EditableField(
            label = "SUMMARY",
            value = subtopic.summary,
            onClick = { dialog = EditorAction.EditField(LessonField.SUMMARY, subtopic.summary) },
        )
        EditableField(
            label = "WHY IT MATTERS",
            value = subtopic.whyItMatters,
            onClick = {
                dialog = EditorAction.EditField(LessonField.WHY_IT_MATTERS, subtopic.whyItMatters)
            },
        )

        SectionCaps("THE LESSON")

        // A slot above the first block, so a lesson can be given a new opening — and so an empty
        // one is not a dead end.
        InsertSlot { dialog = EditorAction.PickType(afterBlockId = null) }

        subtopic.body.forEachIndexed { index, block ->
            EditableBlockCard(
                block = block,
                canMoveUp = index > 0,
                canMoveDown = index < subtopic.body.lastIndex,
                onEditBlock = { dialog = EditorAction.EditBlock(block) },
                onReplacePicture = { dialog = EditorAction.ReplacePicture(block.id, block.text) },
                onDelete = { onEdit(LessonEdit.DeleteBlock(block.id)) },
                // Moving up means landing after whatever the block above it followed — null when
                // that is the top of the lesson.
                onMoveUp = {
                    onEdit(LessonEdit.MoveBlock(block.id, subtopic.body.getOrNull(index - 2)?.id))
                },
                onMoveDown = {
                    onEdit(LessonEdit.MoveBlock(block.id, subtopic.body[index + 1].id))
                },
            )
            InsertSlot { dialog = EditorAction.PickType(afterBlockId = block.id) }
        }

        // Last, and below the lesson: changing the roadmap's shape is the rarer job and the one
        // with consequences beyond this screen.
        section?.let { SectionEditorPanel(section = it, onRoadmapEdit = onRoadmapEdit) }
    }

    when (val open = dialog) {
        null -> Unit

        is EditorAction.EditField -> FieldEditorDialog(
            field = open.field,
            initial = open.value,
            onSave = { dialog = null; onEdit(LessonEdit.UpdateField(open.field, it)) },
            onDismiss = { dialog = null },
        )

        is EditorAction.EditBlock -> BlockEditorDialog(
            block = open.block,
            onSave = { dialog = null; onEdit(LessonEdit.UpdateBlock(open.block.id, it)) },
            onDismiss = { dialog = null },
        )

        is EditorAction.PickType -> BlockTypePickerDialog(
            onPick = { kind ->
                dialog = when (kind) {
                    // A picture is chosen, not written, so it skips straight to the catalogue.
                    NewBlockKind.IMAGE -> EditorAction.PickPicture(open.afterBlockId)
                    else -> EditorAction.CreateBlock(open.afterBlockId, kind.emptyBlock())
                }
            },
            onDismiss = { dialog = null },
        )

        is EditorAction.CreateBlock -> BlockEditorDialog(
            block = open.template,
            // Written and inserted in one step: cancelling leaves no half-made block behind.
            onSave = { dialog = null; onEdit(LessonEdit.InsertBlockAfter(open.afterBlockId, it)) },
            onDismiss = { dialog = null },
        )

        is EditorAction.PickPicture -> ImagePickerDialog(
            initialQuery = "",
            onPick = { picked ->
                dialog = null
                onEdit(LessonEdit.InsertBlockAfter(open.afterBlockId, picked.copy(id = newBlockId())))
            },
            onDismiss = { dialog = null },
        )

        is EditorAction.ReplacePicture -> ImagePickerDialog(
            initialQuery = open.caption,
            onPick = { picked ->
                dialog = null
                onEdit(
                    LessonEdit.ReplaceImage(
                        blockId = open.blockId,
                        url = picked.url,
                        sourceUrl = picked.sourceUrl,
                        credit = picked.credit,
                    ),
                )
            },
            onDismiss = { dialog = null },
        )
    }
}

/** What the editor currently has open. One at a time — these are all modal. */
private sealed interface EditorAction {
    data class EditField(val field: LessonField, val value: String) : EditorAction
    data class EditBlock(val block: LessonBlock) : EditorAction
    data class PickType(val afterBlockId: String?) : EditorAction
    data class CreateBlock(val afterBlockId: String?, val template: LessonBlock) : EditorAction
    data class PickPicture(val afterBlockId: String?) : EditorAction
    data class ReplacePicture(val blockId: String, val caption: String) : EditorAction
}

/** The kinds a user can add. Matches the block vocabulary the renderer knows how to draw. */
private enum class NewBlockKind(val label: String, val hint: String) {
    HEADING("Heading", "Names the idea a section teaches"),
    PARAGRAPH("Paragraph", "A tappable unit of the lesson"),
    CODE("Code", "Keeps its own line breaks and spacing"),
    DIAGRAM("Diagram", "Drawn by the app from labels you give it"),
    IMAGE("Picture", "Searched on Wikimedia Commons, credit included"),
}

/** A starter block of the chosen kind, ready to be filled in. */
private fun NewBlockKind.emptyBlock(): LessonBlock = when (this) {
    NewBlockKind.HEADING -> LessonBlock.Heading("", source = BlockSource.USER)
    NewBlockKind.PARAGRAPH -> LessonBlock.Paragraph("", source = BlockSource.USER)
    NewBlockKind.CODE -> LessonBlock.Code("", source = BlockSource.USER)
    // Two blank items, because a diagram needs two to be one and an empty list gives the editor
    // nothing to show.
    NewBlockKind.DIAGRAM -> LessonBlock.Diagram(
        text = "",
        kind = DiagramKind.FLOW,
        items = listOf(DiagramItem(""), DiagramItem("")),
        source = BlockSource.USER,
    )
    NewBlockKind.IMAGE -> LessonBlock.Image("", "", "", "", source = BlockSource.USER)
}

@Composable
private fun BlockTypePickerDialog(onPick: (NewBlockKind) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add a block", fontFamily = FredokaFamily, fontWeight = FontWeight.SemiBold, color = PathInk)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NewBlockKind.entries.forEach { kind ->
                    Surface(
                        onClick = { onPick(kind) },
                        shape = RoundedCornerShape(12.dp),
                        color = PathChipNeutralBg,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = kind.label,
                                fontFamily = NunitoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = PathInk,
                            )
                            Text(
                                text = kind.hint,
                                fontFamily = NunitoFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = PathMuted,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One block with everything you can do to it. */
@Composable
private fun EditableBlockCard(
    block: LessonBlock,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEditBlock: () -> Unit,
    onReplacePicture: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = PathChipNeutralBg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                KindTag(block.kindLabel())
                // Whose words these are. It is the difference between tidying the AI's draft and
                // overwriting something you wrote yourself.
                if (block.source != BlockSource.AI) KindTag(block.source.shortLabel(), tint = PathNodeBranch)
            }

            Text(
                text = block.previewText().ifBlank { "(empty)" },
                fontFamily = if (block is LessonBlock.Code) FontFamily.Monospace else NunitoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (block is LessonBlock.Code) 13.sp else 15.sp,
                lineHeight = 21.sp,
                color = if (block.previewText().isBlank()) PathFaint else PathInk,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                BlockAction("Edit", onEditBlock)
                if (block is LessonBlock.Image) BlockAction("Picture", onReplacePicture)
                Box(Modifier.weight(1f))
                if (canMoveUp) BlockAction("↑", onMoveUp)
                if (canMoveDown) BlockAction("↓", onMoveDown)
                BlockAction("Delete", onDelete, tint = PathNodeBranch)
            }
        }
    }
}

/** The lesson's own text fields, edited the same way its blocks are. */
@Composable
private fun EditableField(label: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = PathChipNeutralBg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KindTag(label)
            Text(
                text = value.ifBlank { "(empty)" },
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = if (value.isBlank()) PathFaint else PathInk,
            )
        }
    }
}

@Composable
private fun FieldEditorDialog(
    field: LessonField,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Reuses the paragraph editor: both are a block of prose, and giving them two different text
    // boxes would only make them behave differently for no reason.
    BlockEditorDialog(
        block = LessonBlock.Paragraph(initial, id = field.name),
        onSave = { onSave(it.text) },
        onDismiss = onDismiss,
    )
}

/** The gap between two blocks, where a new one goes. */
@Composable
private fun InsertSlot(onClick: () -> Unit) {
    Text(
        text = "＋ add a block here",
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = PathNodeCurrent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
}

@Composable
private fun EditorNote() {
    Surface(shape = RoundedCornerShape(12.dp), color = PathNodeCurrentTint, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "You're editing this lesson. Changes save as you make them, and “Undo” takes " +
                "back the last one.",
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = PathNodeCurrent,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun SectionCaps(text: String) {
    Text(
        text = text,
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        color = PathFaint,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun KindTag(text: String, tint: androidx.compose.ui.graphics.Color = PathMuted) {
    Text(
        text = text,
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Black,
        fontSize = 10.sp,
        letterSpacing = 0.8.sp,
        color = tint,
    )
}

@Composable
private fun BlockAction(
    text: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = PathNodeCurrent,
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

private fun LessonBlock.kindLabel(): String = when (this) {
    is LessonBlock.Heading -> if (level <= 1) "HEADING" else "SUBHEADING"
    is LessonBlock.Paragraph -> "PARAGRAPH"
    is LessonBlock.Code -> "CODE${if (language.isBlank()) "" else " · ${language.uppercase()}"}"
    is LessonBlock.Diagram -> "DIAGRAM · ${kind.name}"
    is LessonBlock.Image -> "PICTURE"
}

/** What the card shows. Visuals aren't re-drawn here — their caption is what's editable. */
private fun LessonBlock.previewText(): String = when (this) {
    is LessonBlock.Diagram -> (text.ifBlank { "Untitled diagram" }) +
        "\n" + items.joinToString(" → ") { it.label.ifBlank { "…" } }
    else -> text
}

private fun BlockSource.shortLabel(): String = when (this) {
    BlockSource.AI -> ""
    BlockSource.USER -> "· YOURS"
    BlockSource.AI_EDITED -> "· AI EDITED"
}

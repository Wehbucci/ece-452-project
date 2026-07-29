package com.example.grasp.ui.feature.subtopic

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.data.model.DiagramItem
import com.example.grasp.data.model.DiagramKind
import com.example.grasp.data.model.LessonBlock
import com.example.grasp.data.repository.searchLessonImages
import com.example.grasp.ui.components.rememberRemoteImage
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathChipNeutralBg
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathNodeCurrentTint
import kotlinx.coroutines.launch

/**
 * The editor for one block, picked to match what the block actually is (FR4.5).
 *
 * One dialog per kind rather than a single text box for all of them, because the kinds are not
 * interchangeable: prose wants a roomy wrapped field, code must not have its indentation tidied
 * away, a diagram is a structure rather than a string, and a picture is chosen from a catalogue,
 * never typed as a URL.
 *
 * [onSave] hands back the edited block. The caller turns it into a
 * [com.example.grasp.core.edit.LessonEdit] — nothing here writes anything.
 */
@Composable
fun BlockEditorDialog(
    block: LessonBlock,
    onSave: (LessonBlock) -> Unit,
    onDismiss: () -> Unit,
) {
    when (block) {
        is LessonBlock.Heading -> HeadingEditor(block, onSave, onDismiss)
        is LessonBlock.Paragraph -> ParagraphEditor(block, onSave, onDismiss)
        is LessonBlock.Code -> CodeEditor(block, onSave, onDismiss)
        is LessonBlock.Diagram -> DiagramEditor(block, onSave, onDismiss)
        is LessonBlock.Image -> ImageCaptionEditor(block, onSave, onDismiss)
    }
}

@Composable
private fun HeadingEditor(
    heading: LessonBlock.Heading,
    onSave: (LessonBlock) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(heading.text) }
    var level by remember { mutableStateOf(heading.level) }

    EditorDialog(
        title = "Edit heading",
        onDismiss = onDismiss,
        saveEnabled = text.isNotBlank(),
        onSave = { onSave(heading.copy(text = text.trim(), level = level)) },
    ) {
        EditorField(value = text, onValueChange = { text = it }, label = "Heading")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Section", selected = level <= 1) { level = 1 }
            ChoiceChip("Subheading", selected = level >= 2) { level = 2 }
        }
    }
}

@Composable
private fun ParagraphEditor(
    paragraph: LessonBlock.Paragraph,
    onSave: (LessonBlock) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(paragraph.text) }

    EditorDialog(
        title = "Edit paragraph",
        onDismiss = onDismiss,
        saveEnabled = text.isNotBlank(),
        onSave = { onSave(paragraph.copy(text = text.trim())) },
    ) {
        EditorField(
            value = text,
            onValueChange = { text = it },
            label = "Paragraph",
            minLines = 6,
        )
    }
}

@Composable
private fun CodeEditor(
    code: LessonBlock.Code,
    onSave: (LessonBlock) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(code.text) }
    var language by remember { mutableStateOf(code.language) }

    EditorDialog(
        title = "Edit code",
        onDismiss = onDismiss,
        saveEnabled = text.isNotBlank(),
        // Only the blank lines top and bottom go. For a language like Python the indentation IS
        // the syntax, so nothing here may tidy the inside of the sample.
        onSave = { onSave(code.copy(text = text.trim('\n', '\r').trimEnd(), language = language.trim())) },
    ) {
        EditorField(
            value = text,
            onValueChange = { text = it },
            label = "Code",
            minLines = 6,
            monospace = true,
        )
        EditorField(value = language, onValueChange = { language = it }, label = "Language")
    }
}

/**
 * The caption and nothing else — swapping the PICTURE goes through [ImagePickerDialog], so a
 * file's url, its source page and its credit can never be edited apart and end up describing
 * three different images.
 */
@Composable
private fun ImageCaptionEditor(
    image: LessonBlock.Image,
    onSave: (LessonBlock) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(image.text) }

    EditorDialog(
        title = "Edit caption",
        onDismiss = onDismiss,
        saveEnabled = true,
        onSave = { onSave(image.copy(text = text.trim())) },
    ) {
        EditorField(value = text, onValueChange = { text = it }, label = "Caption", minLines = 2)
        Text(
            text = image.credit.ifBlank { "Wikimedia Commons" },
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = PathMuted,
        )
    }
}

/**
 * A diagram is edited as the structure it is — a kind and a list of items — because that is what
 * the app draws from. There is no text form of it to type.
 */
@Composable
private fun DiagramEditor(
    diagram: LessonBlock.Diagram,
    onSave: (LessonBlock) -> Unit,
    onDismiss: () -> Unit,
) {
    var caption by remember { mutableStateOf(diagram.text) }
    var kind by remember { mutableStateOf(diagram.kind) }
    val items = remember { mutableStateListOf(*diagram.items.toTypedArray()) }

    EditorDialog(
        title = "Edit diagram",
        onDismiss = onDismiss,
        // Under two items there is nothing to draw — the same bar the generator is held to.
        saveEnabled = items.count { it.label.isNotBlank() } >= 2,
        onSave = {
            onSave(
                diagram.copy(
                    text = caption.trim(),
                    kind = kind,
                    items = items.filter { it.label.isNotBlank() },
                ),
            )
        },
    ) {
        EditorField(value = caption, onValueChange = { caption = it }, label = "Caption")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiagramKind.entries.forEach { option ->
                ChoiceChip(option.name.lowercase(), selected = kind == option) { kind = option }
            }
        }
        items.forEachIndexed { index, item ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EditorField(
                    value = item.label,
                    onValueChange = { items[index] = item.copy(label = it) },
                    label = "Item ${index + 1}",
                )
                EditorField(
                    value = item.detail,
                    onValueChange = { items[index] = item.copy(detail = it) },
                    label = "Detail (optional)",
                )
                // Only bars have magnitudes; asking for one on a flow would be a field with no
                // meaning and no effect.
                if (kind == DiagramKind.BAR) {
                    EditorField(
                        value = if (item.value == 0f) "" else item.value.toString(),
                        onValueChange = {
                            items[index] = item.copy(value = it.toFloatOrNull() ?: 0f)
                        },
                        label = "Value",
                    )
                }
                TextButton(onClick = { items.removeAt(index) }) { Text("Remove item ${index + 1}") }
            }
        }
        if (items.size < MAX_DIAGRAM_ITEMS) {
            TextButton(onClick = { items.add(DiagramItem("")) }) { Text("＋ Add item") }
        }
    }
}

/**
 * Picks a picture by searching Wikimedia Commons (FR4.4).
 *
 * There is deliberately no "paste a URL" field. Every image in a lesson has to arrive with the
 * source page and credit its licence requires, and those only exist for a file we looked up — a
 * typed URL gives a picture with nothing attached to it, which is exactly the case the licence
 * does not allow.
 */
@Composable
fun ImagePickerDialog(
    initialQuery: String,
    onPick: (LessonBlock.Image) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<LessonBlock.Image>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun search() {
        if (query.isBlank()) return
        searching = true
        scope.launch {
            results = searchLessonImages(query)
            searching = false
            searched = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle("Find a picture") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EditorField(
                    value = query,
                    onValueChange = { query = it },
                    label = "Search Wikimedia Commons",
                )
                TextButton(onClick = ::search, enabled = query.isNotBlank() && !searching) {
                    Text(if (searching) "Searching…" else "Search")
                }
                when {
                    searching -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = PathNodeCurrent,
                    )

                    searched && results.isEmpty() -> Text(
                        text = "Nothing found. Try plainer words for something photographable.",
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = PathMuted,
                    )

                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(results, key = { it.url }) { candidate ->
                            ImageCandidate(candidate) { onPick(candidate) }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ImageCandidate(image: LessonBlock.Image, onPick: () -> Unit) {
    val bitmap = rememberRemoteImage(image.url)

    Surface(
        onClick = onPick,
        shape = RoundedCornerShape(12.dp),
        color = PathChipNeutralBg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PathChipNeutralBg),
                contentAlignment = Alignment.Center,
            ) {
                bitmap?.let {
                    Image(
                        bitmap = it,
                        contentDescription = image.text,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = image.text,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = PathInk,
                )
                // Shown before the choice, not after: the credit travels with the picture and the
                // user is agreeing to carry it.
                Text(
                    text = image.credit,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = PathMuted,
                )
            }
        }
    }
}

/** The shared frame every block editor sits in, so they can't drift apart in look or behaviour. */
@Composable
private fun EditorDialog(
    title: String,
    onDismiss: () -> Unit,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = saveEnabled) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DialogTitle(text: String) {
    Text(text = text, fontFamily = FredokaFamily, fontWeight = FontWeight.SemiBold, color = PathInk)
}

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    minLines: Int = 1,
    monospace: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontFamily = NunitoFamily, fontSize = 12.sp) },
        minLines = minLines,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = if (monospace) FontFamily.Monospace else NunitoFamily,
            fontSize = if (monospace) 13.sp else 15.sp,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A small pick-one chip, for the handful of fixed choices a block kind offers. */
@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = if (selected) PathNodeCurrentTint else PathChipNeutralBg,
        contentColor = if (selected) PathNodeCurrent else PathFaint,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Matches the cap the generator writes to, so a hand-built diagram can't outgrow the renderer. */
private const val MAX_DIAGRAM_ITEMS = 5

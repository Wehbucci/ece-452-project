package com.example.grasp.core.edit

import com.example.grasp.data.model.BlockSource
import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.LessonBlock
import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.model.TreeNode
import com.example.grasp.data.model.newBlockId

/**
 * A change the tutor asked to make, exactly as it came off the model (FR5.4).
 *
 * Arguments are strings whatever type was declared, because that is the shape every backend agrees
 * on and this side has to re-check them anyway: a number arriving as `"twenty"` is the same class
 * of problem as one arriving as `20.0`, and both are the model's mistake to catch here rather than
 * a crash somewhere later.
 *
 * @property id the tool call this came from, so the model can be told what became of it.
 */
data class ToolCall(val id: String, val name: String, val args: Map<String, String>)

/**
 * The names the tutor calls its tools by.
 *
 * Named here, in the pure half, and referenced by the Gemini declarations in
 * `data/repository/TutorTools.kt` — so a tool the model can call and a tool this file knows how to
 * read cannot drift apart without the compiler saying so.
 *
 * Conspicuously absent, and it must stay that way: anything that marks a subtopic or a step
 * complete. The proposal commits to the user being the only one who says they have learnt
 * something, with the tutor staying purely assistive — so progress is not merely gated behind
 * approval here, it is not offerable at all.
 */
object TutorTool {
    const val REWRITE_BLOCK = "rewrite_block"
    const val ADD_BLOCK = "add_block"
    const val DELETE_BLOCK = "delete_block"
    const val MOVE_BLOCK = "move_block"
    const val REWRITE_LESSON_FIELD = "rewrite_lesson_field"

    const val RENAME_SECTION = "rename_section"
    const val RETIME_SECTION = "retime_section"
    const val ADD_SECTION = "add_section"
    const val DELETE_SECTION = "delete_section"
    const val MOVE_SECTION = "move_section"
}

/** A [LessonEdit] or a [RoadmapEdit] — the two things a proposal can be made of. */
sealed interface EditTarget {
    data class Lesson(val edit: LessonEdit) : EditTarget
    data class Roadmap(val edit: RoadmapEdit) : EditTarget
}

/**
 * One proposed change, carrying both the edit and the way to show it.
 *
 * [before] and [after] are for the user's eyes only: the card sets them against each other so that
 * accepting is a judgement about the content rather than about how much the assistant is trusted.
 * For a change that moves something rather than rewriting it they describe positions instead of
 * words, since "what will this look like afterwards" is the question either way.
 *
 * @property overwritesUserWork the change replaces or removes words the USER wrote. It is still
 *           offered — refusing to touch a paragraph the user just asked to have fixed would be
 *           absurd — but it is asked about twice.
 */
data class ProposedChange(
    val id: String,
    val title: String,
    val before: String,
    val after: String,
    val target: EditTarget,
    val overwritesUserWork: Boolean = false,
)

/**
 * Everything the tutor proposed in one turn, and everything it wasn't allowed to.
 *
 * Batched because a set of related changes is how an assistant actually answers "tighten this up",
 * and taking them one at a time would leave the lesson half-rewritten between taps — the same
 * reason [applyEdits] is all-or-nothing.
 *
 * @property declined one line per refused change, in the user's language. Shown rather than
 *           swallowed: a tutor that offers to fix three things and quietly fixes two is worse than
 *           one that fixes two and says which.
 */
data class EditProposal(
    val changes: List<ProposedChange>,
    val declined: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = changes.isEmpty() && declined.isEmpty()

    /** Whether accepting this would write over something the user wrote themselves. */
    val overwritesUserWork: Boolean get() = changes.any { it.overwritesUserWork }

    /** The lesson edits, in the order they were proposed and must be applied. */
    fun lessonEdits(): List<LessonEdit> =
        changes.mapNotNull { (it.target as? EditTarget.Lesson)?.edit }

    /** The roadmap edits, likewise. */
    fun roadmapEdits(): List<RoadmapEdit> =
        changes.mapNotNull { (it.target as? EditTarget.Roadmap)?.edit }
}

/**
 * How many changes one proposal may carry.
 *
 * A hard cap rather than a warning, because review is the whole safeguard and a review nobody
 * reads is not one: a model that decides to rewrite every paragraph in a lesson produces a wall of
 * diff cards that gets accepted wholesale. Anything past this is declined out loud, so the user
 * can ask for the rest once they have seen what the first batch did.
 */
const val MAX_CHANGES_PER_PROPOSAL = 5

/**
 * The longest text the tutor may write into one block.
 *
 * Generously above anything a lesson block legitimately holds. It is here for the failure mode
 * where a model loops and emits the same paragraph a hundred times over — not a lesson the user
 * should be offered, whatever they make of its first sentence.
 */
private const val MAX_BLOCK_TEXT = 4000

/**
 * What [calls] would do to [lesson], as changes the user can look at and decide on.
 *
 * Each call is read against the lesson AS THE EARLIER CALLS IN THE BATCH WOULD LEAVE IT, and then
 * actually applied to that running copy. So a batch that survives this is guaranteed to apply as a
 * whole when accepted: the user is never shown a card that then fails, and never has to discover
 * that "rewrite this and move it" half-worked.
 */
fun proposeLessonEdits(lesson: Subtopic, calls: List<ToolCall>): EditProposal {
    val changes = mutableListOf<ProposedChange>()
    val declined = mutableListOf<String>()
    var running = lesson

    for (call in calls) {
        if (changes.size >= MAX_CHANGES_PER_PROPOSAL) {
            declined += tooManyChanges(calls.size)
            break
        }
        val change = lessonChange(running, call)
        if (change == null) {
            declined += declineReason(call)
            continue
        }
        val edit = (change.target as EditTarget.Lesson).edit
        val applied = running.applyEdit(edit, EditAuthor.ASSISTANT)
        if (applied == null) {
            declined += declineReason(call)
            continue
        }
        changes += change
        running = applied
    }
    return EditProposal(changes, declined)
}

/** What [calls] would do to the shape of [path], on the same terms as [proposeLessonEdits]. */
fun proposeRoadmapEdits(path: LearningPath, calls: List<ToolCall>): EditProposal {
    val changes = mutableListOf<ProposedChange>()
    val declined = mutableListOf<String>()
    var running = path

    for (call in calls) {
        if (changes.size >= MAX_CHANGES_PER_PROPOSAL) {
            declined += tooManyChanges(calls.size)
            break
        }
        val change = roadmapChange(running, call)
        if (change == null) {
            declined += declineReason(call)
            continue
        }
        val edit = (change.target as EditTarget.Roadmap).edit
        val applied = running.applyEdit(edit)
        if (applied == null) {
            declined += declineReason(call)
            continue
        }
        changes += change
        running = applied
    }
    return EditProposal(changes, declined)
}

// ---- Lesson calls ------------------------------------------------------------------------------

/** One lesson tool call as a change, or null if it isn't one this lesson can be given. */
private fun lessonChange(lesson: Subtopic, call: ToolCall): ProposedChange? {
    return when (call.name) {

        TutorTool.REWRITE_BLOCK -> {
            val block = lesson.block(call["block_id"]) ?: return null
            val text = call.prose("text") ?: return null
            ProposedChange(
                id = call.id,
                title = "Rewrite ${block.kindPhrase()}",
                before = block.preview(),
                after = text,
                // The kind is kept rather than re-chosen: the model is rewriting the words of a part
                // of the lesson the user can see, and turning their code sample into prose while
                // "fixing a typo" is not a change they agreed to.
                target = EditTarget.Lesson(LessonEdit.UpdateBlock(block.id, block.withText(text))),
                overwritesUserWork = block.source == BlockSource.USER,
            )
        }

        TutorTool.ADD_BLOCK -> {
            val text = call.prose("text") ?: return null
            val block = newBlock(call["kind"], text, call["language"]) ?: return null
            val after = call.optionalId("after_block_id")
            // A named neighbour that isn't there is a mistake worth refusing, but no neighbour at all
            // legitimately means "at the top".
            if (after != null && lesson.block(after) == null) return null
            ProposedChange(
                id = call.id,
                title = "Add ${block.kindPhrase()}",
                before = "",
                after = text,
                target = EditTarget.Lesson(LessonEdit.InsertBlockAfter(after, block)),
            )
        }

        TutorTool.DELETE_BLOCK -> {
            val block = lesson.block(call["block_id"]) ?: return null
            ProposedChange(
                id = call.id,
                title = "Delete ${block.kindPhrase()}",
                before = block.preview(),
                after = "",
                target = EditTarget.Lesson(LessonEdit.DeleteBlock(block.id)),
                overwritesUserWork = block.source == BlockSource.USER,
            )
        }

        TutorTool.MOVE_BLOCK -> {
            val block = lesson.block(call["block_id"]) ?: return null
            val after = call.optionalId("after_block_id")
            if (after != null && lesson.block(after) == null) return null
            if (after == block.id) return null
            ProposedChange(
                id = call.id,
                title = "Move ${block.kindPhrase()}",
                // Positions, not words: nothing about the block itself is changing, so showing its
                // text twice would say the change does nothing.
                before = lesson.positionOf(block),
                after = lesson.positionAfter(after),
                target = EditTarget.Lesson(LessonEdit.MoveBlock(block.id, after)),
            )
        }

        TutorTool.REWRITE_LESSON_FIELD -> {
            val field = call.lessonField() ?: return null
            val text = call.prose("text") ?: return null
            ProposedChange(
                id = call.id,
                title = when (field) {
                    LessonField.SUMMARY -> "Rewrite the summary"
                    LessonField.WHY_IT_MATTERS -> "Rewrite why it matters"
                },
                before = when (field) {
                    LessonField.SUMMARY -> lesson.summary
                    LessonField.WHY_IT_MATTERS -> lesson.whyItMatters
                },
                after = text,
                target = EditTarget.Lesson(LessonEdit.UpdateField(field, text)),
            )
        }

        else -> null
    }
}

// ---- Roadmap calls -----------------------------------------------------------------------------

/** One roadmap tool call as a change, or null if it isn't one this roadmap can be given. */
private fun roadmapChange(path: LearningPath, call: ToolCall): ProposedChange? {
    return when (call.name) {

        TutorTool.RENAME_SECTION -> {
            val node = path.node(call["section_id"]) ?: return null
            val title = call.line("title") ?: return null
            ProposedChange(
                id = call.id,
                title = "Rename a section",
                before = node.title,
                after = title,
                target = EditTarget.Roadmap(RoadmapEdit.RenameNode(node.id, title)),
            )
        }

        TutorTool.RETIME_SECTION -> {
            val node = path.node(call["section_id"]) ?: return null
            val minutes = call.minutes("minutes") ?: return null
            ProposedChange(
                id = call.id,
                title = "Change how long a section takes",
                before = "${node.title} · ${node.estMinutes} min",
                after = "${node.title} · $minutes min",
                target = EditTarget.Roadmap(RoadmapEdit.RetimeNode(node.id, minutes)),
            )
        }

        TutorTool.ADD_SECTION -> {
            val parent = path.node(call["parent_id"]) ?: return null
            val title = call.line("title") ?: return null
            val minutes = call.minutes("minutes") ?: DEFAULT_SECTION_MINUTES
            ProposedChange(
                id = call.id,
                title = "Add a section",
                before = "",
                // The lesson itself isn't written yet — it is generated the first time the section is
                // opened, exactly as a branched-out one is — so the card promises a section, not text.
                after = "$title · $minutes min, after \"${parent.title}\"",
                target = EditTarget.Roadmap(
                    RoadmapEdit.AddNode(
                        parentId = parent.id,
                        node = TreeNode(
                            id = newSectionId(),
                            title = title,
                            estMinutes = minutes,
                            // Nothing has been generated for it, which is what makes the sheet say so
                            // rather than showing an empty lesson.
                            contentReady = false,
                            tier = parent.tier,
                        ),
                    ),
                ),
            )
        }

        TutorTool.DELETE_SECTION -> {
            val node = path.node(call["section_id"]) ?: return null
            // Only ever the section itself. Taking a whole branch out is a bigger thing than a card
            // can honestly describe, and the user has that on the board where they can see what goes.
            ProposedChange(
                id = call.id,
                title = "Delete a section",
                before = node.title +
                    if (node.children.isNotEmpty()) " (what follows it moves up)" else "",
                after = "",
                target = EditTarget.Roadmap(RoadmapEdit.DeleteNode(node.id, withDescendants = false)),
                // A section is the user's own structure whoever wrote its lesson, and deleting one
                // loses whatever they had done inside it.
                overwritesUserWork = true,
            )
        }

        TutorTool.MOVE_SECTION -> {
            val node = path.node(call["section_id"]) ?: return null
            val parent = path.node(call["new_parent_id"]) ?: return null
            if (node.id == parent.id) return null
            val wasUnder = node.parentId?.let { path.node(it)?.title }
            ProposedChange(
                id = call.id,
                title = "Move a section",
                before = wasUnder?.let { "\"${node.title}\" after \"$it\"" } ?: "\"${node.title}\"",
                after = "\"${node.title}\" after \"${parent.title}\"",
                target = EditTarget.Roadmap(RoadmapEdit.ReparentNode(node.id, parent.id)),
            )
        }

        else -> null
    }
}

/** What a section the tutor adds is assumed to take, when it doesn't say. */
private const val DEFAULT_SECTION_MINUTES = 20

/**
 * An id for a section the tutor is adding.
 *
 * Random rather than derived from the title: a node id is a Firestore document id, and two
 * sections that happen to be called the same thing must not land on one document.
 */
private fun newSectionId(): String = "n" + newBlockId().drop(1)

// ---- Reading arguments -------------------------------------------------------------------------

private operator fun ToolCall.get(name: String): String = args[name]?.trim().orEmpty()

/** An optional reference to something else: absent, empty and blank all mean "not given". */
private fun ToolCall.optionalId(name: String): String? = args[name]?.trim()?.ifEmpty { null }

/** A block of prose, checked for the two ways model output goes wrong: nothing, and far too much. */
private fun ToolCall.prose(name: String): String? {
    // Only the ends are trimmed. Line breaks and indentation INSIDE survive, because a code
    // sample is written with this tool too and its spacing is its syntax.
    val text = args[name]?.trim() ?: return null
    if (text.isEmpty() || text.length > MAX_BLOCK_TEXT) return null
    return text
}

/** A one-line value — a title. Newlines collapse, because nothing renders them here. */
private fun ToolCall.line(name: String): String? {
    val text = args[name]?.replace(Regex("\\s+"), " ")?.trim() ?: return null
    return text.ifEmpty { null }
}

/** A count of minutes, or null if it isn't one. Absurd values are a refusal, not a clamp. */
private fun ToolCall.minutes(name: String): Int? {
    val raw = args[name]?.trim()?.ifEmpty { null } ?: return null
    // "45.0" is a perfectly ordinary way for a model to write 45.
    val minutes = raw.toIntOrNull() ?: raw.toDoubleOrNull()?.toInt() ?: return null
    return if (minutes in 1..MAX_SECTION_MINUTES) minutes else null
}

/** Long enough for a genuinely meaty section, short enough to catch "1440". */
private const val MAX_SECTION_MINUTES = 240

private fun ToolCall.lessonField(): LessonField? = when (this["field"].lowercase()) {
    "summary" -> LessonField.SUMMARY
    "why_it_matters", "whyitmatters" -> LessonField.WHY_IT_MATTERS
    else -> null
}

/**
 * A brand-new block of the kind the model named.
 *
 * Only the three written kinds. Diagrams and pictures are deliberately not offerable: a diagram
 * has to satisfy the generator's item rules, and a picture is SOURCED from Wikimedia with the
 * licence attribution that goes with it — a model inventing a url would produce a credit that
 * belongs to a different image.
 */
private fun newBlock(kind: String, text: String, language: String): LessonBlock? =
    when (kind.lowercase()) {
        "heading" -> LessonBlock.Heading(text, level = 2)
        "paragraph" -> LessonBlock.Paragraph(text)
        "code" -> LessonBlock.Code(text, language = language)
        else -> null
    }

// ---- Describing things -------------------------------------------------------------------------

private fun Subtopic.block(id: String): LessonBlock? =
    if (id.isEmpty()) null else body.firstOrNull { it.id == id }

private fun LearningPath.node(id: String): TreeNode? =
    if (id.isEmpty()) null else nodes.firstOrNull { it.id == id && !it.isBranchOut }

/** Where a block sits now, said the way the card asks about the place it will sit. */
private fun Subtopic.positionOf(block: LessonBlock): String {
    val index = body.indexOfFirst { it.id == block.id }
    val above = body.getOrNull(index - 1) ?: return "At the top of the lesson"
    return "After \"${above.preview().oneLine()}\""
}

private fun Subtopic.positionAfter(afterBlockId: String?): String {
    val above = afterBlockId?.let { block(it) } ?: return "At the top of the lesson"
    return "After \"${above.preview().oneLine()}\""
}

/** "a paragraph", "the heading" — what the card calls the thing being changed. */
private fun LessonBlock.kindPhrase(): String = when (this) {
    is LessonBlock.Heading -> "a heading"
    is LessonBlock.Paragraph -> "a paragraph"
    is LessonBlock.Code -> "a code sample"
    is LessonBlock.Diagram -> "a diagram"
    is LessonBlock.Image -> "a picture"
}

/** What the diff shows for the current state of a block. Visuals show their caption. */
private fun LessonBlock.preview(): String = when (this) {
    is LessonBlock.Diagram -> text.ifBlank { "Untitled diagram" }
    is LessonBlock.Image -> text.ifBlank { "Untitled picture" }
    else -> text
}

private fun String.oneLine(): String {
    val flat = replace(Regex("\\s+"), " ").trim()
    return if (flat.length <= 40) flat else flat.take(39).trimEnd() + "…"
}

/**
 * The same block carrying different words.
 *
 * A diagram's or a picture's text is its caption, so rewriting one of those rewrites the caption
 * and leaves the drawing alone — which is the only part of them the tutor is given to change.
 */
private fun LessonBlock.withText(text: String): LessonBlock = when (this) {
    is LessonBlock.Heading -> copy(text = text)
    is LessonBlock.Paragraph -> copy(text = text)
    is LessonBlock.Code -> copy(text = text)
    is LessonBlock.Diagram -> copy(text = text)
    is LessonBlock.Image -> copy(text = text)
}

/**
 * Why a change was refused, in the user's language rather than the machine's.
 *
 * Deliberately vague about the mechanism: "that part of the lesson isn't there any more" is
 * something a user can act on, and `UpdateBlock(blockId=b7f3…) returned null` is not.
 */
private fun declineReason(call: ToolCall): String = when (call.name) {
    TutorTool.REWRITE_BLOCK, TutorTool.DELETE_BLOCK, TutorTool.MOVE_BLOCK ->
        "It tried to change a part of the lesson that isn't there any more."
    TutorTool.ADD_BLOCK -> "It tried to add something the lesson couldn't take."
    TutorTool.REWRITE_LESSON_FIELD -> "It tried to rewrite a part of the lesson that isn't there."
    TutorTool.RENAME_SECTION, TutorTool.RETIME_SECTION, TutorTool.DELETE_SECTION,
    TutorTool.MOVE_SECTION,
    -> "It tried to change a section that isn't on the roadmap."
    TutorTool.ADD_SECTION -> "It tried to add a section the roadmap couldn't take."
    else -> "It asked for something it isn't allowed to do."
}

private fun tooManyChanges(asked: Int): String =
    "It wanted to make $asked changes at once — only the first " +
        "$MAX_CHANGES_PER_PROPOSAL are offered. Ask again for the rest."

package com.example.grasp.data.repository

import com.example.grasp.core.edit.TutorTool
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool

/**
 * Which changes the tutor is allowed to propose, decided by what the user is looking at.
 *
 * Scoped rather than global because a tool the model can see is a tool it will eventually reach
 * for: offering `delete_section` inside a conversation about one paragraph invites it to answer a
 * question about wording by restructuring the roadmap.
 */
enum class TutorToolset {
    /** No material in view, or material with no edit vocabulary — a Tinkerer guide. Words only. */
    NONE,

    /** One lesson: its blocks and its own fields. */
    LESSON,

    /** The roadmap's shape: sections, their names, times and order. */
    ROADMAP,
}

/**
 * The tutor's tools as Gemini function declarations (FR5.4).
 *
 * Calling one of these does NOT change anything. The model has no way to reach the repository —
 * a call comes back as a [com.example.grasp.core.edit.ToolCall], is turned into the very same
 * [com.example.grasp.core.edit.LessonEdit] the manual editor emits, and sits in front of the user
 * as a before/after card until they accept it. That is why "propose" is in every description
 * below: a model that believes it has already acted narrates the change as done, and the user
 * reads that the lesson was rewritten while it is still sitting there waiting for a tap.
 */
fun tutorTools(toolset: TutorToolset): List<Tool>? = when (toolset) {
    TutorToolset.NONE -> null
    TutorToolset.LESSON -> listOf(Tool.functionDeclarations(lessonTools()))
    TutorToolset.ROADMAP -> listOf(Tool.functionDeclarations(roadmapTools()))
}

/**
 * The block ids these take are the ones the system instruction lists beside each part of the
 * lesson. There is no tool for finding one: an id the model did not read off its briefing is an
 * invented id, and the proposal is refused rather than landing on whatever it happens to match.
 */
private fun lessonTools(): List<FunctionDeclaration> = listOf(
    FunctionDeclaration(
        name = TutorTool.REWRITE_BLOCK,
        description = "Propose replacing the words of one part of the lesson, keeping what kind " +
            "of part it is. Use this to fix, clarify or expand something already there.",
        parameters = mapOf(
            "block_id" to Schema.string(
                description = "The id of the part to rewrite, exactly as listed in the lesson.",
            ),
            "text" to Schema.string(description = "The new wording, in full. Not a diff."),
        ),
    ),
    FunctionDeclaration(
        name = TutorTool.ADD_BLOCK,
        description = "Propose adding a new part to the lesson. Only headings, paragraphs and " +
            "code samples can be added; diagrams and pictures cannot.",
        parameters = mapOf(
            "kind" to Schema.enumeration(
                values = listOf("paragraph", "heading", "code"),
                description = "What kind of part to add.",
            ),
            "text" to Schema.string(description = "Its words."),
            "after_block_id" to Schema.string(
                description = "The id of the part it should follow. Leave out to put it at the " +
                    "very top of the lesson.",
            ),
            "language" to Schema.string(
                description = "For a code sample, what language it is written in.",
            ),
        ),
        optionalParameters = listOf("after_block_id", "language"),
    ),
    FunctionDeclaration(
        name = TutorTool.DELETE_BLOCK,
        description = "Propose removing one part of the lesson. Only when it is wrong or " +
            "redundant — rewriting is nearly always the better offer.",
        parameters = mapOf(
            "block_id" to Schema.string(description = "The id of the part to remove."),
        ),
    ),
    FunctionDeclaration(
        name = TutorTool.MOVE_BLOCK,
        description = "Propose moving one part of the lesson somewhere else in it, unchanged.",
        parameters = mapOf(
            "block_id" to Schema.string(description = "The id of the part to move."),
            "after_block_id" to Schema.string(
                description = "The id of the part it should follow once moved. Leave out to move " +
                    "it to the very top.",
            ),
        ),
        optionalParameters = listOf("after_block_id"),
    ),
    FunctionDeclaration(
        name = TutorTool.REWRITE_LESSON_FIELD,
        description = "Propose rewriting the lesson's summary or its \"why it matters\" note.",
        parameters = mapOf(
            "field" to Schema.enumeration(
                values = listOf("summary", "why_it_matters"),
                description = "Which of the two to rewrite.",
            ),
            "text" to Schema.string(description = "The new wording, in full."),
        ),
    ),
)

/**
 * Structure only. Nothing here writes a lesson: a section the tutor adds is generated the first
 * time it is opened, the same way a branched-out one is.
 */
private fun roadmapTools(): List<FunctionDeclaration> = listOf(
    FunctionDeclaration(
        name = TutorTool.RENAME_SECTION,
        description = "Propose renaming one section of the roadmap.",
        parameters = mapOf(
            "section_id" to Schema.string(
                description = "The id of the section, exactly as listed in the roadmap.",
            ),
            "title" to Schema.string(description = "Its new name — short, a few words."),
        ),
    ),
    FunctionDeclaration(
        name = TutorTool.RETIME_SECTION,
        description = "Propose changing how long a section is estimated to take.",
        parameters = mapOf(
            "section_id" to Schema.string(description = "The id of the section."),
            "minutes" to Schema.integer(description = "The new estimate, in minutes."),
        ),
    ),
    FunctionDeclaration(
        name = TutorTool.ADD_SECTION,
        description = "Propose adding a new section to the roadmap, following an existing one. " +
            "Its lesson is written later, when the user first opens it.",
        parameters = mapOf(
            "parent_id" to Schema.string(
                description = "The id of the section the new one should follow.",
            ),
            "title" to Schema.string(description = "The new section's name — short, a few words."),
            "minutes" to Schema.integer(
                description = "Roughly how long it should take, in minutes.",
            ),
        ),
        optionalParameters = listOf("minutes"),
    ),
    FunctionDeclaration(
        name = TutorTool.DELETE_SECTION,
        description = "Propose removing one section from the roadmap. Anything that followed it " +
            "stays, moving up to take its place.",
        parameters = mapOf(
            "section_id" to Schema.string(description = "The id of the section to remove."),
        ),
    ),
    FunctionDeclaration(
        name = TutorTool.MOVE_SECTION,
        description = "Propose moving a section so it follows a different one — for putting the " +
            "roadmap into a better order.",
        parameters = mapOf(
            "section_id" to Schema.string(description = "The id of the section to move."),
            "new_parent_id" to Schema.string(
                description = "The id of the section it should follow instead.",
            ),
        ),
    ),
)

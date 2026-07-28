package com.example.grasp.data.repository

import com.example.grasp.data.model.UserPreferences
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.model.TinkerStep
import org.json.JSONObject

/** One step exactly as the model returned it, before validation. */
internal data class GeneratedStep(
    val instruction: String,
    val detail: String = "",
    val estMinutes: Int = 0,
)

private const val SYSTEM_INSTRUCTION = """
    You generate structured step-by-step task guides.
    Always output valid JSON only.
"""

private const val MAX_EST_MINUTES = 4320

/**
 * Generates the Tinkerer checklist for [title] (FR3.1).
 *
 * Unlike Learner mode, each step's content is written up front and stored inline on the step
 * itself - there is no separate lazy contentRef fetch, since a task's steps are short and
 * meant to be scanned as a flat list.
 *
 * Never throws. If the call or the parse fails the user still gets a one-step starter guide
 * (NFR 3.1).
 */
suspend fun buildTinkerGuide(
    pathId: String,
    title: String,
    prefs: UserPreferences = UserPreferences(),
): TinkerGuide {
    val generated = geminiJson(SYSTEM_INSTRUCTION, guidePrompt(title, prefs))
        ?.let(::parseGeneratedSteps)
        .orEmpty()
    val steps = if (generated.isEmpty()) {
        fallbackSteps(title)
    } else {
        generated.mapIndexed { index, step ->
            TinkerStep(
                id = "step-${index + 1}",
                order = index + 1,
                instruction = step.instruction,
                detail = step.detail,
                estMinutes = step.estMinutes,
            )
        }
    }
    return TinkerGuide(id = pathId, title = title, steps = steps)
}

internal fun parseGeneratedSteps(json: JSONObject): List<GeneratedStep> =
    json.objectList("steps").mapNotNull { step ->
        val instruction = step.optString("instruction").trim()
        if (instruction.isEmpty()) return@mapNotNull null
        GeneratedStep(
            instruction = instruction,
            detail = step.optString("detail").trim(),
            estMinutes = step.optInt("estMinutes", 0).coerceIn(0, MAX_EST_MINUTES),
        )
    }

private fun guidePrompt(title: String, prefs: UserPreferences) = """
    Generate a step-by-step checklist guide as JSON for the task below.

    Task: $title
    
    User Preferences:
    - Pace: ${prefs.pace.label} (${prefs.pace.prompt})
    - Style: ${prefs.style.label} (${prefs.style.prompt})
    - Tone: ${prefs.tone.label} (${prefs.tone.prompt})

    Size Guidance:
    ${
    when (prefs.pace) {
        com.example.grasp.data.model.Pace.OVERVIEW -> """
            - STRICT LIMIT: Total steps MUST be between 3 and 5.
            - FOCUS: Only the absolute 'show-stopper' actions needed to finish.
        """.trimIndent()
        com.example.grasp.data.model.Pace.COMPREHENSIVE -> """
            - MINIMUM STEPS: Total steps MUST be at least 15.
            - FOCUS: Exhaustive micro-instructions. Include safety warnings, preparation steps, cleanup, and expert tips for every sub-action.
        """.trimIndent()
        else -> """
            - TARGET SIZE: 6-10 steps.
            - FOCUS: A standard, complete set of instructions.
        """.trimIndent()
    }
}

    Style Guidance:
    ${
    when (prefs.style) {
        com.example.grasp.data.model.Style.ACTIONABLE -> """
            - STYLE: ACTIONABLE. Focus on physical execution.
            - REQUIREMENT: Use precise, directive language and explicit measurements.
        """.trimIndent()
        com.example.grasp.data.model.Style.THEORETICAL -> """
            - STYLE: THEORETICAL. Briefly explain the logic behind key steps.
            - REQUIREMENT: Include 'detail' fields that explain WHY a step is performed a certain way.
        """.trimIndent()
        else -> """
            - STYLE: BALANCED.
        """.trimIndent()
    }
}

    Tone Guidance:
    ${
    when (prefs.tone) {
        com.example.grasp.data.model.Tone.ENCOURAGING -> """
            - TONE: ENCOURAGING. Use motivating and positive language in the 'detail' field.
        """.trimIndent()
        com.example.grasp.data.model.Tone.MINIMALIST -> """
            - TONE: MINIMALIST. Short instructions, no pleasantries.
        """.trimIndent()
        else -> """
            - TONE: PROFESSIONAL. Standard instructional tone.
        """.trimIndent()
    }
}

    Produce a flat, ORDERED list of steps a person can follow start to finish to accomplish
    this task. Each step is one concrete, actionable instruction — something to DO, not a
    subtopic to study.

    Guidance:
    - Ordered the way someone would actually perform them.
    - "instruction" is a short imperative sentence (e.g. "Crack two eggs into a bowl").
    - If the step involves an ingredient, material, or measurable quantity, state the EXACT
      amount and unit inline in "instruction" (e.g. "Whisk 2 cups flour, 1 tsp baking soda,
      and ½ tsp salt" — not "Mix the dry ingredients"). Never omit a quantity that a real
      recipe or task would specify.
    - "detail" is optional: one extra sentence of guidance, a safety note, or a tip — leave it
      blank if the instruction is self-explanatory.
    - "estMinutes" is an honest whole-minute estimate for that one step, between 1 and 4320.

    Return ONLY valid JSON, no commentary, no markdown fences.

    Format:
    {
      "steps": [
        { "instruction": "Whisk 2 cups flour, 1 cup sugar, and 1 tsp baking powder in a bowl", "detail": "", "estMinutes": 5 }
      ]
    }
""".trimIndent()

/** What the user gets when generation fails: a single step naming the task itself. */
private fun fallbackSteps(title: String): List<TinkerStep> = listOf(
    TinkerStep(id = "step-1", order = 1, instruction = "Get started on: $title", estMinutes = 5),
)
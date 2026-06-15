package com.example.grasp.data.model

/**
 * The two core product modes.
 *
 *  - [LEARNER]: "learn a topic" → produces a branching learning TREE / roadmap of subtopics.
 *  - [TINKERER]: "accomplish a task" → produces a FLAT, ordered step-by-step checklist.
 *
 * [label] is the user-facing name; [tagline] is a one-liner used on the mode picker.
 */
enum class Mode(val label: String, val tagline: String) {
    LEARNER("Learn", "Break a topic into a roadmap"),
    TINKERER("Tinker", "Get a step-by-step guide"),
}

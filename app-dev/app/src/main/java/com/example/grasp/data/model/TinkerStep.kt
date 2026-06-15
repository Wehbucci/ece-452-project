package com.example.grasp.data.model

/**
 * One step in a Tinkerer guide — a flat, ordered checklist item.
 *
 * @property id stable id (used for toggling/animation keys)
 * @property order 1-based position in the guide
 * @property instruction what to do for this step
 * @property detail optional extra explanation shown when expanded
 * @property estMinutes estimated time for this step
 * @property done whether the user has checked it off
 */
data class TinkerStep(
    val id: String,
    val order: Int,
    val instruction: String,
    val detail: String = "",
    val estMinutes: Int = 0,
    val done: Boolean = false,
)

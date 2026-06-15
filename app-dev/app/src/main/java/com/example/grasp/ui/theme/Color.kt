package com.example.grasp.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Grasp color palette — "calm focus".
 *
 * Design intent:
 *  - A LOW-AROUSAL, near-white background so long reading sessions stay comfortable.
 *  - ONE confident accent (indigo) used only for primary actions and the "active" path,
 *    so the eye always knows where the next action is.
 *  - Color is also SEMANTIC: it doubles as the learning-tree node states described in
 *    overview.md §8 — Completed (green), Active path (indigo/blue), Unexplored (grey).
 *
 * Naming convention:
 *  - The `Node*` and `Streak*` tokens are app-specific semantic colors used by the tree
 *    renderer, progress indicators and status chips. Reference them via
 *    `MaterialTheme`-adjacent helpers where possible; raw use is fine for the canvas.
 *
 * If you add a new color, add it here (never hard-code a hex value inside a Composable).
 */

// ---------------------------------------------------------------------------
// Brand anchors (the three hues the whole palette is derived from)
// ---------------------------------------------------------------------------
/** Indigo — primary actions and the "active path" node state. Calm but decisive. */
private val Indigo = Color(0xFF3F51D6)

/** Teal-green — growth, progress and the "completed" node state. */
private val Growth = Color(0xFF1E8E72)

/** Warm amber — sparing highlights (streaks, "new", attention markers). */
private val Amber = Color(0xFF7A5900)

// ---------------------------------------------------------------------------
// Light scheme tokens
// ---------------------------------------------------------------------------
val PrimaryLight = Indigo
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFDEE0FF)
val OnPrimaryContainerLight = Color(0xFF000F5E)

val SecondaryLight = Growth
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFA6F2DC)
val OnSecondaryContainerLight = Color(0xFF00201A)

val TertiaryLight = Amber
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFFDEA6)
val OnTertiaryContainerLight = Color(0xFF261A00)

val BackgroundLight = Color(0xFFFBF8FF) // soft cool white — easy on the eyes for long reads
val OnBackgroundLight = Color(0xFF1A1B22)
val SurfaceLight = Color(0xFFFBF8FF)
val OnSurfaceLight = Color(0xFF1A1B22)
val SurfaceVariantLight = Color(0xFFE3E1EC)
val OnSurfaceVariantLight = Color(0xFF46464F)
val SurfaceContainerLight = Color(0xFFF0EDF7) // resting elevation for cards
val OutlineLight = Color(0xFF777680)
val OutlineVariantLight = Color(0xFFC7C5D0)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

// ---------------------------------------------------------------------------
// App-specific SEMANTIC colors (tree node states + progress). Used by the Compose Canvas
// tree renderer, status chips and progress bars.
// ---------------------------------------------------------------------------
/** A node / subtopic the user has finished. */
val NodeCompleted = Color(0xFF1E8E72)

/** The node the user is currently on, or the next recommended step. */
val NodeActive = Color(0xFF3F51D6)

/** A generated-but-not-yet-started node. */
val NodeUnexplored = Color(0xFF9A99A3)

/** "Branch out" affordance — a place the user can expand a new branch (drawn dashed). */
val NodeBranchOut = Color(0xFFB07A00)

/** Streak / highlight accent for attention-keeping nudges. Use sparingly. */
val StreakAccent = Color(0xFFF2A03D)

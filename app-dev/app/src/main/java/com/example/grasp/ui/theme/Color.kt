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

// ---------------------------------------------------------------------------
// Gamified learning-path palette (Duolingo journey + RPG skill-tree)
// ---------------------------------------------------------------------------
// Semantic tokens for the vertically-scrolling roadmap and its subtopic bottom sheet
// (see `design_handoff_gamified_path/README.md`). These are the "game" surface: chunky
// bevelled node buttons, an XP bar, region pills and celebratory motion. They intentionally
// live NEXT TO the calm `Node*` tokens above rather than replacing them — the journey screen
// is a deliberately louder, more playful surface than the rest of the app.
//
// Node-state → token mapping (state is DERIVED in PathPresenter; the Composable only reads):
//   done    -> fill PathNodeDone,    bevel PathNodeDoneBevel    (+ soft green glow)
//   current -> fill PathNodeCurrent, bevel PathNodeCurrentBevel (+ soft indigo glow + pulse ring)
//   open    -> fill PathCard, 3.dp border PathNodeCurrent, bevel PathNodeOpenBevel
//   locked  -> fill PathNodeLockedFill, ink PathNodeLockedInk, bevel PathNodeLockedBevel
//   branch  -> fill PathCard, 3.dp dashed border PathNodeBranch, bevel PathNodeBranchBevel
//
// "Game bevel" = a hard (0-blur) solid drop shadow directly below the circle in the *Bevel
// token; do NOT use Modifier.shadow (its blur ruins the flat, stacked-plastic look).

// ---- Text / ink ----
/** Primary text and dark pills on the journey surface. */
val PathInk = Color(0xFF211D3B)

/** Secondary text (subtitles, muted labels). */
val PathMuted = Color(0xFF7B778F)

/** Tertiary text — estimates, and the "ink" of a locked node's label. */
val PathFaint = Color(0xFFA8A5BC)

// ---- Surfaces ----
/** Journey screen background — a soft lilac-tinted off-white. */
val PathScreenBg = Color(0xFFF3F2FB)

/** HUD header card + bottom-sheet card. */
val PathCard = Color(0xFFFFFFFF)

/** Unfilled XP-bar track. */
val PathXpTrack = Color(0xFFEDEBF7)

/** Unfilled connector path between nodes. */
val PathConnector = Color(0xFFE6E4F0)

/** Region-label pill background ("FOUNDATIONS", "MASTERY", …). */
val PathRegionPill = Color(0xFFEAE8F5)

/** Neutral state-chip background in the subtopic sheet (upcoming/other states). */
val PathChipNeutralBg = Color(0xFFF3F2FB)

// ---- Node: DONE (green) ----
val PathNodeDone = Color(0xFF1FB980)
val PathNodeDoneBevel = Color(0xFF159A69) // hard bevel + filled connector color
val PathNodeDoneTint = Color(0xFFE2F7EF)  // "done" state-chip background

// ---- Node: CURRENT / OPEN (indigo, the one accent that says "go here next") ----
val PathNodeCurrent = Color(0xFF6C5CE7)
val PathNodeCurrentBevel = Color(0xFF5346D6)
val PathNodeCurrentTint = Color(0xFFEDEAFE) // "current" state-chip background
val PathNodeOpenBevel = Color(0xFFE4E1F0)   // soft bevel under an available (open) node

// ---- Node: BRANCH + XP (amber "grow your path") ----
val PathNodeBranch = Color(0xFFF5A524)
val PathNodeBranchBevel = Color(0xFFEBDFC7)
val PathXpFillStart = Color(0xFFF5A524) // XP-bar gradient start
val PathXpFillEnd = Color(0xFFFFC65C)   // XP-bar gradient end

// ---- Node: LOCKED ----
val PathNodeLockedFill = Color(0xFFE1DFEC)
val PathNodeLockedBevel = Color(0xFFCAC7D8)
val PathNodeLockedInk = Color(0xFFA8A5BC)

// ---- Accents ----
/** Streak flame + count in the HUD. */
val PathStreak = Color(0xFFFF6B57)

/** "Why it matters" callout background in the subtopic sheet. */
val PathWhyItMattersBg = Color(0xFFF7F0FF)

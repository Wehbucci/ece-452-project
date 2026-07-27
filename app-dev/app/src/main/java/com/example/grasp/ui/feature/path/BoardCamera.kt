package com.example.grasp.ui.feature.path

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * Where the journey board is being looked at from.
 *
 * The board is drawn as one transformed layer pinned at the viewport's top-left corner and scaled
 * about that same corner, which makes the whole camera this one mapping:
 *
 *     screen = offset + scale × board
 *
 * [offset] is therefore literally where the board's own origin lands on screen, in pixels.
 *
 * This lives apart from the Composable, and with no Compose or Android types beyond plain geometry,
 * because "does the root actually end up on screen" is the part that kept being wrong and needs to
 * be answerable by a unit test rather than by reading a layout and hoping.
 */
internal data class BoardCamera(val scale: Float, val offset: Offset) {

    /** Where a point on the board is drawn, in viewport pixels. */
    fun screenPositionOf(boardPoint: Offset): Offset = offset + boardPoint * scale
}

/**
 * The camera the board frames itself with until the user touches it: zoomed to fit [content]'s
 * width within [marginPx] either side, and aimed at [focusCentre] — the node the learner is on.
 *
 * Both axes are solved outright: the focused node's centre is placed on the viewport's centre line,
 * [focusInsetPx] below its top. Nothing about the result depends on layout alignment, a transform
 * pivot, or any other implicit contribution, because earlier versions leaned on those and put the
 * target off screen with nothing in the arithmetic to show it.
 *
 * Zoom is bounded below by [minZoom]: a roadmap several branches wide cannot be fitted whole on a
 * phone without shrinking its titles to nothing, so a wide board opens readable and overhanging and
 * is panned instead. It is never zoomed past 1x, since the nodes are drawn at a deliberate size.
 *
 * Deliberately NOT clamped by [clampOffset] — a viewport that hasn't been measured yet produces
 * nonsense bounds, and clamping the opening frame against them is what pushed the board off target
 * once already. Guards against a zero-sized viewport or board so a first measure pass can't divide
 * by it.
 */
internal fun openingCamera(
    viewport: Size,
    content: Size,
    focusCentre: Offset,
    marginPx: Float,
    focusInsetPx: Float,
    minZoom: Float,
): BoardCamera {
    val fitted = if (viewport.width <= 0f || content.width <= 0f) {
        1f
    } else {
        ((viewport.width - 2f * marginPx) / content.width).coerceIn(minZoom, 1f)
    }
    return BoardCamera(
        scale = fitted,
        offset = Offset(
            x = viewport.width / 2f - fitted * focusCentre.x,
            y = focusInsetPx - fitted * focusCentre.y,
        ),
    )
}

/**
 * Pulls [candidate] back to somewhere that still leaves [keepOnScreenPx] of the board in view.
 *
 * Deliberately loose: the only thing panning is stopped from doing is losing the board altogether.
 * A clamp tight to the board's edges pins a board that already fits dead centre, so every drag does
 * nothing, and any asymmetry in one shows up as a direction that mysteriously refuses to move.
 *
 * Each bound is applied on its own rather than as a range, because on an unmeasured viewport they
 * can cross over and `coerceIn` throws when they do.
 */
internal fun clampOffset(
    candidate: Offset,
    scale: Float,
    viewport: Size,
    content: Size,
    keepOnScreenPx: Float,
): Offset = Offset(
    x = candidate.x
        .coerceAtMost(viewport.width - keepOnScreenPx)
        .coerceAtLeast(keepOnScreenPx - content.width * scale),
    y = candidate.y
        .coerceAtMost(viewport.height - keepOnScreenPx)
        .coerceAtLeast(keepOnScreenPx - content.height * scale),
)

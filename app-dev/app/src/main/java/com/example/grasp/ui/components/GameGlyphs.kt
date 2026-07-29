package com.example.grasp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.grasp.data.model.Mode

/**
 * Hand-drawn (Canvas) glyphs for the game shell.
 *
 * Why not Material icons? Only `material-icons-core` is on the classpath — the curated ~40-icon
 * set — so anything expressive (book, checklist, sliders, bell, sprout) has to be drawn. Doing
 * it in Canvas also keeps the glyphs on-brand: same rounded caps and chunky stroke weight as the
 * roadmap nodes, and they scale to any size without an asset.
 *
 * Every glyph is a pure function of its box size, so callers only pick a [Dp] size and a tint.
 */

/** Stroke width that keeps a glyph looking "chunky" at any size. */
private fun DrawScope.chunky(fraction: Float = 0.1f) =
    (size.minDimension * fraction).coerceAtLeast(1.6f)

/**
 * A rounded-square icon tile: the faint [tint] container that every glyph sits in across
 * Home cards, Library cards and the Profile settings list.
 */
@Composable
fun GameIconTile(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    corner: Dp = 15.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(tint, RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** The glyph that stands for a [Mode]: an open book for Learn, a checklist for Tinker. */
@Composable
fun ModeGlyph(
    mode: Mode,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) = when (mode) {
    Mode.LEARNER -> BookGlyph(tint, modifier.size(size))
    Mode.TINKERER -> ChecklistGlyph(tint, modifier.size(size))
}

/** Open book — "learn a topic" (matches the roadmap's OPEN-node glyph). */
@Composable
fun BookGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sw = chunky(0.11f)
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.14f, size.height * 0.2f),
            size = Size(size.width * 0.72f, size.height * 0.6f),
            cornerRadius = CornerRadius(size.width * 0.14f),
            style = Stroke(width = sw),
        )
        // The spine turns a plain card into a book.
        drawLine(
            color = tint,
            start = Offset(size.width * 0.5f, size.height * 0.2f),
            end = Offset(size.width * 0.5f, size.height * 0.8f),
            strokeWidth = sw,
        )
    }
}

/** Three ticked rows — "a task, step by step" (Tinker mode is a flat checklist). */
@Composable
fun ChecklistGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sw = chunky(0.1f)
        val boxSize = size.width * 0.2f
        listOf(0.2f, 0.5f, 0.8f).forEachIndexed { index, y ->
            val cy = size.height * y
            drawRoundRect(
                color = tint,
                topLeft = Offset(size.width * 0.08f, cy - boxSize / 2f),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(boxSize * 0.3f),
                style = Stroke(width = sw),
            )
            // The first row reads as "done" — fill its box in.
            if (index == 0) {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.08f, cy - boxSize / 2f),
                    size = Size(boxSize, boxSize),
                    cornerRadius = CornerRadius(boxSize * 0.3f),
                )
            }
            drawLine(
                color = tint,
                start = Offset(size.width * 0.4f, cy),
                end = Offset(size.width * 0.92f, cy),
                strokeWidth = sw,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Two sliders — "learning preferences" (difficulty, depth, pace). */
@Composable
fun SlidersGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sw = chunky(0.1f)
        val knob = size.minDimension * 0.15f
        listOf(0.32f to 0.66f, 0.68f to 0.34f).forEach { (y, knobX) ->
            drawLine(
                color = tint,
                start = Offset(size.width * 0.1f, size.height * y),
                end = Offset(size.width * 0.9f, size.height * y),
                strokeWidth = sw,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = tint,
                radius = knob,
                center = Offset(size.width * knobX, size.height * y),
            )
        }
    }
}

/** Arrow into a tray — "offline content" (downloaded paths). */
@Composable
fun DownloadGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sw = chunky(0.11f)
        val cx = size.width * 0.5f
        drawLine(
            color = tint,
            start = Offset(cx, size.height * 0.14f),
            end = Offset(cx, size.height * 0.58f),
            strokeWidth = sw,
            cap = StrokeCap.Round,
        )
        // Arrow head.
        listOf(-1f, 1f).forEach { direction ->
            drawLine(
                color = tint,
                start = Offset(cx + direction * size.width * 0.2f, size.height * 0.4f),
                end = Offset(cx, size.height * 0.6f),
                strokeWidth = sw,
                cap = StrokeCap.Round,
            )
        }
        // Tray.
        drawLine(
            color = tint,
            start = Offset(size.width * 0.16f, size.height * 0.82f),
            end = Offset(size.width * 0.84f, size.height * 0.82f),
            strokeWidth = sw,
            cap = StrokeCap.Round,
        )
    }
}

/** Bell — "notifications" (reminders, streak nudges). */
@Composable
fun BellGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sw = chunky(0.1f)
        val w = size.width
        val h = size.height
        // Silhouette: straight flaring sides under a domed top. Drawn as a path (rather than a
        // rounded rect) because a uniform corner radius reads as a light bulb, not a bell.
        val bell = Path().apply {
            moveTo(w * 0.18f, h * 0.66f)
            lineTo(w * 0.28f, h * 0.4f)
            quadraticTo(w * 0.5f, h * 0.06f, w * 0.72f, h * 0.4f)
            lineTo(w * 0.82f, h * 0.66f)
        }
        drawPath(bell, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round))
        // Skirt.
        drawLine(
            color = tint,
            start = Offset(w * 0.12f, h * 0.68f),
            end = Offset(w * 0.88f, h * 0.68f),
            strokeWidth = sw,
            cap = StrokeCap.Round,
        )
        // Clapper.
        drawCircle(color = tint, radius = w * 0.09f, center = Offset(w * 0.5f, h * 0.84f))
    }
}

/** Circle with an "i" — "about". */
@Composable
fun InfoGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sw = chunky(0.1f)
        val r = size.minDimension * 0.38f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = tint, radius = r, center = center, style = Stroke(width = sw))
        drawCircle(color = tint, radius = sw * 0.6f, center = center.copy(y = center.y - r * 0.45f))
        drawLine(
            color = tint,
            start = center.copy(y = center.y - r * 0.05f),
            end = center.copy(y = center.y + r * 0.5f),
            strokeWidth = sw,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * A sprout: Grasp's mark. A stem with two leaves — "something you grow", which is exactly what
 * a learning path is in this app.
 */
@Composable
fun SproutGlyph(
    stem: Color,
    leaf: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = chunky(0.1f)
        // Stem, from the soil line up between the leaves.
        drawLine(
            color = stem,
            start = Offset(w * 0.5f, h * 0.92f),
            end = Offset(w * 0.5f, h * 0.3f),
            strokeWidth = sw,
            cap = StrokeCap.Round,
        )
        // Two leaves at DIFFERENT heights (left high, right low). A symmetric pair reads as wings
        // or a moustache; staggering them is what makes the mark read as a growing plant.
        val leafWidth = w * 0.34f
        val leafHeight = h * 0.19f
        leaf(color = leaf, cx = w * 0.31f, cy = h * 0.42f, width = leafWidth, height = leafHeight, tilt = -32f)
        leaf(color = leaf, cx = w * 0.69f, cy = h * 0.58f, width = leafWidth, height = leafHeight, tilt = 32f)
    }
}

/** One leaf of [SproutGlyph]: an oval centred on ([cx], [cy]) and tilted by [tilt] degrees. */
private fun DrawScope.leaf(
    color: Color,
    cx: Float,
    cy: Float,
    width: Float,
    height: Float,
    tilt: Float,
) {
    rotate(degrees = tilt, pivot = Offset(cx, cy)) {
        drawOval(
            color = color,
            topLeft = Offset(cx - width / 2f, cy - height / 2f),
            size = Size(width, height),
        )
    }
}

/**
 * A miniature of the roadmap — three connected nodes — used as the illustration on empty
 * states so a blank Library still shows what the app makes.
 */
@Composable
fun MiniPathArt(
    doneColor: Color,
    activeColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = size.minDimension * 0.13f
        val nodes = listOf(
            Offset(w * 0.5f, h * 0.18f),
            Offset(w * 0.22f, h * 0.55f),
            Offset(w * 0.72f, h * 0.85f),
        )
        val stroke = r * 0.45f
        nodes.zipWithNext { a, b ->
            drawLine(color = trackColor, start = a, end = b, strokeWidth = stroke, cap = StrokeCap.Round)
        }
        drawCircle(color = doneColor, radius = r, center = nodes[0])
        drawCircle(color = trackColor, radius = r, center = nodes[1])
        drawCircle(color = activeColor, radius = r * 1.15f, center = nodes[2])
    }
}

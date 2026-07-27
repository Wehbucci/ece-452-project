package com.example.grasp.ui.feature.path

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JUnit tests for how the board frames itself.
 *
 * These exist because "the root is visible when the roadmap opens" was reported broken repeatedly,
 * each time after reasoning that the layout would place it correctly. The framing is pure
 * arithmetic now, so the claim is checked here instead of inferred: for every shape of tree, the
 * focused node's centre must land inside the viewport.
 */
class BoardCameraTest {

    private val viewport = Size(width = 1080f, height = 1600f)
    private val margin = 72f
    private val focusInset = 288f
    private val minZoom = 0.7f
    private val keepOnScreen = 360f

    /** A board [columns] wide and [rows] deep, in the same px units as the viewport. */
    private fun board(columns: Float, rows: Float) = Size(columns * 336f, rows * 444f)

    /** The focused node of a tree, centred over the board and one row down from the top. */
    private fun focusOf(content: Size) = Offset(x = content.width / 2f, y = 234f)

    private fun frame(content: Size, focus: Offset = focusOf(content)) = openingCamera(
        viewport = viewport,
        content = content,
        focusCentre = focus,
        marginPx = margin,
        focusInsetPx = focusInset,
        minZoom = minZoom,
    )

    private fun assertFocusOnScreen(camera: BoardCamera, focus: Offset) {
        val onScreen = camera.screenPositionOf(focus)
        assertTrue(
            "focus drawn at $onScreen, outside the ${viewport.width}x${viewport.height} viewport",
            onScreen.x in 0f..viewport.width && onScreen.y in 0f..viewport.height,
        )
    }

    @Test
    fun `the focused node lands on the centre line at the top, whatever the tree looks like`() {
        // One strand, three strands, and a board far wider than the phone.
        listOf(board(1f, 4f), board(3f, 5f), board(9f, 12f)).forEach { content ->
            val focus = focusOf(content)
            val camera = frame(content)
            val onScreen = camera.screenPositionOf(focus)

            assertEquals("horizontally centred", viewport.width / 2f, onScreen.x, 0.5f)
            assertEquals("at the top inset", focusInset, onScreen.y, 0.5f)
            assertFocusOnScreen(camera, focus)
        }
    }

    @Test
    fun `a lopsided focus is still centred rather than the board being centred`() {
        // A node off to one side of its own board — centring the BOARD would leave it off to the
        // side of the screen, which is the bug this solves for.
        val content = board(9f, 6f)
        val focus = Offset(x = content.width * 0.85f, y = 234f)

        val camera = frame(content, focus)

        assertEquals(viewport.width / 2f, camera.screenPositionOf(focus).x, 0.5f)
        assertFocusOnScreen(camera, focus)
    }

    @Test
    fun `a deep roadmap still opens with its root on screen`() {
        // A roadmap that has grown far taller than the phone. The frame aims at the root, so no
        // amount of depth below it may push it off the top.
        val content = board(3f, 40f)
        val root = focusOf(content)

        assertFocusOnScreen(frame(content, root), root)
        assertEquals(focusInset, frame(content, root).screenPositionOf(root).y, 0.5f)
    }

    @Test
    fun `a board narrower than the screen opens at 1x rather than being stretched`() {
        assertEquals(1f, frame(board(1f, 4f)).scale, 0.001f)
    }

    @Test
    fun `a wide board opens readable and overhanging rather than fitted whole`() {
        val camera = frame(board(9f, 12f))

        assertEquals("floored at the readable zoom", minZoom, camera.scale, 0.001f)
        assertTrue("and therefore wider than the screen", board(9f, 12f).width * camera.scale > viewport.width)
    }

    @Test
    fun `an unmeasured viewport cannot produce a frame that hides the focus`() {
        // BoxWithConstraints composes during measure, so this really does happen on the first pass.
        // It must not divide by zero, and must not be the pass that decides where the board sits.
        val camera = openingCamera(
            viewport = Size.Zero,
            content = board(3f, 5f),
            focusCentre = focusOf(board(3f, 5f)),
            marginPx = margin,
            focusInsetPx = focusInset,
            minZoom = minZoom,
        )

        assertEquals(1f, camera.scale, 0.001f)
        assertTrue("no NaN or infinity leaks out", camera.offset.x.isFinite() && camera.offset.y.isFinite())
    }

    @Test
    fun `clamping never drags the opening frame off the focus`() {
        // The opening frame is not clamped in the Composable, but it must be inside the clamp's
        // bounds anyway — that mismatch is exactly what shoved the board up into the middle before.
        listOf(board(1f, 4f), board(3f, 5f), board(9f, 12f)).forEach { content ->
            val camera = frame(content)
            val clamped = clampOffset(camera.offset, camera.scale, viewport, content, keepOnScreen)

            assertEquals("x moved for a ${content.width}px board", camera.offset.x, clamped.x, 0.5f)
            assertEquals("y moved for a ${content.height}px board", camera.offset.y, clamped.y, 0.5f)
        }
    }

    @Test
    fun `panning stays live in all four directions at every zoom`() {
        val content = board(3f, 5f)
        val camera = frame(content)
        val far = 10_000f

        fun clamp(candidate: Offset) =
            clampOffset(candidate, camera.scale, viewport, content, keepOnScreen)

        // Dragging hard each way has to actually move the board, including back up past the top.
        assertTrue("cannot pan left", clamp(camera.offset - Offset(far, 0f)).x < camera.offset.x)
        assertTrue("cannot pan right", clamp(camera.offset + Offset(far, 0f)).x > camera.offset.x)
        assertTrue("cannot pan up", clamp(camera.offset - Offset(0f, far)).y < camera.offset.y)
        assertTrue("cannot pan down", clamp(camera.offset + Offset(0f, far)).y > camera.offset.y)
    }

    @Test
    fun `the board can never be flung entirely off screen`() {
        val content = board(3f, 5f)
        val camera = frame(content)
        val far = 100_000f

        listOf(Offset(far, far), Offset(-far, -far), Offset(far, -far), Offset(-far, far)).forEach { push ->
            val offset = clampOffset(camera.offset + push, camera.scale, viewport, content, keepOnScreen)
            val right = offset.x + content.width * camera.scale
            val bottom = offset.y + content.height * camera.scale

            assertTrue("board escaped to $offset", right >= keepOnScreen && offset.x <= viewport.width - keepOnScreen)
            assertTrue("board escaped to $offset", bottom >= keepOnScreen && offset.y <= viewport.height - keepOnScreen)
        }
    }
}

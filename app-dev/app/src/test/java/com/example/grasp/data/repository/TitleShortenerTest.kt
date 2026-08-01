package com.example.grasp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of title-shortening that don't touch Gemini.
 *
 * What the model writes is non-deterministic, but the decisions AROUND it are not: which titles
 * are worth an AI call at all, and what counts as an acceptable answer. Both matter — the first
 * is what keeps a freshly generated roadmap from paying for a call it doesn't need, and the
 * second is what stops a bad answer replacing a perfectly good title.
 */
class TitleShortenerTest {

    @Test
    fun `counts words, ignoring the spacing around them`() {
        assertEquals(1, titleWordCount("Regression"))
        assertEquals(3, titleWordCount("How Rockets Work"))
        assertEquals(3, titleWordCount("  How  Rockets   Work  "))
        assertEquals(0, titleWordCount("   "))
    }

    @Test
    fun `only titles past the board's budget are too long`() {
        assertFalse(isTitleTooLong("Labelled Data"))
        assertFalse("exactly the budget is fine", isTitleTooLong("The Forgetting Curve Explained"))
        assertTrue(isTitleTooLong("Understanding the Role of Labelled Data"))
    }

    /** A roadmap whose titles are already short must never trigger a call. */
    @Test
    fun `a roadmap of short titles has nothing to shorten`() {
        val titles = listOf("What Orbit Is", "How Rockets Work", "Reusable Rockets")
        assertTrue(titles.none(::isTitleTooLong))
    }

    @Test
    fun `strips the packaging a model wraps a title in`() {
        assertEquals("Labelled Data", cleanTitle("  \"Labelled Data\"  "))
        assertEquals("Labelled Data", cleanTitle("3. Labelled Data"))
        assertEquals("Labelled Data", cleanTitle("1) Labelled Data."))
    }

    /** The packaging nests, and one layer can hide the next from being recognised at all. */
    @Test
    fun `strips packaging that is wrapped around more packaging`() {
        assertEquals("quotes hidden by a colon", "Labelled Data", cleanTitle("'Labelled Data':"))
        assertEquals("quotes hidden by a stop", "Labelled Data", cleanTitle("\"Labelled Data\"."))
        assertEquals("quotes hidden by numbering", "Labelled Data", cleanTitle("1. \"Labelled Data\""))
    }

    /** Cleaning must not turn a real title into an empty one — that gets the original kept. */
    @Test
    fun `nothing but packaging cleans to nothing`() {
        assertEquals("", cleanTitle("   "))
        assertEquals("", cleanTitle("\"\""))
    }
}

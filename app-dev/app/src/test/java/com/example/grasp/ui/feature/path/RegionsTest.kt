package com.example.grasp.ui.feature.path

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the bands down the side of the board are allowed to claim.
 *
 * The bug these pin: a single "FOUNDATIONS" pill on the root labelled the entire roadmap, because
 * a region pill is a banner with no closing edge. So the rule under test is less "which labels"
 * than "never exactly one" — a roadmap gets a full set that reaches the bottom, or none.
 */
class RegionsTest {

    /** rows 0..n-1, one node per row, named for its row. */
    private fun rows(count: Int): Map<String, Int> =
        (0 until count).associate { "n$it" to it }

    @Test
    fun `a roadmap too shallow to divide gets no bands at all`() {
        assertTrue(regionsFor(rows(1)).isEmpty())
        assertTrue(regionsFor(rows(2)).isEmpty())
        assertTrue(regionsFor(emptyMap()).isEmpty())
    }

    /**
     * The heart of it. One banner over a whole roadmap is what made every lesson look like a
     * foundation, so a derived set is never allowed to be of size one.
     */
    @Test
    fun `derived bands never come alone`() {
        (1..24).forEach { rowCount ->
            val bands = regionsFor(rows(rowCount))
            assertTrue(
                "$rowCount rows produced a single band, which would claim the whole board",
                bands.size != 1,
            )
        }
    }

    @Test
    fun `a shallow roadmap splits in two, a deep one in three`() {
        assertEquals(listOf("FOUNDATIONS", "MASTERY"), regionsFor(rows(4)).map { it.label })
        assertEquals(
            listOf("FOUNDATIONS", "BUILDING UP", "MASTERY"),
            regionsFor(rows(9)).map { it.label },
        )
    }

    /** Bands have to land on distinct rows in order, or two pills stack on one another. */
    @Test
    fun `bands are ordered and never share a row`() {
        (3..24).forEach { rowCount ->
            val bandRows = regionsFor(rows(rowCount)).map { it.row }
            assertEquals(
                "$rowCount rows put two bands on the same row",
                bandRows.distinct(),
                bandRows,
            )
            assertEquals("$rowCount rows produced bands out of order", bandRows.sorted(), bandRows)
            assertTrue("$rowCount rows put a band past the board", bandRows.all { it < rowCount })
        }
    }

    @Test
    fun `the first band always starts at the top`() {
        (3..24).forEach { rowCount ->
            assertEquals(0, regionsFor(rows(rowCount)).first().row)
        }
    }

    /** An author read the material; this only counted rows. */
    @Test
    fun `hand-authored tiers win over the derived bands`() {
        val authored = regionsFor(
            rowByNode = rows(9),
            tierByNode = mapOf("n0" to "FOUNDATIONS", "n4" to "CORE · PICK A TRACK"),
        )

        assertEquals(listOf("FOUNDATIONS", "CORE · PICK A TRACK"), authored.map { it.label })
        assertEquals(listOf(0, 4), authored.map { it.row })
    }

    /** Several nodes may carry the same tier; the pill belongs at the shallowest of them. */
    @Test
    fun `a tier shared by several nodes is anchored at its first row`() {
        val authored = regionsFor(
            rowByNode = mapOf("a" to 5, "b" to 2, "c" to 7),
            tierByNode = mapOf("a" to "CORE", "b" to "CORE", "c" to "CORE"),
        )

        assertEquals(listOf(RegionUi("CORE", 2)), authored)
    }
}

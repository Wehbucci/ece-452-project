package com.example.grasp.ui.feature.path

/**
 * The banded labels down the side of the journey — "FOUNDATIONS", "BUILDING UP", "MASTERY".
 *
 * A region pill is a banner at a row with no closing edge, so whatever is drawn under it reads as
 * belonging to it all the way to the next one — or to the bottom of the board if there isn't a next
 * one. That is what made a single "FOUNDATIONS" pill on the root claim the entire roadmap: not a
 * wrong label, a label with nothing after it. A roadmap therefore has either NO bands or a full set
 * that reaches the bottom; one band is never an option.
 *
 * Bands come from DEPTH, which is the only progression the board actually knows. The generator is
 * told to order each strand foundational-to-advanced (see `LearnerTreeGenerator`'s prompt), so a
 * node's row genuinely tracks how far into the subject it is — but this is an inference from shape,
 * not from content, so the labels stay deliberately generic. "MASTERY" over the bottom third is a
 * claim about position in the roadmap; naming that third would be a claim about what is in it.
 *
 * An author who set tiers by hand wins outright: they read the material, this only counted rows.
 *
 * Pure Kotlin, no Compose — same reason as everything else in this package.
 */

/** What a derived band is called, shallowest first. */
private const val FOUNDATIONS = "FOUNDATIONS"
private const val BUILDING_UP = "BUILDING UP"
private const val MASTERY = "MASTERY"

/**
 * Below this many rows a roadmap is not deep enough to divide.
 *
 * Two rows is a root and its first lessons; slicing that into "foundations" and "mastery" would be
 * labelling two steps as a journey.
 */
private const val MIN_ROWS_FOR_BANDS = 3

/** At or above this many rows the middle band earns its place; below it two bands read better. */
private const val MIN_ROWS_FOR_THREE_BANDS = 6

/**
 * The pills to draw, in row order.
 *
 * @param rowByNode every node's row, as [com.example.grasp.core.layout.layoutBoard] derived it.
 * @param tierByNode hand-authored tiers, if the roadmap carries any. One pill per distinct tier,
 *        anchored at that tier's shallowest row.
 */
internal fun regionsFor(
    rowByNode: Map<String, Int>,
    tierByNode: Map<String, String> = emptyMap(),
): List<RegionUi> {
    if (rowByNode.isEmpty()) return emptyList()

    // Hand-authored wins. Several nodes may declare the same tier, so each label is anchored at the
    // shallowest row that carries it.
    val authored = tierByNode
        .mapNotNull { (id, tier) -> rowByNode[id]?.let { row -> RegionUi(tier, row) } }
        .groupBy { it.label }
        .map { (_, sharing) -> sharing.minBy { it.row } }
        .sortedBy { it.row }
    if (authored.isNotEmpty()) return authored

    val rowCount = rowByNode.values.max() + 1
    return when {
        rowCount < MIN_ROWS_FOR_BANDS -> emptyList()

        rowCount < MIN_ROWS_FOR_THREE_BANDS -> listOf(
            RegionUi(FOUNDATIONS, 0),
            RegionUi(MASTERY, (rowCount + 1) / 2),
        )

        else -> listOf(
            RegionUi(FOUNDATIONS, 0),
            RegionUi(BUILDING_UP, rowCount / 3),
            RegionUi(MASTERY, rowCount * 2 / 3),
        )
    }
}

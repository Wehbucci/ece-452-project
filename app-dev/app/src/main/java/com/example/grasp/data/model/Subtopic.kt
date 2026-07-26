package com.example.grasp.data.model

/**
 * The fully-resolved content for one subtopic — i.e. what you get after following a
 * [TreeNode.contentRef] (overview.md §5 "resolve contentRef lazily"). This is the heavy
 * object the Subtopic detail screen renders.
 *
 * @property nodeId id of the [TreeNode] this content belongs to
 * @property title subtopic name
 * @property sectionLabel position indicator for the header, e.g. "Section 4 of 8". A Learner
 *           roadmap is made of sections that can fork and be branched off, NOT of ordered steps —
 *           steps belong to Tinkerer guides ([TinkerStep]).
 * @property summary the quick intro/excerpt
 * @property whyItMatters short "why this matters" blurb
 * @property body the lesson itself: headings and paragraphs, where each paragraph is a block the
 *           user can tap to chat about (FR4.1).
 * @property resources "Dive deeper" links
 * @property estMinutes estimated reading/work time
 * @property completed whether the subtopic is marked done
 */
data class Subtopic(
    val nodeId: String,
    val title: String,
    val sectionLabel: String,
    val summary: String,
    val whyItMatters: String,
    val body: List<LessonBlock>,
    val resources: List<ResourceLink>,
    val estMinutes: Int,
    val completed: Boolean = false,
)

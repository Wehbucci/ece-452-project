package com.example.grasp.data.model

/**
 * The fully-resolved content for one subtopic — i.e. what you get after following a
 * [TreeNode.contentRef] (overview.md §5 "resolve contentRef lazily"). This is the heavy
 * object the Subtopic detail screen renders.
 *
 * @property nodeId id of the [TreeNode] this content belongs to
 * @property title subtopic name
 * @property stepLabel position indicator for the header, e.g. "Step 4 of 8"
 * @property summary the quick intro/excerpt
 * @property whyItMatters short "why this matters" blurb
 * @property body the deeper material, split into blocks the user can chat about (FR4.1).
 *           For the skeleton these are plain paragraphs; later they become rich content.
 * @property resources "Dive deeper" links
 * @property estMinutes estimated reading/work time
 * @property completed whether the subtopic is marked done
 */
data class Subtopic(
    val nodeId: String,
    val title: String,
    val stepLabel: String,
    val summary: String,
    val whyItMatters: String,
    val body: List<String>,
    val resources: List<ResourceLink>,
    val estMinutes: Int,
    val completed: Boolean = false,
)

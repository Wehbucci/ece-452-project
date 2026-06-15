package com.example.grasp.data.model

/**
 * A "Dive deeper" external resource attached to a subtopic.
 *
 * @property title human-readable label, e.g. "Wikipedia: Machine learning"
 * @property url destination opened in a browser/custom tab
 * @property kind used to pick an icon and group links
 */
data class ResourceLink(
    val title: String,
    val url: String,
    val kind: ResourceKind = ResourceKind.ARTICLE,
)

enum class ResourceKind { ARTICLE, BOOK, VIDEO, GUIDE }

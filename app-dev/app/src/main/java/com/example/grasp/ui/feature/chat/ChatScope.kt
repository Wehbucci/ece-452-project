package com.example.grasp.ui.feature.chat

/**
 * What a chat is ABOUT — the one thing that decides both which conversation gets loaded and how
 * much of the user's material the tutor is told about.
 *
 * This is a type rather than a handful of loose id strings because the two used to disagree. The
 * chat id was built from `pathId`/`nodeId`/`blockIndex` in one place and the system instruction
 * was built from the same three values in another, and only the first of them ever looked at the
 * block: tapping a single paragraph opened its own separate conversation but briefed the tutor
 * exactly as if you had tapped the lesson. One scope value now feeds both, so a conversation and
 * the context behind it cannot drift apart again.
 *
 * The cases nest deliberately — [Block] is a narrowing of [Node], [Node] of [Path] — and
 * [ChatPresenter] briefs the tutor tier by tier in that order: the wider tier is always still
 * true, it just isn't what the user is pointing at.
 *
 * @property chatId the key this conversation's history is stored under, in `users/{uid}/chats`.
 */
sealed interface ChatScope {

    val chatId: String

    /** No material in view — the tutor gets only the display context string. */
    data object General : ChatScope {
        override val chatId: String = "general"
    }

    /** The whole Learner roadmap: the tutor sees the tree, not any one lesson. */
    data class Path(val pathId: String) : ChatScope {
        override val chatId: String get() = pathId
    }

    /** One subtopic's lesson. */
    data class Node(val pathId: String, val nodeId: String) : ChatScope {
        override val chatId: String get() = "${pathId}__${nodeId}"
    }

    /**
     * One block WITHIN a lesson — the paragraph the user tapped.
     *
     * Keyed on the block's stable [com.example.grasp.data.model.LessonBlock.id] rather than its
     * position, because position is exactly what editing changes: insert a paragraph above and
     * every conversation below it would otherwise re-point at its neighbour.
     */
    data class Block(val pathId: String, val nodeId: String, val blockId: String) : ChatScope {
        override val chatId: String get() = "${pathId}__${nodeId}__${blockId}"
    }

    /**
     * A whole Tinkerer guide.
     *
     * The `tinker__` prefix is load-bearing: a guide and a roadmap are both addressed by a bare
     * path id, so without it a [Path] chat and a [Guide] chat over the same topic would collide
     * on one conversation.
     */
    data class Guide(val pathId: String) : ChatScope {
        override val chatId: String get() = "tinker__${pathId}"
    }

    /** One step of a Tinkerer guide — the step the user is actually standing on. */
    data class Step(val pathId: String, val stepId: String) : ChatScope {
        override val chatId: String get() = "tinker__${pathId}__${stepId}"
    }

    companion object {

        /**
         * Rebuild a scope from the flat arguments a navigation route can carry.
         *
         * [tinkerer] cannot be inferred from the ids: a Learner roadmap and a Tinkerer guide are
         * both just a path id, and the two tell the tutor completely different things.
         */
        fun of(
            pathId: String = "",
            nodeId: String = "",
            blockId: String = "",
            stepId: String = "",
            tinkerer: Boolean = false,
        ): ChatScope = when {
            pathId.isEmpty() -> General
            tinkerer && stepId.isNotEmpty() -> Step(pathId, stepId)
            tinkerer -> Guide(pathId)
            nodeId.isEmpty() -> Path(pathId)
            blockId.isEmpty() -> Node(pathId, nodeId)
            else -> Block(pathId, nodeId, blockId)
        }
    }
}

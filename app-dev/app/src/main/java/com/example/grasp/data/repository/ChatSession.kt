package com.example.grasp.data.repository

import com.example.grasp.core.edit.ToolCall
import kotlinx.coroutines.flow.Flow

/**
 * One piece of a reply as it arrives.
 *
 * A reply is not only words any more: the tutor can also ask to change the material it is talking
 * about (FR5.4). Both travel down the same stream because they belong to one answer — "I'd tighten
 * that paragraph, like this" is a sentence and a proposal, and splitting them into two channels
 * would let the UI show either one without the other.
 */
sealed interface ChatChunk {

    /** More of the answer's text. */
    data class Text(val text: String) : ChatChunk

    /**
     * A change the tutor wants to make.
     *
     * Nothing has happened yet — this is the model asking, and it is refused, altered or applied
     * by the layers above. See [com.example.grasp.core.edit.proposeLessonEdits].
     */
    data class Call(val call: ToolCall) : ChatChunk

    /** A failure in the AI round-trip, surfaced for the UI to explain. */
    data class Error(val message: String) : ChatChunk
}

/**
 * One open conversation with the model.
 *
 * An interface so the presenter's failure handling can be tested: a network error is the one path
 * users are guaranteed to hit and the one that cannot be reproduced against the real backend on
 * demand. It is now also how a proposal is tested end to end without a model that has to be
 * persuaded to call a tool. [GeminiChatSession] is the only production implementation.
 */
interface ChatSession {

    /** Stream the reply to [userText] chunk by chunk. Throws if the call fails. */
    fun sendMessageStream(userText: String): Flow<ChatChunk>

    /**
     * Tell the model what became of the change it proposed as [callId] — accepted, or rejected.
     *
     * Sent with the user's NEXT message rather than immediately, for two reasons. The model's
     * turn is left open until something answers its call, and answering it straight away would
     * spend a whole round trip on a reply nobody asked for. And a conversation where the tutor
     * knows its last suggestion was turned down is a better one: it stops re-offering the change
     * the user has already said no to.
     *
     * Doing nothing is a valid implementation — a session that never proposes anything has
     * nothing to settle.
     */
    fun settle(callId: String, outcome: String) = Unit
}

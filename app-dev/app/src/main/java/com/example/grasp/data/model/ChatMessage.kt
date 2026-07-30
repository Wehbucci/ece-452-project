package com.example.grasp.data.model

import com.example.grasp.core.edit.EditProposal

/**
 * One message in the multi-modal AI chat. "Multi-modal" = a message can
 * carry text and/or an image.
 *
 * @property id stable id for list keys
 * @property author who sent it
 * @property text message body (may be empty if it's an image-only message)
 * @property imageUri optional local/remote image attached to the message; null = text only
 * @property pending true while an assistant reply is still streaming/loading (drives the
 *           typing indicator). UI only — not persisted.
 * @property failed the reply never arrived, and [text] explains that rather than answering. Offers
 *           the user a retry. UI only, and never persisted — a failure is not part of the
 *           conversation, so reopening the chat should not show it again.
 * @property proposal changes the assistant offered to make alongside these words (FR5.4), shown
 *           under the message as before/after cards. UI only and NOT persisted: an offer nobody
 *           answered has gone stale by the time the chat is reopened — the lesson may have moved
 *           on — and re-showing it would invite accepting a change written against a lesson that
 *           no longer looks like that.
 * @property proposalOutcome what the user decided, once they have. Null while [proposal] is still
 *           waiting on them.
 */
data class ChatMessage(
    val id: String,
    val author: Author,
    val text: String,
    val imageUri: String? = null,
    val pending: Boolean = false,
    val failed: Boolean = false,
    val proposal: EditProposal? = null,
    val proposalOutcome: ProposalOutcome? = null,
) {
    enum class Author { USER, ASSISTANT }
}

/** What happened to a set of changes the assistant proposed. */
enum class ProposalOutcome {
    /** The user accepted, and the material now carries the changes. */
    ACCEPTED,

    /** The user said no. Nothing was written, and nothing is kept. */
    REJECTED,

    /**
     * The user accepted but the changes no longer fit the material.
     *
     * Its own outcome rather than a silent failure: the proposal was checked against the lesson as
     * it stood when it was made, so getting here means the lesson changed underneath it — and a
     * user who tapped Accept must not be left believing something happened.
     */
    FAILED,
}

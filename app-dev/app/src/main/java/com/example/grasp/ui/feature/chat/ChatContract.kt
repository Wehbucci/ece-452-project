package com.example.grasp.ui.feature.chat

import com.example.grasp.core.mvp.MvpPresenter
import com.example.grasp.core.mvp.MvpView
import com.example.grasp.data.model.ChatMessage

/**
 * MVP contract for the multi-modal AI chat.
 *
 * "Multi-modal" = the user can send text and/or a photo. For the skeleton the
 * assistant replies with a canned message; the real version routes to the AI provider via a
 * backend proxy so the API key never touches the client, using coroutines.
 */
interface ChatContract {

    interface View : MvpView {
        /** Render (or re-render) the transcript and the composer state. */
        fun showMessages(
            messages: List<ChatMessage>,
            isSending: Boolean = false,
            isCircuitBroken: Boolean = false,
        )
    }

    interface Presenter : MvpPresenter<View> {
        /** Send a text message. */
        fun onSend(text: String)

        /**
         * Ask again after a failed reply. [messageId] is the failed assistant message, which is
         * reused rather than replaced — the user asked once and should see one answer.
         */
        fun onRetry(messageId: String)

        /**
         * Make the changes the assistant offered under [messageId] (FR5.4).
         *
         * The ONLY way anything the assistant proposes reaches the user's material. There is no
         * path that applies a change without a tap that means this.
         */
        fun onAcceptProposal(messageId: String)

        /** Turn them down. Nothing is written, and the offer does not come back. */
        fun onRejectProposal(messageId: String)

        /** Attach a photo (multi-modal). Placeholder in the skeleton. */
        fun onAttachImage()
    }
}

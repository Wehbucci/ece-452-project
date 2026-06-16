package com.example.grasp.ui.feature.chat

import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.ChatMessage
import com.example.grasp.data.repository.FakePathRepository
import com.example.grasp.data.repository.PathRepository

/**
 * Logic for the AI chat screen. Seeds the conversation with sample history and echoes a
 * canned assistant reply when the user sends a message.
 *
 * @param context what we're chatting about (subtopic title or a highlighted block); shown in
 *        the header and would be sent to the AI as grounding context.
 */
class ChatPresenter(
    private val context: String,
    private val repo: PathRepository = FakePathRepository,
) : BasePresenter<ChatContract.View>(), ChatContract.Presenter {

    // Mutable working copy of the conversation.
    private val messages = mutableListOf<ChatMessage>()
    private var nextId = 0

    override fun onViewAttached() {
        messages.clear()
        messages.addAll(repo.sampleChat())
        nextId = messages.size
        view?.showMessages(messages.toList())
    }

    override fun onSend(text: String) {
        if (text.isBlank()) return
        messages += ChatMessage("local-${nextId++}", ChatMessage.Author.USER, text.trim())
        // TODO(ai): replace the canned reply with a coroutine call to the AI proxy.
        messages += ChatMessage(
            id = "local-${nextId++}",
            author = ChatMessage.Author.ASSISTANT,
            text = "(Demo) Here's where the AI's answer about \"$context\" will appear.",
        )
        view?.showMessages(messages.toList())
    }

    override fun onAttachImage() {
        // Placeholder: in the real app this opens the photo picker and attaches the image.
        messages += ChatMessage(
            id = "local-${nextId++}",
            author = ChatMessage.Author.USER,
            text = "",
            imageUri = "sample://attached-photo",
        )
        view?.showMessages(messages.toList())
    }
}

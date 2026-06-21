package com.example.grasp.ui.feature.chat

import android.util.Log
import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.ChatMessage
import com.example.grasp.data.repository.ChatRepository
import com.example.grasp.data.repository.FakePathRepository
import com.example.grasp.data.repository.FirebaseChatRepository
import com.example.grasp.data.repository.GeminiChatSession
import com.example.grasp.data.repository.PathRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ChatPresenter(
    private val context: String,
    private val pathId: String = "",
    private val nodeId: String = "",
    private val blockIndex: Int = -1,
    private val repo: PathRepository = FakePathRepository,
    private val chatRepo: ChatRepository = FirebaseChatRepository(),
) : BasePresenter<ChatContract.View>(), ChatContract.Presenter {

    private val chatId: String = when {
        pathId.isNotEmpty() && nodeId.isNotEmpty() && blockIndex >= 0 -> "${pathId}__${nodeId}__${blockIndex}"
        pathId.isNotEmpty() && nodeId.isNotEmpty() -> "${pathId}__${nodeId}"
        pathId.isNotEmpty() -> "tinker__${pathId}"
        else -> "general"
    }

    private val messages = mutableListOf<ChatMessage>()
    private var nextId = 0
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val gemini by lazy { GeminiChatSession(buildSystemInstruction()) }

    override fun onViewAttached() {
        view?.showMessages(messages.toList())
        scope.launch {
            val saved = chatRepo.loadMessages(chatId)
            if (saved.isNotEmpty()) {
                messages.clear()
                messages.addAll(saved)
                nextId = messages.size
                view?.showMessages(messages.toList())
            }
        }
    }

    override fun detach() {
        scope.cancel()
        super.detach()
    }

    override fun onSend(text: String) {
        if (text.isBlank()) return

        val userMessage = ChatMessage("msg-${nextId++}", ChatMessage.Author.USER, text.trim())
        messages += userMessage

        val pendingId = "msg-${nextId++}"
        messages += ChatMessage(pendingId, ChatMessage.Author.ASSISTANT, "", pending = true)
        view?.showMessages(messages.toList())

        scope.launch {
            chatRepo.saveMessage(chatId, context, userMessage)
        }

        scope.launch {
            var accumulated = ""
            try {
                gemini.sendMessageStream(text.trim()).collect { chunk ->
                    accumulated += chunk
                    updatePending(pendingId, accumulated, stillPending = true)
                }
                updatePending(pendingId, accumulated, stillPending = false)
                val assistantMessage = ChatMessage(pendingId, ChatMessage.Author.ASSISTANT, accumulated)
                chatRepo.saveMessage(chatId, context, assistantMessage)
            } catch (e: Exception) {
                Log.e("ChatPresenter", "Gemini call failed", e)
                updatePending(
                    pendingId,
                    text = "Error: ${e.javaClass.simpleName}: ${e.message}",
                    stillPending = false,
                )
            }
        }
    }

    override fun onAttachImage() {
        messages += ChatMessage(
            id = "msg-${nextId++}",
            author = ChatMessage.Author.USER,
            text = "",
            imageUri = "sample://attached-photo",
        )
        view?.showMessages(messages.toList())
    }

    private fun updatePending(id: String, text: String, stillPending: Boolean) {
        val idx = messages.indexOfFirst { it.id == id }
        if (idx >= 0) {
            messages[idx] = messages[idx].copy(text = text, pending = stillPending)
            view?.showMessages(messages.toList())
        }
    }

    private fun buildSystemInstruction(): String = buildString {
        appendLine("You are a helpful AI tutor in the Grasp learning app.")
        appendLine("Be concise, clear, and encouraging.")
        appendLine("If the user shares an image, describe what you see and relate it to the topic.")
        appendLine()

        val subtopic = if (pathId.isNotEmpty() && nodeId.isNotEmpty()) {
            repo.subtopic(pathId, nodeId)
        } else null

        if (subtopic != null) {
            appendLine("The user is studying: \"${subtopic.title}\"")
            appendLine()
            appendLine("Summary: ${subtopic.summary}")
            appendLine()
            appendLine("Why it matters: ${subtopic.whyItMatters}")
            appendLine()
            appendLine("Content:")
            subtopic.body.forEach { paragraph -> appendLine(paragraph) }
        } else if (pathId.isNotEmpty()) {
            val guide = repo.tinkerGuide(pathId)
            if (guide != null) {
                appendLine("The user is working on the task: \"${guide.title}\"")
                appendLine()
                appendLine("Steps:")
                guide.steps.forEach { step ->
                    append("${step.order}. ${step.instruction}")
                    if (step.detail.isNotBlank()) append(" (${step.detail})")
                    appendLine()
                }
            } else {
                appendLine("The user is studying: $context")
            }
        } else {
            appendLine("The user is studying: $context")
        }
    }
}

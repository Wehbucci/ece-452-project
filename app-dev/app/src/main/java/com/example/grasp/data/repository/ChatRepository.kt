package com.example.grasp.data.repository

import com.example.grasp.data.model.ChatMessage

interface ChatRepository {
    /** Load persisted messages for this chat, oldest-first. Returns empty list if none. */
    suspend fun loadMessages(chatId: String): List<ChatMessage>

    /** Persist one message. Also upserts the parent chat doc with [context] and a timestamp. */
    suspend fun saveMessage(chatId: String, context: String, message: ChatMessage)

    /**
     * Returns all chat IDs that start with [prefix]. Used by subtopic/tinker screens to
     * discover which sections already have history without loading full message lists.
     */
    suspend fun existingChatIds(prefix: String): Set<String>
}

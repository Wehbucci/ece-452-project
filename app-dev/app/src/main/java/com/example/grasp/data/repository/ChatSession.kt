package com.example.grasp.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * One open conversation with the model.
 *
 * An interface so the presenter's failure handling can be tested: a network error is the one path
 * users are guaranteed to hit and the one that cannot be reproduced against the real backend on
 * demand. [GeminiChatSession] is the only production implementation.
 */
interface ChatSession {
    /** Stream the reply to [userText] chunk by chunk. Throws if the call fails. */
    fun sendMessageStream(userText: String): Flow<String>
}

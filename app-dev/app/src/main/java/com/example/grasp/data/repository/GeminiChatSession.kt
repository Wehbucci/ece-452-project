package com.example.grasp.data.repository

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn

class GeminiChatSession(systemInstruction: String) {

    private val model = Firebase.ai(backend = GenerativeBackend.googleAI())
        .generativeModel(
            modelName = "gemini-3.1-flash-lite",
            systemInstruction = content { text(systemInstruction) },
        )

    private val chat = model.startChat()

    fun sendMessageStream(userText: String): Flow<String> = channelFlow {
        chat.sendMessageStream(userText).collect { chunk ->
            val text = chunk.text
            if (!text.isNullOrEmpty()) send(text)
        }
    }.flowOn(Dispatchers.IO)
}

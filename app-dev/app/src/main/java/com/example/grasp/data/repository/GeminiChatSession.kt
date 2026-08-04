package com.example.grasp.data.repository

import com.example.grasp.core.edit.ToolCall
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GeminiChatSession(
    systemInstruction: String,
    toolset: TutorToolset = TutorToolset.NONE,
) : ChatSession {

    private val model = Firebase.ai(
        backend = GenerativeBackend.vertexAI(location = "global")
    )
        .generativeModel(
            modelName = "gemini-3.1-flash-lite",
            tools = tutorTools(toolset),
            systemInstruction = content { text(systemInstruction) },
        )

    private val chat = model.startChat()

    /**
     * The calls the model has made, by the id this class handed out for them.
     *
     * Ours rather than the model's because [FunctionCallPart.id] is optional and the Gemini
     * backend leaves it null — and the whole accept/reject flow needs to be able to name one
     * proposal out of several.
     */
    private val proposed = mutableMapOf<String, FunctionCallPart>()

    /** What became of them, waiting to travel with the next thing the user says. */
    private val outcomes = mutableMapOf<String, String>()

    private var callCount = 0

    override fun sendMessageStream(userText: String): Flow<ChatChunk> = channelFlow {
        chat.sendMessageStream(nextMessage(userText)).collect { chunk ->
            chunk.text?.let { if (it.isNotEmpty()) send(ChatChunk.Text(it)) }
            chunk.functionCalls.forEach { part ->
                val id = "call-${callCount++}"
                proposed[id] = part
                send(ChatChunk.Call(ToolCall(id, part.name, part.args.readable())))
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun settle(callId: String, outcome: String) {
        if (callId in proposed) outcomes[callId] = outcome
    }

    /**
     * What the user says, preceded by the answers to anything the model is still waiting on.
     *
     * A function call the conversation never answers leaves the model's turn hanging, and the
     * history it is re-sent every turn slowly fills with unanswered asks. Settling them here costs
     * nothing extra: they ride along with a message that was being sent anyway.
     */
    private fun nextMessage(userText: String): Content = content("user") {
        outcomes.forEach { (id, outcome) ->
            val call = proposed[id] ?: return@forEach
            part(
                FunctionResponsePart(
                    name = call.name,
                    response = buildJsonObject { put("outcome", outcome) },
                    id = call.id,
                ),
            )
        }
        outcomes.keys.forEach(proposed::remove)
        outcomes.clear()
        text(userText)
    }
}

/**
 * Arguments as plain strings.
 *
 * Everything is flattened to text because the far side has to re-check every value anyway — a
 * model can put `"twenty"` where a number was declared — so there is nothing to be gained by
 * carrying the model's idea of the type any further than this.
 */
private fun Map<String, JsonElement>.readable(): Map<String, String> = buildMap {
    this@readable.forEach { (name, value) ->
        // A null argument is the model declining to give an optional one, not the string "null".
        if (value == JsonNull) return@forEach
        put(name, if (value is JsonPrimitive) value.content else value.toString())
    }
}

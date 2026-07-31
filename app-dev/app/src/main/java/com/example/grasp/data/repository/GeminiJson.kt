package com.example.grasp.data.repository

import android.util.Log
import org.json.JSONObject

/**
 * Shared plumbing for the "ask Gemini for a JSON document" generators — [buildLearnerTree],
 * [generateSubtopicContent], [buildBranch] and [suggestBranchTopics].
 *
 * They all have the same shape: send one prompt, collect the streamed answer, parse it as JSON.
 * Failures are deliberately swallowed into `null`: every caller degrades to a usable fallback
 * instead of crashing, which is what NFR 3.1 (graceful failure) asks for.
 */
private const val TAG = "GeminiJson"

/** Sends [prompt] and returns the parsed JSON answer, or null if the call or the parse failed. */
internal suspend fun geminiJson(systemInstruction: String, prompt: String): JSONObject? = try {
    val answer = StringBuilder()
    GeminiChatSession(systemInstruction).sendMessageStream(prompt).collect { chunk ->
        if (chunk is ChatChunk.Text) answer.append(chunk.text)
    }
    extractJsonObject(answer.toString())
} catch (e: Exception) {
    Log.e(TAG, "Gemini request failed", e)
    null
}

/**
 * Pulls the JSON object out of a model answer, tolerating ```json fences or a stray sentence
 * wrapped around it. Returns null when there is nothing parseable.
 */
internal fun extractJsonObject(raw: String): JSONObject? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return try {
        JSONObject(raw.substring(start, end + 1))
    } catch (e: Exception) {
        Log.e(TAG, "Model returned unparseable JSON", e)
        null
    }
}

/** Value of [key] as a list of non-blank strings; missing or wrong-typed keys give an empty list. */
internal fun JSONObject.stringList(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optString(it, "").trim().ifBlank { null } }
}

/** Value of [key] as a list of JSON objects, skipping entries that aren't objects. */
internal fun JSONObject.objectList(key: String): List<JSONObject> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

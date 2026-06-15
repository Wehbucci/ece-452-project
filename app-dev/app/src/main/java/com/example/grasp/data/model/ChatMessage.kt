package com.example.grasp.data.model

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
 */
data class ChatMessage(
    val id: String,
    val author: Author,
    val text: String,
    val imageUri: String? = null,
    val pending: Boolean = false,
) {
    enum class Author { USER, ASSISTANT }
}

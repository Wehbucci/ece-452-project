package com.example.grasp.data.repository

import android.util.Log
import com.example.grasp.data.model.ChatMessage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

class FirebaseChatRepository : ChatRepository {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val uid: String? get() = auth.currentUser?.uid

    private fun chatsRef(uid: String) =
        db.collection("users").document(uid).collection("chats")

    override suspend fun loadMessages(chatId: String): List<ChatMessage> {
        val uid = uid ?: return emptyList()
        return try {
            chatsRef(uid).document(chatId)
                .collection("messages")
                .orderBy("timestamp")
                .get()
                .await()
                .documents.mapNotNull { doc ->
                    val id = doc.getString("id") ?: return@mapNotNull null
                    val authorStr = doc.getString("author") ?: return@mapNotNull null
                    val text = doc.getString("text") ?: ""
                    val imageUri = doc.getString("imageUri")
                    val author = ChatMessage.Author.entries.firstOrNull { it.name == authorStr }
                        ?: return@mapNotNull null
                    ChatMessage(id, author, text, imageUri)
                }
        } catch (e: Exception) {
            Log.e("FirebaseChatRepo", "loadMessages failed for $chatId", e)
            emptyList()
        }
    }

    override suspend fun saveMessage(chatId: String, context: String, message: ChatMessage) {
        val uid = uid ?: return
        if (message.text.isBlank() && message.imageUri == null) return
        try {
            val chatDoc = chatsRef(uid).document(chatId)
            chatDoc.set(
                mapOf("context" to context, "lastUpdated" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            ).await()
            chatDoc.collection("messages").document(message.id).set(
                mapOf(
                    "id" to message.id,
                    "author" to message.author.name,
                    "text" to message.text,
                    "imageUri" to message.imageUri,
                    "timestamp" to FieldValue.serverTimestamp(),
                ),
            ).await()
        } catch (e: Exception) {
            Log.e("FirebaseChatRepo", "saveMessage failed", e)
        }
    }

    override suspend fun existingChatIds(prefix: String): Set<String> {
        val uid = uid ?: return emptySet()
        return try {
            chatsRef(uid).get().await()
                .documents.map { it.id }
                .filter { it.startsWith(prefix) }
                .toSet()
        } catch (e: Exception) {
            Log.e("FirebaseChatRepo", "existingChatIds failed for prefix=$prefix", e)
            emptySet()
        }
    }
}

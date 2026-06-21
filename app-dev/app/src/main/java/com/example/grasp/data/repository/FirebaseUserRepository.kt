package com.example.grasp.data.repository

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

class FirebaseUserRepository : UserRepository {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val uid get() = auth.currentUser?.uid

    override suspend fun getSkillLevel(): String {
        val uid = uid ?: return DEFAULT
        return try {
            db.collection("users").document(uid).get().await()
                .getString("skillLevel") ?: DEFAULT
        } catch (e: Exception) {
            Log.e("FirebaseUserRepo", "getSkillLevel failed", e)
            DEFAULT
        }
    }

    override suspend fun setSkillLevel(level: String) {
        val uid = uid ?: return
        try {
            db.collection("users").document(uid)
                .set(mapOf("skillLevel" to level), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("FirebaseUserRepo", "setSkillLevel failed", e)
        }
    }

    companion object {
        const val DEFAULT = "beginner"
    }
}

package com.example.grasp.data.repository

import android.util.Log
import com.example.grasp.data.model.Pace
import com.example.grasp.data.model.Style
import com.example.grasp.data.model.Tone
import com.example.grasp.data.model.UserPreferences
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

class FirebaseUserRepository : UserRepository {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val uid get() = auth.currentUser?.uid

    override suspend fun getPreferences(): UserPreferences {
        val uid = uid ?: return UserPreferences()
        return try {
            val doc = db.collection("users").document(uid).get().await()
            val prefsMap = doc.get("preferences") as? Map<String, String> ?: return UserPreferences()
            
            UserPreferences(
                pace = Pace.entries.find { it.name == prefsMap["pace"] } ?: Pace.STANDARD,
                style = Style.entries.find { it.name == prefsMap["style"] } ?: Style.BALANCED,
                tone = Tone.entries.find { it.name == prefsMap["tone"] } ?: Tone.PROFESSIONAL
            )
        } catch (e: Exception) {
            Log.e("FirebaseUserRepo", "getPreferences failed", e)
            UserPreferences()
        }
    }

    override suspend fun setPreferences(prefs: UserPreferences) {
        val uid = uid ?: return
        try {
            val prefsMap = mapOf(
                "pace" to prefs.pace.name,
                "style" to prefs.style.name,
                "tone" to prefs.tone.name
            )
            db.collection("users").document(uid)
                .set(mapOf("preferences" to prefsMap), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("FirebaseUserRepo", "setPreferences failed", e)
        }
    }
}

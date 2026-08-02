package com.example.grasp.data.repository

import android.util.Log
import com.example.grasp.core.progress.StudyStreak
import com.example.grasp.core.progress.recordingStudy
import com.example.grasp.core.progress.todayEpochDay
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

    override suspend fun getUsername(): String? {
        val uid = uid ?: return null
        return try {
            db.collection("users").document(uid).get().await().getString("username")
        } catch (e: Exception) {
            Log.e("FirebaseUserRepo", "getUsername failed", e)
            null
        }
    }

    override suspend fun setUsername(username: String) {
        val uid = uid ?: return
        try {
            db.collection("users").document(uid)
                .set(mapOf("username" to username), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("FirebaseUserRepo", "setUsername failed", e)
        }
    }

    override suspend fun studyStreak(): StudyStreak {
        val uid = uid ?: return StudyStreak.None
        return try {
            val doc = db.collection("users").document(uid).get().await()
            val days = doc.getLong(FIELD_STREAK_DAYS)?.toInt() ?: return StudyStreak.None
            StudyStreak(days = days, lastStudyDay = doc.getLong(FIELD_LAST_STUDY_DAY) ?: 0L)
        } catch (e: Exception) {
            Log.e("FirebaseUserRepo", "studyStreak failed", e)
            // A streak we couldn't read is not a streak we should claim is broken — but it isn't
            // one we can count either. Zero here only affects this read; nothing is written.
            StudyStreak.None
        }
    }

    /**
     * Read-modify-write, because extending a streak depends on what it already was.
     *
     * Not a transaction: the only writer is the signed-in user finishing a lesson, and two devices
     * doing that on the same day both compute the same answer ([recordingStudy] is idempotent
     * within a day). The cost of losing that race is a streak that stays correct anyway.
     */
    override suspend fun recordStudyToday(): StudyStreak {
        val today = todayEpochDay()
        val uid = uid ?: return StudyStreak.None.recordingStudy(today)
        val updated = studyStreak().recordingStudy(today)
        try {
            db.collection("users").document(uid)
                .set(
                    mapOf(
                        FIELD_STREAK_DAYS to updated.days,
                        FIELD_LAST_STUDY_DAY to updated.lastStudyDay,
                    ),
                    SetOptions.merge(),
                )
                .await()
        } catch (e: Exception) {
            // The lesson is still complete; only the flame is behind. It catches up the next time
            // something is finished, since the stored day is what the next call reads.
            Log.e("FirebaseUserRepo", "recordStudyToday failed", e)
        }
        return updated
    }

    private companion object {
        const val FIELD_STREAK_DAYS = "streakDays"
        const val FIELD_LAST_STUDY_DAY = "lastStudyDay"
    }
}

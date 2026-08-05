package com.example.grasp.data.repository

import android.util.Log
import com.example.grasp.core.progress.StudyStreak
import com.example.grasp.core.progress.recordingStudy
import com.example.grasp.core.progress.todayEpochDay
import com.example.grasp.data.model.Pace
import com.example.grasp.data.model.Style
import com.example.grasp.data.model.Tone
import com.example.grasp.data.model.UserPreferences
import com.example.grasp.GraspApp
import com.example.grasp.core.util.NetworkMonitor
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class FirebaseUserRepository : UserRepository {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val uid get() = auth.currentUser?.uid

    private val networkMonitor = NetworkMonitor(GraspApp.context)

    /**
     * The signed-in user's document, read the way the rest of the app reads Firestore.
     *
     * Every accessor here used to call a bare `get()`: no [Source], no timeout, and no regard for
     * whether there was a network at all. On a connection that is up but not answering — a captive
     * portal, a blocked backend — that is an unbounded wait, and these are called while a screen
     * is being drawn. Cache-only when offline, briefly bounded when online, and the cached copy on
     * the way out: for a streak or a set of preferences, what the user last saw is the right answer
     * when the server has nothing to add.
     */
    private suspend fun userDoc(): DocumentSnapshot? {
        val uid = uid ?: return null
        val ref = db.collection("users").document(uid)
        return try {
            if (networkMonitor.isOnline()) {
                withTimeout(USER_DOC_TIMEOUT_MS) { ref.get(Source.DEFAULT).await() }
            } else {
                ref.get(Source.CACHE).await()
            }
        } catch (e: Exception) {
            try {
                ref.get(Source.CACHE).await()
            } catch (cacheEx: Exception) {
                Log.e("FirebaseUserRepo", "user document unreadable", cacheEx)
                null
            }
        }
    }

    override suspend fun getPreferences(): UserPreferences {
        return try {
            val doc = userDoc() ?: return UserPreferences()
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

    override suspend fun getUsername(): String? = try {
        userDoc()?.getString("username")
    } catch (e: Exception) {
        Log.e("FirebaseUserRepo", "getUsername failed", e)
        null
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
        return try {
            val doc = userDoc() ?: return StudyStreak.None
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

        /**
         * How long a server read of the user document may hold a screen up.
         *
         * Matched to the roadmap reads in `FirebasePathRepository`, and for the same reason: past
         * this the cached copy is a better answer than a longer wait.
         */
        const val USER_DOC_TIMEOUT_MS = 1500L
    }
}

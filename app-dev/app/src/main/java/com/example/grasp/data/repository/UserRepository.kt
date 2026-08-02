package com.example.grasp.data.repository

import com.example.grasp.core.progress.StudyStreak
import com.example.grasp.data.model.UserPreferences

interface UserRepository {
    suspend fun getPreferences(): UserPreferences
    suspend fun setPreferences(prefs: UserPreferences)
    suspend fun getUsername(): String?
    suspend fun setUsername(username: String)

    /**
     * The stored streak, exactly as it was last written.
     *
     * Raw on purpose: whether it is still alive is a question about today, which the caller answers
     * with [com.example.grasp.core.progress.asOf]. Keeping the record intact rather than resetting
     * it on read is also what would let a "longest streak" be shown later.
     */
    suspend fun studyStreak(): StudyStreak = StudyStreak.None

    /**
     * Counts today as a day studied and returns the streak as it now stands.
     *
     * Idempotent within a day: the second lesson someone finishes on a Tuesday is not a second
     * Tuesday, so calling this repeatedly cannot inflate the number.
     */
    suspend fun recordStudyToday(): StudyStreak = StudyStreak.None
}

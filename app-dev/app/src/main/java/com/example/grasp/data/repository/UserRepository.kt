package com.example.grasp.data.repository

import com.example.grasp.data.model.UserPreferences

interface UserRepository {
    suspend fun getPreferences(): UserPreferences
    suspend fun setPreferences(prefs: UserPreferences)
    suspend fun getUsername(): String?
    suspend fun setUsername(username: String)
}

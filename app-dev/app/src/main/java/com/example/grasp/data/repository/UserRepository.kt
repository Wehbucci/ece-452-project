package com.example.grasp.data.repository

interface UserRepository {
    suspend fun getSkillLevel(): String
    suspend fun setSkillLevel(level: String)
}

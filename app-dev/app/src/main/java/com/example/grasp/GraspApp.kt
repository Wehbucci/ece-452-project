package com.example.grasp

import android.app.Application
import android.content.Context

/**
 * Global application class to provide static access to the application context
 * for repository-level network monitoring.
 */
class GraspApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private lateinit var instance: GraspApp
        val context: Context get() = instance.applicationContext
    }
}

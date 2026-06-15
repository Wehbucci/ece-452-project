package com.example.grasp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.grasp.ui.navigation.GraspApp
import com.example.grasp.ui.theme.GraspTheme

/**
 * The single Activity that hosts the entire Compose UI (single-activity architecture).
 *
 * Responsibilities are intentionally tiny: apply the theme and launch the navigation graph.
 * All screens, logic and data live under `ui/`, `core/` and `data/` following the MVP layering. 
 * Start exploring from [com.example.grasp.ui.navigation.GraspNavHost].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GraspTheme {
                GraspApp()
            }
        }
    }
}

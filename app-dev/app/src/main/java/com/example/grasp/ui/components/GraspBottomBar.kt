package com.example.grasp.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.grasp.ui.navigation.TopLevelDestination

/**
 * The bottom navigation bar shared by the three top-level screens (Home / Library / Profile).
 *
 * This is a DUMB component (pure MVP View element): it knows nothing about the NavController.
 * It just shows which tab is [selected] and reports taps via [onSelect]. The actual route
 * change is performed by the navigation layer (see GraspNavHost).
 *
 * @param selected the destination for the screen currently hosting this bar
 * @param onSelect invoked with the tab the user tapped
 */
@Composable
fun GraspBottomBar(
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}

/** Bottom-bar icon for each tab. Kept here so the destination enum stays UI-framework-free. */
private val TopLevelDestination.icon: ImageVector
    get() = when (this) {
        TopLevelDestination.HOME -> Icons.Filled.Home
        TopLevelDestination.LIBRARY -> Icons.AutoMirrored.Filled.List
        TopLevelDestination.PROFILE -> Icons.Filled.Person
    }

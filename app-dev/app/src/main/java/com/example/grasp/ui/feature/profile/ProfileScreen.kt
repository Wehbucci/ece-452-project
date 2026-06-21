@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.grasp.ui.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.grasp.ui.components.GraspBottomBar
import com.example.grasp.ui.navigation.TopLevelDestination

@Composable
fun ProfileScreen(
    onSelectTab: (TopLevelDestination) -> Unit,
    navigateToLogin: () -> Unit,
    presenterFactory: () -> ProfileContract.Presenter = { ProfilePresenter() },
) {
    var userName by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }

    val presenter = remember { presenterFactory() }
    val view = remember(navigateToLogin) {
        object : ProfileContract.View {
            override fun showProfile(name: String, email: String) { userName = name; userEmail = email }
            override fun onLoggedOut() = navigateToLogin()
        }
    }
    DisposableEffect(presenter, view) {
        presenter.attach(view)
        onDispose { presenter.detach() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Profile") }) },
        bottomBar = { GraspBottomBar(selected = TopLevelDestination.PROFILE, onSelect = onSelectTab) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = userName.take(1).uppercase().ifBlank { "?" },
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Text(userName.ifBlank { "—" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(userEmail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.size(28.dp))

            SettingRow("Learning preferences") { /* TODO: difficulty / length / format */ }
            SettingRow("Offline content") { /* TODO: manage cached paths */ }
            SettingRow("Notifications") { /* TODO */ }
            SettingRow("About Grasp") { /* TODO */ }

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = presenter::onLogout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Log out")
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

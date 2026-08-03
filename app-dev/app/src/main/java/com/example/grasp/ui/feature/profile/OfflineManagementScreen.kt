@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.grasp.ui.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.data.model.SavedItem
import com.example.grasp.ui.components.GameCard
import com.example.grasp.ui.components.GameRowDivider
import com.example.grasp.ui.components.GameSectionHeader
import com.example.grasp.ui.components.PathToast
import com.example.grasp.ui.theme.GameDanger
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathScreenBg
import java.util.Locale

@Composable
fun OfflineManagementScreen(
    onBack: () -> Unit,
    presenterFactory: () -> OfflineManagementContract.Presenter = { OfflineManagementPresenter() },
) {
    var storageBytes by remember { mutableLongStateOf(0L) }
    var useMobileData by remember { mutableStateOf(false) }
    var activeSyncs by remember { mutableStateOf<List<SavedItem>>(emptyList()) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    val presenter = remember { presenterFactory() }
    val view = remember {
        object : OfflineManagementContract.View {
            override fun showStorageUsage(bytes: Long) { storageBytes = bytes }
            override fun showMobileDataAllowed(enabled: Boolean) { useMobileData = enabled }
            override fun showActiveSyncs(items: List<SavedItem>) { activeSyncs = items }
            override fun showToast(message: String) { toastMessage = message }
        }
    }

    DisposableEffect(presenter, view) {
        presenter.attach(view)
        onDispose { presenter.detach() }
    }

    Scaffold(
        containerColor = PathScreenBg,
        topBar = {
            TopAppBar(
                title = { Text("Offline Management", fontFamily = NunitoFamily, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Storage Usage
            GameSectionHeader("Storage")
            GameCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.Start) {
                    Text(
                        text = formatBytes(storageBytes),
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.Black,
                        color = PathNodeCurrent
                    )
                    Text(
                        text = "Used by roadmaps and lessons",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PathMuted
                    )
                    Spacer(Modifier.height(20.dp))
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { presenter.onClearAllClicked() }
                            .padding(vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = GameDanger)
                            Text("Clear All Downloads", color = GameDanger, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            // Sync Settings
            GameSectionHeader("Settings")
            GameCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use mobile data", fontWeight = FontWeight.Bold, color = PathInk)
                        Text("Allows downloads over cellular", style = MaterialTheme.typography.bodySmall, color = PathMuted)
                    }
                    Switch(
                        checked = useMobileData,
                        onCheckedChange = presenter::onMobileDataToggled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PathNodeCurrent,
                            checkedBorderColor = PathNodeCurrent,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = com.example.grasp.ui.theme.PathXpTrack,
                            uncheckedBorderColor = com.example.grasp.ui.theme.PathXpTrack,
                        )
                    )
                }
            }

            // Active Syncs
            if (activeSyncs.isNotEmpty()) {
                GameSectionHeader("Active Downloads")
                GameCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        activeSyncs.forEachIndexed { index, item ->
                            ActiveSyncRow(
                                item = item,
                                onCancel = { presenter.onCancelSyncClicked(item) }
                            )
                            if (index < activeSyncs.lastIndex) GameRowDivider()
                        }
                    }
                }
            }
        }
        
        PathToast(toastMessage, onFinished = { toastMessage = null })
    }
}

@Composable
private fun ActiveSyncRow(item: SavedItem, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = PathNodeCurrent)
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Downloading…", style = MaterialTheme.typography.bodySmall, color = PathMuted)
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = PathMuted)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

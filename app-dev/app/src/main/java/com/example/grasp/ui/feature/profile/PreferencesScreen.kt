@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.grasp.ui.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.unit.sp
import com.example.grasp.data.model.Pace
import com.example.grasp.data.model.Style
import com.example.grasp.data.model.Tone
import com.example.grasp.data.model.UserPreferences
import com.example.grasp.ui.components.GameCard
import com.example.grasp.ui.components.GameSectionHeader
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathScreenBg

@Composable
fun PreferencesScreen(
    onBack: () -> Unit,
    presenterFactory: () -> PreferencesContract.Presenter = { PreferencesPresenter() },
) {
    var prefs by remember { mutableStateOf(UserPreferences()) }

    val presenter = remember { presenterFactory() }
    val view = remember(onBack) {
        object : PreferencesContract.View {
            override fun showPreferences(p: UserPreferences) { prefs = p }
            override fun navigateBack() = onBack()
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
                title = { Text("Learning Preferences", fontFamily = NunitoFamily, fontWeight = FontWeight.ExtraBold) },
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "Tune how our AI teaches you. Every roadmap and lesson will be generated based on these choices.",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = NunitoFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PreferenceSliderSection(
                title = "Learning Pace",
                value = prefs.pace.ordinal.toFloat(),
                onValueChange = { prefs = prefs.copy(pace = Pace.entries[it.toInt()]) },
                label = prefs.pace.label,
                description = prefs.pace.description
            )

            PreferenceSliderSection(
                title = "Content Style",
                value = prefs.style.ordinal.toFloat(),
                onValueChange = { prefs = prefs.copy(style = Style.entries[it.toInt()]) },
                label = prefs.style.label,
                description = prefs.style.description
            )

            PreferenceSliderSection(
                title = "Tutor Tone",
                value = prefs.tone.ordinal.toFloat(),
                onValueChange = { prefs = prefs.copy(tone = Tone.entries[it.toInt()]) },
                label = prefs.tone.label,
                description = prefs.tone.description
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { presenter.onSaveClicked(prefs) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Apply & Save", fontFamily = NunitoFamily, fontWeight = FontWeight.ExtraBold)
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PreferenceSliderSection(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    description: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GameSectionHeader(title)
        GameCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.Black,
                        color = PathNodeCurrent,
                        fontSize = 16.sp
                    )
                }
                
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = 0f..2f,
                    steps = 1,
                    colors = SliderDefaults.colors(
                        thumbColor = PathNodeCurrent,
                        activeTrackColor = PathNodeCurrent,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = NunitoFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

package com.example.grasp.ui.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.data.model.SavedItem
import com.example.grasp.ui.components.GameCard
import com.example.grasp.ui.components.GameEmptyState
import com.example.grasp.ui.components.GameIconTile
import com.example.grasp.ui.components.GameProgressBar
import com.example.grasp.ui.components.GameTag
import com.example.grasp.ui.components.ModeGlyph
import com.example.grasp.ui.components.accent
import com.example.grasp.ui.components.tint
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeDone
import com.example.grasp.ui.theme.PathScreenBg
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineContentScreen(
    onBack: () -> Unit,
    onOpenLearner: (String) -> Unit,
    onOpenTinker: (String) -> Unit,
    presenterFactory: () -> OfflineContract.Presenter = { OfflinePresenter() },
) {
    var savedItems by remember { mutableStateOf<List<SavedItem>>(emptyList()) }
    val presenter = remember { presenterFactory() }
    
    val view = remember(onOpenLearner, onOpenTinker) {
        object : OfflineContract.View {
            override fun showOfflineItems(items: List<SavedItem>) { savedItems = items }
            override fun openLearner(id: String) = onOpenLearner(id)
            override fun openTinker(id: String) = onOpenTinker(id)
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
                title = { Text("Offline Content", fontFamily = NunitoFamily, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (savedItems.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                GameEmptyState(
                    title = "Nothing downloaded yet",
                    message = "Go to your Library and tap the download icon on any topic to save it for offline use.",
                    art = {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(100.dp),
                            tint = PathMuted
                        )
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(savedItems, key = { "${it.mode}-${it.id}" }) { item ->
                    OfflineItemCard(
                        item = item,
                        onClick = { presenter.onItemClicked(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineItemCard(item: SavedItem, onClick: () -> Unit) {
    val percent = (item.progress * 100).roundToInt()
    val finished = item.progress >= 1f
    val barColor = if (finished) PathNodeDone else item.mode.accent

    GameCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameIconTile(tint = item.mode.tint) {
                    ModeGlyph(mode = item.mode, tint = item.mode.accent, size = 24.dp)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                ) {
                    Text(
                        text = item.title,
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        color = PathInk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        GameTag(
                            text = item.mode.label,
                            accent = item.mode.accent,
                            tint = item.mode.tint,
                        )
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Offline ready",
                            tint = PathNodeDone,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            GameProgressBar(fraction = item.progress, accent = barColor)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.subtitle,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = PathMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$percent%",
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = barColor,
                )
            }
        }
    }
}

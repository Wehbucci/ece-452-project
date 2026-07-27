package com.example.grasp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.ui.navigation.TopLevelDestination
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathCard
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathNodeCurrentTint

/**
 * The bottom navigation bar shared by the three top-level screens (Home / Library / Profile).
 *
 * This is a DUMB component (pure MVP View element): it knows nothing about the NavController.
 * It just shows which tab is [selected] and reports taps via [onSelect]. The actual route
 * change is performed by the navigation layer (see GraspNavHost).
 *
 * Styled by hand rather than with `NavigationBar` so it speaks the same language as the rest of
 * the gamified shell: a white card floating over the lilac background, and a selected tab that
 * pops into an indigo pill and lifts its icon. Material's default bar (grey surface tint, wide
 * capsule indicator) reads as a stock Android component next to the roadmap.
 */
@Composable
fun GraspBottomBar(
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
) {
    Surface(
        color = PathCard,
        // Soft lift: the bar is the one place a blurred shadow beats a hard bevel, because it
        // has to separate from whatever content scrolls under it.
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopLevelDestination.entries.forEach { destination ->
                BottomBarTab(
                    destination = destination,
                    selected = destination == selected,
                    onClick = { onSelect(destination) },
                )
            }
        }
    }
}

/** One tab: an icon in a pill that fills when active, with its label underneath. */
@Composable
private fun BottomBarTab(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) PathNodeCurrent else PathMuted,
        animationSpec = tween(200),
        label = "tabTint",
    )
    val pillColor by animateColorAsState(
        targetValue = if (selected) PathNodeCurrentTint else Color.Transparent,
        animationSpec = tween(200),
        label = "tabPill",
    )
    // A small bounce on becoming active — the same "toy" feedback as the roadmap nodes.
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "tabScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(percent = 50))
                .background(pillColor)
                .padding(horizontal = 18.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = destination.label,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = destination.label,
            color = tint,
            fontFamily = NunitoFamily,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
            fontSize = 11.sp,
        )
    }
}

/** Bottom-bar icon for each tab. Kept here so the destination enum stays UI-framework-free. */
private val TopLevelDestination.icon: ImageVector
    get() = when (this) {
        TopLevelDestination.HOME -> Icons.Filled.Home
        TopLevelDestination.LIBRARY -> Icons.AutoMirrored.Filled.List
        TopLevelDestination.PROFILE -> Icons.Filled.Person
    }

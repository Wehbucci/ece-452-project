package com.example.grasp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.grasp.ui.feature.about.AboutScreen
import com.example.grasp.ui.feature.auth.LoginScreen
import com.example.grasp.ui.feature.chat.ChatScope
import com.example.grasp.ui.feature.chat.ChatScreen
import com.example.grasp.ui.feature.home.HomeScreen
import com.example.grasp.ui.feature.library.LibraryScreen
import com.example.grasp.ui.feature.notifications.NotificationsScreen
import com.example.grasp.ui.feature.path.PathScreen
import com.example.grasp.ui.feature.profile.PreferencesScreen
import com.example.grasp.ui.feature.profile.ProfileScreen
import com.example.grasp.ui.feature.subtopic.SubtopicScreen
import com.example.grasp.ui.feature.tinker.TinkerScreen
import com.google.firebase.auth.FirebaseAuth

/**
 * The app's single navigation graph (single-activity architecture). This is the ONLY place
 * that knows how routes connect; screens stay decoupled by receiving plain navigation
 * lambdas (e.g. `onOpenSubtopic`) instead of a NavController.
 *
 * Flow: [GraspDestinations.LOGIN] → the three bottom-nav tabs (Home / Library / Profile) →
 * deep screens (roadmap, guide, subtopic, chat). The bottom bar shows only on the tabs (each
 * tab screen renders it itself); deep screens are full-screen with a back button.
 */
@Composable
fun GraspApp() {
    val navController = rememberNavController()
    // Determine the start destination based on current auth state.
    val startDestination = remember {
        if (FirebaseAuth.getInstance().currentUser != null) {
            GraspDestinations.HOME
        } else {
            GraspDestinations.LOGIN
        }
    }
    GraspNavHost(navController = navController, startDestination = startDestination)
}

@Composable
fun GraspNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        // ---- Auth ----
        composable(GraspDestinations.LOGIN) {
            LoginScreen(
                onAuthenticated = {
                    // Enter the app and drop login from the back stack.
                    navController.navigate(GraspDestinations.HOME) {
                        popUpTo(GraspDestinations.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        // ---- Top-level tabs ----
        composable(GraspDestinations.HOME) {
            HomeScreen(
                onSelectTab = navController::switchTab,
                onOpenLearner = { navController.navigate(GraspDestinations.path(it)) },
                onOpenTinker = { navController.navigate(GraspDestinations.tinker(it)) },
            )
        }
        composable(GraspDestinations.LIBRARY) {
            LibraryScreen(
                onSelectTab = navController::switchTab,
                onOpenLearner = { navController.navigate(GraspDestinations.path(it)) },
                onOpenTinker = { navController.navigate(GraspDestinations.tinker(it)) },
            )
        }
        composable(GraspDestinations.PROFILE) {
            ProfileScreen(
                onSelectTab = navController::switchTab,
                onOpenPreferences = { navController.navigate(GraspDestinations.PREFERENCES) },
                navigateToLogin = {
                    navController.navigate(GraspDestinations.LOGIN) {
                        popUpTo(GraspDestinations.HOME) { inclusive = true }
                    }
                },
                navigateToNotifications = { navController.navigate(GraspDestinations.NOTIFICATIONS) },
                navigateToAbout = { navController.navigate(GraspDestinations.ABOUT) },
            )
        }
        composable(GraspDestinations.PREFERENCES) {
            PreferencesScreen(onBack = navController::popBackStack)
        }

        // ---- Profile sub-screens (full-screen, back returns to the Profile tab) ----
        composable(GraspDestinations.NOTIFICATIONS) {
            NotificationsScreen(onBack = navController::popBackStack)
        }
        composable(GraspDestinations.ABOUT) {
            AboutScreen(onBack = navController::popBackStack)
        }

        // ---- Learner roadmap ----
        composable(
            route = GraspDestinations.PATH,
            arguments = listOf(navArgument(GraspDestinations.ARG_PATH_ID) { type = NavType.StringType }),
        ) { entry ->
            val pathId = entry.arguments?.getString(GraspDestinations.ARG_PATH_ID).orEmpty()
            PathScreen(
                pathId = pathId,
                onBack = navController::popBackStack,
                // The subtopic detail is now an in-path bottom sheet, so the roadmap navigates
                // out only for "Ask AI" → the existing chat feature.
                onOpenChat = { ctx, p, n, blockId ->
                    navController.navigate(
                        GraspDestinations.chat(context = ctx, pathId = p, nodeId = n, blockId = blockId),
                    )
                },
            )
        }

        // ---- Tinkerer guide ----
        composable(
            route = GraspDestinations.TINKER,
            arguments = listOf(navArgument(GraspDestinations.ARG_PATH_ID) { type = NavType.StringType }),
        ) { entry ->
            val guideId = entry.arguments?.getString(GraspDestinations.ARG_PATH_ID).orEmpty()
            TinkerScreen(
                guideId = guideId,
                onBack = navController::popBackStack,
                onOpenChat = { ctx, pathId, stepId ->
                    navController.navigate(
                        GraspDestinations.chat(ctx, pathId, stepId = stepId, tinkerer = true),
                    )
                },
            )
        }

        // ---- Subtopic detail ----
        composable(
            route = GraspDestinations.SUBTOPIC,
            arguments = listOf(
                navArgument(GraspDestinations.ARG_PATH_ID) { type = NavType.StringType },
                navArgument(GraspDestinations.ARG_NODE_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            SubtopicScreen(
                pathId = entry.arguments?.getString(GraspDestinations.ARG_PATH_ID).orEmpty(),
                nodeId = entry.arguments?.getString(GraspDestinations.ARG_NODE_ID).orEmpty(),
                onBack = navController::popBackStack,
                onOpenChat = { ctx, pathId, nodeId, blockId ->
                    navController.navigate(GraspDestinations.chat(ctx, pathId, nodeId, blockId))
                },
            )
        }

        // ---- Multi-modal AI chat ----
        composable(
            route = GraspDestinations.CHAT,
            arguments = listOf(
                navArgument(GraspDestinations.ARG_CONTEXT) {
                    type = NavType.StringType
                    defaultValue = "your material"
                },
                navArgument(GraspDestinations.ARG_PATH_ID) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(GraspDestinations.ARG_NODE_ID) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(GraspDestinations.ARG_BLOCK_ID) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(GraspDestinations.ARG_STEP_ID) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(GraspDestinations.ARG_TINKERER) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            ChatScreen(
                chatContext = entry.arguments?.getString(GraspDestinations.ARG_CONTEXT) ?: "your material",
                scope = ChatScope.of(
                    pathId = entry.arguments?.getString(GraspDestinations.ARG_PATH_ID).orEmpty(),
                    nodeId = entry.arguments?.getString(GraspDestinations.ARG_NODE_ID).orEmpty(),
                    blockId = entry.arguments?.getString(GraspDestinations.ARG_BLOCK_ID).orEmpty(),
                    stepId = entry.arguments?.getString(GraspDestinations.ARG_STEP_ID).orEmpty(),
                    tinkerer = entry.arguments?.getBoolean(GraspDestinations.ARG_TINKERER) ?: false,
                ),
                onBack = navController::popBackStack,
            )
        }
    }
}

/**
 * Switches between bottom-nav tabs with the standard Compose pattern: a single instance per
 * tab, with each tab's UI state saved and restored as the user hops between them. Home stays
 * at the base of the tab stack so the system back button returns there.
 */
private fun NavHostController.switchTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(GraspDestinations.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

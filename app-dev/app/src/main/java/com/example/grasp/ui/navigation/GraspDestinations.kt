package com.example.grasp.ui.navigation

import android.net.Uri

/**
 * Single source of truth for every navigation route in the app.
 *
 * We use Jetpack Navigation for Compose with simple string routes. Routes
 * that take arguments expose BOTH a template (e.g. [PATH], used when registering the
 * destination) and a builder function (e.g. [path]) used by callers to fill in real values.
 * Always use the builder and never hand-concatenate a route string at a call site.
 *
 * Arg keys ([ARG_PATH_ID] etc.) are shared between the template and the code that reads the
 * argument back out, so they can't drift apart.
 */
object GraspDestinations {

    // ---- Standalone destinations ----
    /** Required login (cloud-first model). Start destination. */
    const val LOGIN = "login"

    // ---- Top-level (bottom-nav) destinations ----
    const val HOME = "home"
    const val LIBRARY = "library"
    const val PROFILE = "profile"
    const val PREFERENCES = "preferences"

    // ---- Profile sub-screens (pushed on top of the Profile tab) ----
    /** Notification preferences (Profile → Notifications). */
    const val NOTIFICATIONS = "notifications"

    /** About Grasp — product, team and stack (Profile → About Grasp). */
    const val ABOUT = "about"

    // ---- Argument keys ----
    const val ARG_PATH_ID = "pathId"
    const val ARG_NODE_ID = "nodeId"
    const val ARG_CONTEXT = "context"
    const val ARG_BLOCK_ID = "blockId"
    const val ARG_TINKERER = "tinkerer"

    // ---- Learner: roadmap (list + tree views live behind one route) ----
    const val PATH = "path/{$ARG_PATH_ID}"
    fun path(pathId: String) = "path/$pathId"

    // ---- Tinkerer: step-by-step guide ----
    const val TINKER = "tinker/{$ARG_PATH_ID}"
    fun tinker(pathId: String) = "tinker/$pathId"

    // ---- Subtopic detail ----
    const val SUBTOPIC = "subtopic/{$ARG_PATH_ID}/{$ARG_NODE_ID}"
    fun subtopic(pathId: String, nodeId: String) = "subtopic/$pathId/$nodeId"

    // ---- Multi-modal AI chat. `context` is the display title; the rest identify the chat's scope. ----
    const val CHAT = "chat?$ARG_CONTEXT={$ARG_CONTEXT}&$ARG_PATH_ID={$ARG_PATH_ID}&$ARG_NODE_ID={$ARG_NODE_ID}&$ARG_BLOCK_ID={$ARG_BLOCK_ID}&$ARG_TINKERER={$ARG_TINKERER}"

    /**
     * Every value is percent-encoded on the way in.
     *
     * [context] is a display title taken straight from the user's own material — a lesson title,
     * or the first 60 characters of a paragraph. Concatenated raw it only had to contain a `?`,
     * `&`, `#` or `/` to cut the route in half and lose every argument after it, which is a thing
     * ordinary English prose does constantly.
     */
    fun chat(
        context: String = "your material",
        pathId: String = "",
        nodeId: String = "",
        blockId: String = "",
        tinkerer: Boolean = false,
    ) = "chat?$ARG_CONTEXT=${enc(context)}" +
        "&$ARG_PATH_ID=${enc(pathId)}" +
        "&$ARG_NODE_ID=${enc(nodeId)}" +
        "&$ARG_BLOCK_ID=${enc(blockId)}" +
        "&$ARG_TINKERER=$tinkerer"

    /**
     * An empty argument still has to occupy its slot: Navigation matches this route by shape, and
     * `arg=` with nothing after it is not the same as the argument being absent.
     */
    private fun enc(value: String): String =
        if (value.isEmpty()) "" else Uri.encode(value)
}

/**
 * The three destinations shown in the bottom navigation bar. Keeping them in an enum lets
 * the bottom bar render itself from a list and lets the NavHost know which routes should
 * show the bar.
 *
 * @property route the matching route in [GraspDestinations]
 * @property label the tab label
 */
enum class TopLevelDestination(val route: String, val label: String) {
    HOME(GraspDestinations.HOME, "Home"),
    LIBRARY(GraspDestinations.LIBRARY, "Library"),
    PROFILE(GraspDestinations.PROFILE, "Profile");

    companion object {
        /** Routes that should display the bottom navigation bar. */
        val routes: Set<String> = entries.map { it.route }.toSet()
    }
}

package com.example.grasp.ui.feature.path

import com.example.grasp.core.mvp.MvpPresenter
import com.example.grasp.core.mvp.MvpView
import com.example.grasp.data.model.Subtopic

/**
 * MVP contract for the gamified Learner roadmap (the vertical "journey").
 *
 * The split is strict so the Presenter stays unit-testable:
 *  - The [Presenter] owns ALL logic — current-node derivation, per-node state, XP/level,
 *    marking complete, and branch insertion — and pushes a fully-resolved [PathUiState] to the
 *    View. Transient, one-shot effects (advance, confetti, level-up, toast) are separate View
 *    calls so the Composable can fire an animation exactly once.
 *  - The [View] only renders state and plays those effects; it holds no business logic.
 */
interface PathContract {

    interface View : MvpView {
        /** Render (or re-render) the whole journey: HUD numbers + laid-out nodes + regions. */
        fun showPath(state: PathUiState)

        /** The path id didn't resolve (show an empty/error state, never crash). */
        fun showNotFound()

        /**
         * Open the detail sheet for a node titled [title] while its lesson is fetched, or — when
         * [generating] — written from scratch, which takes far longer and is worth saying.
         * Always followed by [showSubtopicSheet] or [dismissSheet].
         */
        fun showSubtopicLoading(title: String, generating: Boolean)

        /** Open the subtopic detail bottom sheet for [subtopic]; [completed] drives the CTA. */
        fun showSubtopicSheet(subtopic: Subtopic, completed: Boolean)

        /** Open the "grow your path" sheet; [fromTitle] is the lesson the branch will grow off. */
        fun showBranchSheet(fromTitle: String)

        /** Fill the open branch sheet's starter chips (empty = show none). */
        fun showBranchSuggestions(topics: List<String>)

        /** Put the open branch sheet into / out of its "growing your branch…" state. */
        fun showBranchGenerating(generating: Boolean)

        /** Close whichever bottom sheet is open. */
        fun dismissSheet()

        /** The "you are here" marker moved to [nodeId] — play its bounce/spotlight animation. */
        fun playAdvance(nodeId: String)

        /** A brand-new topic node was inserted — play its pop-in animation. */
        fun playPopIn(nodeId: String)

        /** Celebrate a completion (radial confetti burst). */
        fun showConfetti()

        /** A 200-XP multiple was crossed — cross a "LEVEL UP" ribbon for [level]. */
        fun showLevelUp(level: Int)

        /** Non-blocking transient message (branch added, lesson failed to open, …). */
        fun showToast(message: String)

        /**
         * Route to the existing AI chat feature for this subtopic.
         * [blockIndex] is -1 for the whole subtopic, or the index of the lesson paragraph asked about.
         */
        fun openChat(context: String, pathId: String, nodeId: String, blockIndex: Int)
    }

    interface Presenter : MvpPresenter<View> {
        /** A node was tapped: opens its lesson sheet, or — in add mode — the branch sheet. */
        fun onNodeTapped(nodeId: String)

        /** Branch off [nodeId] itself, adding a detour beside whatever it already leads to. */
        fun onBranchFromNode(nodeId: String)

        /** Toggle add mode: show a "+" at every spot a new section could go, or hide them again. */
        fun onAddNodeRequested()

        /** Leave add mode without picking a spot. */
        fun onAddModeCancelled()

        /** A "+" slot (or, in add mode, a node) was picked — open the sheet for that spot. */
        fun onAddSlotTapped(anchorId: String)

        /** Mark [nodeId] complete: +XP, advance to the next node, celebrate, maybe level up. */
        fun onMarkComplete(nodeId: String)

        /**
         * Confirm a new branch about [name]: generates and persists a short chain of nodes where
         * the tapped affordance was, ending in a fresh affordance.
         */
        fun onGenerateBranch(name: String)

        /** "Ask AI" for [nodeId] — routes to chat. */
        fun onAskAi(nodeId: String)

        /** A paragraph of [nodeId]'s lesson was tapped — routes to chat scoped to that block. */
        fun onAskAboutBlock(nodeId: String, blockText: String, blockIndex: Int)

        /** The open sheet was dismissed by the user. */
        fun onSheetDismissed()
    }
}

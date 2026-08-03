package com.example.grasp.ui.feature.path

import com.example.grasp.core.edit.LessonEdit
import com.example.grasp.core.edit.RoadmapEdit
import com.example.grasp.core.mvp.MvpPresenter
import com.example.grasp.core.mvp.MvpView
import com.example.grasp.data.model.Subtopic
import com.example.grasp.ui.feature.subtopic.SectionShape

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

        /**
         * Open the subtopic detail bottom sheet for [subtopic]; [completed] drives the CTA.
         *
         * @param editing whether the sheet is in edit mode (FR4.5) rather than reading mode. Off
         *        by default and off again every time the sheet opens — editing is something the
         *        user asks for, never the state they find the lesson in.
         * @param canUndo whether there is a stored earlier version to take back.
         * @param section the roadmap facts about this node that edit mode can change; null when
         *        the node isn't on the board (which is not a state the app produces, but is what
         *        a stale sheet would look like).
         */
        fun showSubtopicSheet(
            subtopic: Subtopic,
            completed: Boolean,
            editing: Boolean = false,
            canUndo: Boolean = false,
            section: SectionShape? = null,
        )

        /** Open the "grow your path" sheet; [fromTitle] is the lesson the branch will grow off. */
        fun showBranchSheet(fromTitle: String)

        /** Fill the open branch sheet's starter chips (empty = show none). */
        fun showBranchSuggestions(topics: List<String>)

        /** Put the open branch sheet into / out of its "growing your branch…" state. */
        fun showBranchGenerating(generating: Boolean)

        /**
         * Ask, before deleting the section the user just tapped on the board.
         *
         * A one-shot request rather than a flag on [PathUiState] because it is a question, not a
         * state of the board: the picker is already over by the time it is asked.
         *
         * @param hasDescendants whether anything grew beyond this section — the only thing the
         *        question actually hinges on, since with nothing below it there is no second choice
         *        to make.
         */
        fun confirmDeleteSection(nodeId: String, title: String, hasDescendants: Boolean)

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
         * [blockId] is empty for the whole subtopic, or the stable id of the paragraph asked about.
         */
        fun openChat(context: String, pathId: String, nodeId: String, blockId: String)
    }

    interface Presenter : MvpPresenter<View> {
        /**
         * A node was tapped: opens its lesson sheet — or, in one of the picking modes, becomes the
         * section the new branch grows from / the one to delete / the one to move or move under.
         *
         * A tap on a node the current mode can't act on does nothing at all: the roadmap is only
         * ever reshaped by a tap the board already showed as available.
         */
        fun onNodeTapped(nodeId: String)

        /** Branch off [nodeId] itself, adding a detour beside whatever it already leads to. */
        fun onBranchFromNode(nodeId: String)

        /** Open (or close) the roadmap's edit menu — add a section, move one, delete one. */
        fun onEditRoadmapRequested()

        /** From the menu: turn the board into a picker for the section to branch from. */
        fun onAddSectionChosen()

        /** From the menu: turn the board into a picker for the section to move. */
        fun onMoveSectionChosen()

        /** From the menu: turn the board into a picker for the section to delete. */
        fun onDeleteSectionChosen()

        /** Back out of the menu or any picker, leaving the roadmap untouched. */
        fun onBoardEditCancelled()

        /**
         * The delete asked about by [View.confirmDeleteSection] was confirmed. [withDescendants]
         * is the user's answer to whether everything beyond it goes too; false re-hangs those
         * sections where the deleted one stood.
         */
        fun onDeleteSectionConfirmed(nodeId: String, withDescendants: Boolean)

        /** Mark [nodeId] complete: +XP, advance to the next node, celebrate, maybe level up. */
        fun onMarkComplete(nodeId: String)

        /**
         * Confirm a new branch about [name]: generates and persists a short chain of nodes where
         * the tapped affordance was, ending in a fresh affordance.
         */
        fun onGenerateBranch(name: String)

        /** "Ask AI" for [nodeId] — routes to chat. */
        fun onAskAi(nodeId: String)

        /**
         * "Ask AI" for the roadmap itself — routes to a chat that sees the whole tree rather than
         * any one lesson, which is where "what should I learn next" can actually be answered.
         */
        fun onAskAboutRoadmap()

        /** A paragraph of [nodeId]'s lesson was tapped — routes to chat scoped to that block. */
        fun onAskAboutBlock(nodeId: String, blockText: String, blockId: String)

        /** The open sheet was dismissed by the user. */
        fun onSheetDismissed()

        /** Turn edit mode on or off for the open lesson (FR4.5). */
        fun onToggleEditMode()

        /** Apply one change the user made in edit mode, and save it. */
        fun onLessonEdit(edit: LessonEdit)

        /** Take back the last change made to the open lesson. */
        fun onUndoLessonEdit()

        /** Apply one change to the roadmap's own shape, and save it. */
        fun onRoadmapEdit(edit: RoadmapEdit)

        /**
         * The chat overlay closed. The roadmap may not be the one this screen loaded any more —
         * the tutor can change its shape from inside the chat once the user accepts (FR5.4) — so
         * the board is re-read. An overlay never detaches the presenter, so it has to be told.
         */
        fun onChatClosed()

        /** Explicitly download the entire path for offline use. */
        fun onDownloadPath()
    }
}

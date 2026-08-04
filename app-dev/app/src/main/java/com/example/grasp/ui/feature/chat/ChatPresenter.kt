package com.example.grasp.ui.feature.chat

import android.util.Log
import com.example.grasp.core.edit.EditAuthor
import com.example.grasp.core.edit.EditProposal
import com.example.grasp.core.edit.MAX_CHANGES_PER_PROPOSAL
import com.example.grasp.core.edit.ToolCall
import com.example.grasp.core.edit.proposeLessonEdits
import com.example.grasp.core.edit.proposeRoadmapEdits
import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.ChatMessage
import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.LessonBlock
import com.example.grasp.data.model.ProposalOutcome
import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.repository.ChatChunk
import com.example.grasp.data.repository.ChatRepository
import com.example.grasp.data.repository.ChatSession
import com.example.grasp.data.repository.FirebaseChatRepository
import com.example.grasp.data.repository.FirebaseUserRepository
import com.example.grasp.data.repository.FirebasePathRepository
import com.example.grasp.data.repository.GeminiChatSession
import com.example.grasp.data.repository.PathRepository
import com.example.grasp.data.repository.TutorToolset
import com.example.grasp.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ChatPresenter(
    private val context: String,
    private val scope: ChatScope = ChatScope.General,
    // The real repository, so the tutor is grounded in the SAME generated lesson the user is
    // reading (it falls back to the fake data when signed out). It is also where an accepted
    // proposal lands — through `editLesson`, the same door the manual editor uses.
    private val repo: PathRepository = FirebasePathRepository(),
    private val chatRepo: ChatRepository = FirebaseChatRepository(),
    private val userRepo: UserRepository = FirebaseUserRepository(),
    private val sessionFactory: (String, TutorToolset) -> ChatSession = { instruction, tools ->
        GeminiChatSession(instruction, tools)
    },
) : BasePresenter<ChatContract.View>(), ChatContract.Presenter {

    private val chatId: String = scope.chatId

    /**
     * What the tutor is allowed to offer to change here, which follows entirely from what the user
     * is looking at (FR5.4).
     *
     * A Tinkerer guide gets nothing: its steps have no edit vocabulary — phase 1 built one for
     * lessons and roadmaps only — so there is nothing for a tool call to turn into.
     */
    private val toolset: TutorToolset = when (scope) {
        is ChatScope.Node, is ChatScope.Block -> TutorToolset.LESSON
        is ChatScope.Path -> TutorToolset.ROADMAP
        is ChatScope.General, is ChatScope.Guide, is ChatScope.Step -> TutorToolset.NONE
    }

    private val messages = mutableListOf<ChatMessage>()
    private var nextId = 0
    private var uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Failed assistant message id → the question that produced it, so Retry knows what to resend. */
    private val retryPrompts = mutableMapOf<String, String>()

    /**
     * Message id → the changes offered under it that nobody has answered yet.
     *
     * Kept here rather than read back off the message list because this is the authority on what
     * can still be applied: an entry is removed the moment a decision is taken, so a second tap on
     * a card mid-flight finds nothing and does nothing.
     */
    private val openProposals = mutableMapOf<String, EditProposal>()

    private var isSending = false
    private var isCircuitBroken = false

    /**
     * Built on first use rather than eagerly: the instruction embeds the node's lesson, which the
     * repository may have to generate (and which must not be fetched on the main thread).
     * Only ever touched from [streamReply]'s single coroutine, so no locking is needed.
     */
    private var session: ChatSession? = null

    private suspend fun session(): ChatSession =
        session ?: sessionFactory(buildSystemInstruction(), toolset).also { session = it }

    override fun onViewAttached() {
        view?.showMessages(messages.toList())
        uiScope.launch {
            val saved = chatRepo.loadMessages(chatId)
            if (saved.isNotEmpty()) {
                messages.clear()
                messages.addAll(saved)
                nextId = messages.size
                view?.showMessages(messages.toList())
            }
        }
    }

    override fun detach() {
        uiScope.cancel()
        super.detach()
    }

    override fun onSend(text: String) {
        if (text.isBlank() || isSending || isCircuitBroken) return
        val prompt = text.trim()

        val userMessage = ChatMessage("msg-${nextId++}", ChatMessage.Author.USER, prompt)
        messages += userMessage

        val pendingId = "msg-${nextId++}"
        messages += ChatMessage(pendingId, ChatMessage.Author.ASSISTANT, "", pending = true)
        
        isSending = true
        view?.showMessages(messages.toList(), isSending = isSending, isCircuitBroken = isCircuitBroken)

        uiScope.launch {
            chatRepo.saveMessage(chatId, context, userMessage)
        }

        streamReply(pendingId, prompt)
    }

    /**
     * Ask again, in place.
     *
     * The failed bubble becomes the pending one rather than a new message being appended, so a
     * flaky connection doesn't leave a column of dead ends in the transcript.
     */
    override fun onRetry(messageId: String) {
        if (isSending || isCircuitBroken) return
        val prompt = retryPrompts[messageId] ?: return
        updatePending(messageId, text = "", stillPending = true)
        
        isSending = true
        view?.showMessages(messages.toList(), isSending = isSending, isCircuitBroken = isCircuitBroken)

        streamReply(messageId, prompt)
    }

    private fun streamReply(pendingId: String, prompt: String) {
        uiScope.launch {
            var accumulated = ""
            val calls = mutableListOf<ToolCall>()
            try {
                session().sendMessageStream(prompt).collect { chunk ->
                    when (chunk) {
                        is ChatChunk.Text -> {
                            accumulated += chunk.text
                            updatePending(pendingId, accumulated, stillPending = true)
                        }
                        // Held until the turn is over: a proposal is reviewed as one batch, and
                        // showing the first card while the second is still arriving invites a tap
                        // on half of it.
                        is ChatChunk.Call -> calls += chunk.call

                        is ChatChunk.Error -> {
                            // If the circuit is broken, the session emits an error chunk.
                            // We detect "unavailable" to trip the UI's broken state.
                            if (chunk.message.contains("unavailable", ignoreCase = true)) {
                                isCircuitBroken = true
                            }
                            throw Exception(chunk.message)
                        }
                    }
                }
                retryPrompts -= pendingId
                val proposal = proposalFrom(calls)
                // A turn that is nothing but a tool call has no words in it, and a bubble with
                // cards under it and nothing in it reads as a bug.
                val text = accumulated.ifBlank { if (proposal != null) PROPOSAL_ONLY_TEXT else "" }
                if (proposal != null) openProposals[pendingId] = proposal
                updatePending(pendingId, text, stillPending = false, proposal = proposal)
                // The proposal is deliberately not saved with it — see ChatMessage.proposal.
                val assistantMessage = ChatMessage(pendingId, ChatMessage.Author.ASSISTANT, text)
                chatRepo.saveMessage(chatId, context, assistantMessage)
            } catch (e: Exception) {
                // Recover the UI first, log second. Reversed, anything the logger itself throws
                // leaves the bubble stuck on the typing dots forever.
                retryPrompts[pendingId] = prompt
                updatePending(pendingId, text = e.message ?: FAILURE_TEXT, stillPending = false, failed = true)
                // The class name and stack trace belong in the log, not in the tutor's voice: the
                // bubble is where an answer goes, and "UnknownHostException: null" reads as one.
                Log.e("ChatPresenter", "Gemini call failed", e)
            } finally {
                isSending = false
                view?.showMessages(messages.toList(), isSending = isSending, isCircuitBroken = isCircuitBroken)
            }
        }
    }

    override fun onAttachImage() {
        if (isSending || isCircuitBroken) return
        messages += ChatMessage(
            id = "msg-${nextId++}",
            author = ChatMessage.Author.USER,
            text = "",
            imageUri = "sample://attached-photo",
        )
        view?.showMessages(messages.toList(), isSending = isSending, isCircuitBroken = isCircuitBroken)
    }

    private fun updatePending(
        id: String,
        text: String,
        stillPending: Boolean,
        failed: Boolean = false,
        proposal: EditProposal? = null,
    ) {
        val idx = messages.indexOfFirst { it.id == id }
        if (idx >= 0) {
            messages[idx] = messages[idx].copy(
                text = text,
                pending = stillPending,
                failed = failed,
                proposal = proposal,
            )
            view?.showMessages(messages.toList(), isSending = isSending, isCircuitBroken = isCircuitBroken)
        }
    }

    // ---- Proposed changes (FR5.4) --------------------------------------------------------------

    /**
     * What [calls] would do to the material this conversation is about, or null if they would do
     * nothing to it.
     *
     * The material is read again here rather than reused from the briefing, because the user may
     * have edited it by hand since the conversation started — and a card has to show what the
     * change would do to the lesson as it stands now, not as the tutor was told about it.
     */
    private suspend fun proposalFrom(calls: List<ToolCall>): EditProposal? {
        if (calls.isEmpty()) return null
        val proposal = when (val s = scope) {
            is ChatScope.Node -> repo.subtopic(s.pathId, s.nodeId)?.let { proposeLessonEdits(it, calls) }
            is ChatScope.Block -> repo.subtopic(s.pathId, s.nodeId)?.let { proposeLessonEdits(it, calls) }
            is ChatScope.Path -> repo.learningPath(s.pathId)?.let { proposeRoadmapEdits(it, calls) }
            // The model was given no tools in these scopes, so a call here is one it invented.
            is ChatScope.General, is ChatScope.Guide, is ChatScope.Step -> null
        }
        return proposal?.takeUnless { it.isEmpty }
    }

    override fun onAcceptProposal(messageId: String) {
        // Taken out of the map before anything suspends, so a second tap on the same card while
        // the first is still writing finds nothing to apply.
        val proposal = openProposals.remove(messageId) ?: return
        if (proposal.changes.isEmpty()) {
            settle(messageId, ProposalOutcome.REJECTED, proposal)
            return
        }
        uiScope.launch {
            val applied = applyToMaterial(proposal)
            settle(
                messageId,
                if (applied) ProposalOutcome.ACCEPTED else ProposalOutcome.FAILED,
                proposal,
            )
        }
    }

    override fun onRejectProposal(messageId: String) {
        val proposal = openProposals.remove(messageId) ?: return
        settle(messageId, ProposalOutcome.REJECTED, proposal)
    }

    /**
     * The accepted changes, written through the repository.
     *
     * The same call the editing UI makes, differing only in [EditAuthor] — which is the whole
     * point of phase 1's one operation set: an AI edit is not a second kind of edit, so it gets
     * the same validation, the same partial write and the same undo entry as a hand-made one.
     */
    private suspend fun applyToMaterial(proposal: EditProposal): Boolean = when (val s = scope) {
        is ChatScope.Node ->
            repo.editLesson(s.pathId, s.nodeId, proposal.lessonEdits(), EditAuthor.ASSISTANT) != null
        is ChatScope.Block ->
            repo.editLesson(s.pathId, s.nodeId, proposal.lessonEdits(), EditAuthor.ASSISTANT) != null
        is ChatScope.Path ->
            repo.editRoadmap(s.pathId, proposal.roadmapEdits()) != null
        is ChatScope.General, is ChatScope.Guide, is ChatScope.Step -> false
    }

    /** Record the decision on the card, and tell the model about it for its next turn. */
    private fun settle(messageId: String, outcome: ProposalOutcome, proposal: EditProposal) {
        val word = when (outcome) {
            ProposalOutcome.ACCEPTED -> "accepted by the user and applied"
            ProposalOutcome.REJECTED -> "rejected by the user"
            ProposalOutcome.FAILED -> "accepted, but it no longer fitted the material"
        }
        proposal.changes.forEach { session?.settle(it.id, word) }

        val idx = messages.indexOfFirst { it.id == messageId }
        if (idx >= 0) {
            messages[idx] = messages[idx].copy(proposalOutcome = outcome)
            view?.showMessages(messages.toList())
        }
    }

    // ---- System instruction -------------------------------------------------------------------

    /**
     * The tutor's briefing, assembled in tiers from the widest context down to the narrowest.
     *
     * Each [ChatScope] case appends everything the tiers above it would have, then narrows: a
     * block chat is told the roadmap, then the whole lesson, and only then which paragraph the
     * user is pointing at. The wider material stays in because it is still true and the tutor
     * needs it to answer "why does this matter" — it just stops being the subject.
     */
    // internal, not private, so the tier assembly can be asserted on directly in unit tests —
    // it is the whole point of the class and there is no other way to observe it.
    internal suspend fun buildSystemInstruction(): String = buildString {
        val prefs = userRepo.getPreferences()

        appendLine("You are a helpful AI tutor in the Grasp learning app.")
        appendLine("Be concise, clear, and encouraging.")
        appendLine("If the user shares an image, describe what you see and relate it to the topic.")
        appendLine()

        appendLine("User Preferences:")
        appendLine("- Style: ${prefs.style.label} (${prefs.style.prompt})")
        appendLine("- Tone: ${prefs.tone.label} (${prefs.tone.prompt})")
        appendLine()

        when (val s = scope) {
            is ChatScope.General -> appendFallback()
            is ChatScope.Path -> appendRoadmapTier(s.pathId)
            is ChatScope.Node -> appendLessonTier(s.pathId, s.nodeId, focusBlockId = null)
            is ChatScope.Block -> appendLessonTier(s.pathId, s.nodeId, focusBlockId = s.blockId)
            is ChatScope.Guide -> appendGuideTier(s.pathId, focusStepId = null)
            is ChatScope.Step -> appendGuideTier(s.pathId, focusStepId = s.stepId)
        }

        if (toolset != TutorToolset.NONE) appendEditingBriefing()
    }

    /**
     * How to use the tools, and when not to (FR5.4).
     *
     * Appended last, after the material, because everything it refers to — the ids in square
     * brackets — only exists in what came above it. Written at all because the tools' own
     * descriptions can say what each one does but not what a good tutor does with them: the
     * failure that actually shows up is not a malformed call, it is a model that answers "what
     * does this mean?" by rewriting the paragraph.
     */
    private fun StringBuilder.appendEditingBriefing() {
        appendLine()
        appendLine("---")
        appendLine("You can also offer to CHANGE this material, using the tools you have been given.")
        appendLine(
            "Calling a tool does not change anything by itself. It becomes a before/after card " +
                "the user taps Accept or Reject on, so nothing you propose takes effect unless " +
                "they say so — never tell them you have already made a change.",
        )
        appendLine("- Propose a change only when they ask for one, or when they have agreed to a")
        appendLine("  problem you just pointed out. A question deserves an answer, not an edit.")
        appendLine("- Refer to a part by the id in square brackets beside it. Never invent an id.")
        appendLine("- Keep it to the smallest set of changes that does what they asked, and to at")
        appendLine("  most $MAX_CHANGES_PER_PROPOSAL at a time.")
        appendLine("- Say in words what you are proposing and why, in the same reply as the change.")
        appendLine(
            "- You CANNOT mark anything complete or tick anything off, and must not offer to. " +
                "Whether they have learnt something is theirs alone to say.",
        )
    }

    /** Used whenever the material can't be loaded — [context] is the title the user is looking at. */
    private fun StringBuilder.appendFallback() {
        appendLine("The user is studying: $context")
    }

    /** Tier 1, Learner: the roadmap as a whole. */
    private suspend fun StringBuilder.appendRoadmapTier(pathId: String) {
        val path = repo.learningPath(pathId)
        if (path == null) {
            appendFallback()
            return
        }
        appendLine("The user is working through a roadmap called \"${path.title}\".")
        appendLine()
        appendLine("Its sections, indented by what they branch off (x = already completed):")
        appendRoadmapTree(path)
        appendLine()
        appendLine(
            "They are asking about the roadmap as a whole — what to learn next, how the pieces " +
                "fit together, what is missing — rather than about any one section.",
        )
    }

    /**
     * The tree, depth-first from its roots.
     *
     * Walked from `children` rather than printed flat because the branching IS the roadmap's
     * meaning: "what should I do next" has a different answer on a branch than on the trunk.
     * `seen` guards against a malformed parent/child pair looping forever — the tree comes back
     * from Firestore, where nothing has enforced its shape.
     */
    private fun StringBuilder.appendRoadmapTree(path: LearningPath) {
        val byId = path.nodes.associateBy { it.id }
        val seen = mutableSetOf<String>()

        fun walk(id: String, depth: Int) {
            val node = byId[id] ?: return
            if (!seen.add(id)) return
            // Branch-out nodes are affordances the user taps to grow the tree, not material.
            if (!node.isBranchOut) {
                append("  ".repeat(depth))
                append("- ")
                if (node.completed) append("x ")
                append(node.title)
                if (node.estMinutes > 0) append(" (${node.estMinutes} min)")
                // The handle the tutor names a section by when it proposes a change to one. Only
                // when it has roadmap tools: an id it can do nothing with is noise it may repeat
                // back at the user.
                if (toolset == TutorToolset.ROADMAP) append(" [${node.id}]")
                appendLine()
            }
            node.children.forEach { walk(it, depth + 1) }
        }

        path.nodes.filter { it.parentId == null }.forEach { walk(it.id, 0) }
        // Anything orphaned by a bad parent link still belongs in the briefing.
        path.nodes.filter { it.id !in seen }.forEach { walk(it.id, 0) }
    }

    /** Tier 2, Learner: one lesson — and tier 3 when [focusBlockId] names a block inside it. */
    private suspend fun StringBuilder.appendLessonTier(
        pathId: String,
        nodeId: String,
        focusBlockId: String?,
    ) {
        val subtopic = repo.subtopic(pathId, nodeId)
        if (subtopic == null) {
            appendFallback()
            return
        }

        repo.learningPath(pathId)?.let { appendLine("Roadmap: \"${it.title}\"") }
        appendLine("The user is studying: \"${subtopic.title}\"")
        appendLine()
        appendLine("Summary: ${subtopic.summary}")
        appendLine()
        appendLine("Why it matters: ${subtopic.whyItMatters}")
        appendLine()
        appendLine("Content:")
        // Headings included, so the tutor knows how the lesson is organised.
        subtopic.body.forEach { block ->
            appendBlock(block)
        }

        if (focusBlockId != null) appendBlockTier(subtopic, focusBlockId)
    }

    /**
     * Tier 3, Learner: the one block the user tapped.
     *
     * A missing block is not an error — the chat outlives the paragraph it was opened from, and a
     * lesson edit can delete it. The conversation then simply widens to its lesson, which is
     * still the material the user was reading.
     */
    private fun StringBuilder.appendBlockTier(subtopic: Subtopic, blockId: String) {
        val focus = subtopic.body.firstOrNull { it.id == blockId } ?: return
        appendLine()
        appendLine("The user tapped THIS specific part of the lesson, and their questions are about it:")
        appendBlock(focus)
        appendLine()
        appendLine(
            "Answer about that part specifically. Treat the rest of the lesson as background you " +
                "may draw on, but do not re-explain it unless they ask.",
        )
    }

    /** Tier 1-2, Tinkerer: the guide, and the step they are standing on when [focusStepId] is set. */
    private suspend fun StringBuilder.appendGuideTier(pathId: String, focusStepId: String?) {
        val guide = repo.tinkerGuide(pathId)
        if (guide == null) {
            appendFallback()
            return
        }

        appendLine("The user is working on the task: \"${guide.title}\"")
        appendLine()
        appendLine("Steps (x = already done):")
        guide.steps.forEach { step ->
            append(if (step.done) "x " else "- ")
            append("${step.order}. ${step.instruction}")
            if (step.detail.isNotBlank()) append(" (${step.detail})")
            appendLine()
        }

        val focus = focusStepId?.let { id -> guide.steps.firstOrNull { it.id == id } } ?: return
        appendLine()
        appendLine(
            "They are on step ${focus.order} right now — \"${focus.instruction}\" — so assume a " +
                "question without an obvious subject is about that step. They have not done the " +
                "later steps yet; do not spoil them unless asked.",
        )
    }

    /**
     * One block as the tutor sees it.
     *
     * Visuals are described, not reproduced — the user can see them, and the tutor needs to know
     * what they are looking at when they ask about one.
     *
     * Each is prefixed with its [LessonBlock.id] when the tutor can propose changes to the lesson,
     * because that id is the ONLY way it can name the paragraph it wants to rewrite: a proposal
     * has to survive the user editing something else in the meantime, and a position doesn't.
     */
    private fun StringBuilder.appendBlock(block: LessonBlock) {
        val rendered = when (block) {
            is LessonBlock.Heading -> "## ${block.text}"
            is LessonBlock.Paragraph -> block.text
            is LessonBlock.Code -> "```${block.language}\n${block.text}\n```"
            is LessonBlock.Diagram ->
                "[${block.kind.name.lowercase()} diagram: ${block.text}] " +
                    block.items.joinToString(" -> ") { it.label }
            is LessonBlock.Image -> "[image: ${block.text}]"
        }
        // A heading still opens with a blank line, so the lesson reads as sections rather than
        // one run-on wall.
        if (block is LessonBlock.Heading) appendLine()
        if (toolset == TutorToolset.LESSON) append("[${block.id}] ")
        appendLine(rendered)
    }

    private companion object {
        /**
         * Deliberately says nothing about what went wrong. The causes the user can act on are all
         * the same action — try again — and the ones they cannot act on are not their problem.
         */
        const val FAILURE_TEXT = "Couldn't reach the tutor just now."

        /**
         * Stands in when the tutor proposes a change and says nothing at all about it, which the
         * briefing asks it not to do but cannot guarantee. Neutral on purpose: it must not read as
         * the assistant vouching for a change the user hasn't looked at yet.
         */
        const val PROPOSAL_ONLY_TEXT = "Here's a change I could make:"
    }
}

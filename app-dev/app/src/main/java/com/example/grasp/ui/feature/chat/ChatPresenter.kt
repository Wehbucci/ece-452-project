package com.example.grasp.ui.feature.chat

import android.util.Log
import com.example.grasp.core.mvp.BasePresenter
import com.example.grasp.data.model.ChatMessage
import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.LessonBlock
import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.repository.ChatRepository
import com.example.grasp.data.repository.ChatSession
import com.example.grasp.data.repository.FirebaseChatRepository
import com.example.grasp.data.repository.FirebaseUserRepository
import com.example.grasp.data.repository.FirebasePathRepository
import com.example.grasp.data.repository.GeminiChatSession
import com.example.grasp.data.repository.PathRepository
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
    // reading (it falls back to the fake data when signed out).
    private val repo: PathRepository = FirebasePathRepository(),
    private val chatRepo: ChatRepository = FirebaseChatRepository(),
    private val userRepo: UserRepository = FirebaseUserRepository(),
    private val sessionFactory: (String) -> ChatSession = { GeminiChatSession(it) },
) : BasePresenter<ChatContract.View>(), ChatContract.Presenter {

    private val chatId: String = scope.chatId

    private val messages = mutableListOf<ChatMessage>()
    private var nextId = 0
    private var uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Failed assistant message id → the question that produced it, so Retry knows what to resend. */
    private val retryPrompts = mutableMapOf<String, String>()

    /**
     * Built on first use rather than eagerly: the instruction embeds the node's lesson, which the
     * repository may have to generate (and which must not be fetched on the main thread).
     * Only ever touched from [streamReply]'s single coroutine, so no locking is needed.
     */
    private var session: ChatSession? = null

    private suspend fun session(): ChatSession =
        session ?: sessionFactory(buildSystemInstruction()).also { session = it }

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
        if (text.isBlank()) return
        val prompt = text.trim()

        val userMessage = ChatMessage("msg-${nextId++}", ChatMessage.Author.USER, prompt)
        messages += userMessage

        val pendingId = "msg-${nextId++}"
        messages += ChatMessage(pendingId, ChatMessage.Author.ASSISTANT, "", pending = true)
        view?.showMessages(messages.toList())

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
        val prompt = retryPrompts[messageId] ?: return
        updatePending(messageId, text = "", stillPending = true)
        streamReply(messageId, prompt)
    }

    private fun streamReply(pendingId: String, prompt: String) {
        uiScope.launch {
            var accumulated = ""
            try {
                session().sendMessageStream(prompt).collect { chunk ->
                    accumulated += chunk
                    updatePending(pendingId, accumulated, stillPending = true)
                }
                retryPrompts -= pendingId
                updatePending(pendingId, accumulated, stillPending = false)
                val assistantMessage = ChatMessage(pendingId, ChatMessage.Author.ASSISTANT, accumulated)
                chatRepo.saveMessage(chatId, context, assistantMessage)
            } catch (e: Exception) {
                // Recover the UI first, log second. Reversed, anything the logger itself throws
                // leaves the bubble stuck on the typing dots forever.
                retryPrompts[pendingId] = prompt
                updatePending(pendingId, text = FAILURE_TEXT, stillPending = false, failed = true)
                // The class name and stack trace belong in the log, not in the tutor's voice: the
                // bubble is where an answer goes, and "UnknownHostException: null" reads as one.
                Log.e("ChatPresenter", "Gemini call failed", e)
            }
        }
    }

    override fun onAttachImage() {
        messages += ChatMessage(
            id = "msg-${nextId++}",
            author = ChatMessage.Author.USER,
            text = "",
            imageUri = "sample://attached-photo",
        )
        view?.showMessages(messages.toList())
    }

    private fun updatePending(
        id: String,
        text: String,
        stillPending: Boolean,
        failed: Boolean = false,
    ) {
        val idx = messages.indexOfFirst { it.id == id }
        if (idx >= 0) {
            messages[idx] = messages[idx].copy(text = text, pending = stillPending, failed = failed)
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
    }

    /** Used whenever the material can't be loaded — [context] is the title the user is looking at. */
    private fun StringBuilder.appendFallback() {
        appendLine("The user is studying: $context")
    }

    /** Tier 1, Learner: the roadmap as a whole. */
    private fun StringBuilder.appendRoadmapTier(pathId: String) {
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
    private fun StringBuilder.appendGuideTier(pathId: String, focusStepId: String?) {
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
     */
    private fun StringBuilder.appendBlock(block: LessonBlock) {
        when (block) {
            is LessonBlock.Heading -> appendLine().appendLine("## ${block.text}")
            is LessonBlock.Paragraph -> appendLine(block.text)
            is LessonBlock.Code -> appendLine("```${block.language}\n${block.text}\n```")
            is LessonBlock.Diagram -> appendLine(
                "[${block.kind.name.lowercase()} diagram: ${block.text}] " +
                    block.items.joinToString(" -> ") { it.label },
            )
            is LessonBlock.Image -> appendLine("[image: ${block.text}]")
        }
    }

    private companion object {
        /**
         * Deliberately says nothing about what went wrong. The causes the user can act on are all
         * the same action — try again — and the ones they cannot act on are not their problem.
         */
        const val FAILURE_TEXT = "Couldn't reach the tutor just now."
    }
}

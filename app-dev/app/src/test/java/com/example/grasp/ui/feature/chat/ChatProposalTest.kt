package com.example.grasp.ui.feature.chat

import com.example.grasp.core.edit.LessonEdit
import com.example.grasp.core.edit.ToolCall
import com.example.grasp.core.edit.TutorTool
import com.example.grasp.data.model.ChatMessage
import com.example.grasp.data.model.LessonBlock
import com.example.grasp.data.model.ProposalOutcome
import com.example.grasp.data.model.UserPreferences
import com.example.grasp.data.repository.ChatChunk
import com.example.grasp.data.repository.ChatRepository
import com.example.grasp.data.repository.ChatSession
import com.example.grasp.data.repository.FakePathRepository
import com.example.grasp.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The road a change takes from the model to the lesson (FR5.4).
 *
 * Driven through a scripted [ChatSession] rather than a real one, because the thing under test is
 * the gate — that a proposed change sits in front of the user until they tap, and that a tap is
 * the only thing that writes it. Persuading a real model to call a tool on cue would test the
 * model, not the gate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatProposalTest {

    private val dispatcher = StandardTestDispatcher()

    private object NoChats : ChatRepository {
        override suspend fun loadMessages(chatId: String): List<ChatMessage> = emptyList()
        override suspend fun saveMessage(chatId: String, context: String, message: ChatMessage) = Unit
        override suspend fun existingChatIds(prefix: String): Set<String> = emptySet()
    }

    private object DefaultPrefs : UserRepository {
        override suspend fun getPreferences() = UserPreferences()
        override suspend fun setPreferences(prefs: UserPreferences) = Unit
        // Nothing in the tutor's briefing depends on the user's name.
        override suspend fun getUsername(): String? = null
        override suspend fun setUsername(username: String) = Unit
    }

    private class RecordingView : ChatContract.View {
        val frames = mutableListOf<List<ChatMessage>>()
        override fun showMessages(messages: List<ChatMessage>) { frames += messages }
        val latest: List<ChatMessage> get() = frames.last()
    }

    /** Says [words], then makes [calls] — the shape of a real turn that proposes something. */
    private class ScriptedSession(
        private val words: String = "",
        private val calls: List<ToolCall> = emptyList(),
    ) : ChatSession {
        val settled = mutableMapOf<String, String>()

        override fun sendMessageStream(userText: String): Flow<ChatChunk> = flow {
            if (words.isNotEmpty()) emit(ChatChunk.Text(words))
            calls.forEach { emit(ChatChunk.Call(it)) }
        }

        override fun settle(callId: String, outcome: String) { settled[callId] = outcome }
    }

    private fun attach(
        session: ChatSession,
        scope: ChatScope,
    ): Pair<ChatPresenter, RecordingView> {
        val presenter = ChatPresenter(
            context = "your material",
            scope = scope,
            repo = FakePathRepository,
            chatRepo = NoChats,
            userRepo = DefaultPrefs,
            sessionFactory = { _, _ -> session },
        )
        val view = RecordingView()
        presenter.attach(view)
        return presenter to view
    }

    private fun rewriteFirstParagraph(text: String, id: String = "c0") = ToolCall(
        id = id,
        name = TutorTool.REWRITE_BLOCK,
        // b1 is the first paragraph of every lesson the fake repository hands out.
        args = mapOf("block_id" to "b1", "text" to text),
    )

    private fun firstParagraph(): String = runBlocking {
        FakePathRepository.subtopic("ml-101", "supervised")!!
            .body.first { it is LessonBlock.Paragraph }.text
    }

    private val lessonScope = ChatScope.Node("ml-101", "supervised")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        FakePathRepository.clearEdits()
    }

    // ---- Proposing -----------------------------------------------------------------------------

    @Test
    fun `a tool call in a reply becomes a proposal on the message`() = runTest(dispatcher) {
        val (presenter, view) = attach(
            ScriptedSession("I'd tighten that up.", listOf(rewriteFirstParagraph("Tighter."))),
            lessonScope,
        )

        presenter.onSend("can you tighten that?")
        advanceUntilIdle()

        val reply = view.latest.last()
        assertEquals("I'd tighten that up.", reply.text)
        val proposal = reply.proposal
        assertNotNull(proposal)
        assertEquals(1, proposal!!.changes.size)
        assertEquals("Tighter.", proposal.changes.single().after)
        assertNull("nothing is decided yet", reply.proposalOutcome)
    }

    @Test
    fun `proposing does not change the lesson on its own`() = runTest(dispatcher) {
        val before = firstParagraph()
        val (presenter, _) = attach(
            ScriptedSession("Here.", listOf(rewriteFirstParagraph("Replaced."))),
            lessonScope,
        )

        presenter.onSend("tighten it")
        advanceUntilIdle()

        assertEquals("the lesson must be untouched until a tap says otherwise", before, firstParagraph())
    }

    @Test
    fun `a reply that is only a tool call still says something`() = runTest(dispatcher) {
        val (presenter, view) = attach(
            ScriptedSession(calls = listOf(rewriteFirstParagraph("Silent."))),
            lessonScope,
        )

        presenter.onSend("tighten it")
        advanceUntilIdle()

        // A bubble with cards under it and nothing in it reads as a bug.
        assertTrue(view.latest.last().text.isNotBlank())
    }

    @Test
    fun `a reply with no tool calls carries no proposal`() = runTest(dispatcher) {
        val (presenter, view) = attach(ScriptedSession("Supervised learning uses labels."), lessonScope)

        presenter.onSend("what is it?")
        advanceUntilIdle()

        assertNull(view.latest.last().proposal)
    }

    @Test
    fun `a call in a conversation with no material to change is ignored`() = runTest(dispatcher) {
        // A Tinkerer guide has no edit vocabulary, so the model is given no tools there — a call
        // arriving anyway is one it invented, and it must not find a way through.
        val (presenter, view) = attach(
            ScriptedSession("Sure.", listOf(rewriteFirstParagraph("Nope."))),
            ChatScope.Guide("omelette"),
        )

        presenter.onSend("fix step 3")
        advanceUntilIdle()

        assertNull(view.latest.last().proposal)
    }

    // ---- Accepting and rejecting -----------------------------------------------------------------

    @Test
    fun `accepting writes the change into the lesson`() = runTest(dispatcher) {
        val (presenter, view) = attach(
            ScriptedSession("Here.", listOf(rewriteFirstParagraph("A much tighter paragraph."))),
            lessonScope,
        )

        presenter.onSend("tighten it")
        advanceUntilIdle()
        presenter.onAcceptProposal(view.latest.last().id)
        advanceUntilIdle()

        assertEquals("A much tighter paragraph.", firstParagraph())
        assertEquals(ProposalOutcome.ACCEPTED, view.latest.last().proposalOutcome)
    }

    @Test
    fun `rejecting writes nothing and closes the offer`() = runTest(dispatcher) {
        val before = firstParagraph()
        val (presenter, view) = attach(
            ScriptedSession("Here.", listOf(rewriteFirstParagraph("Rejected."))),
            lessonScope,
        )

        presenter.onSend("tighten it")
        advanceUntilIdle()
        presenter.onRejectProposal(view.latest.last().id)
        advanceUntilIdle()

        assertEquals(before, firstParagraph())
        assertEquals(ProposalOutcome.REJECTED, view.latest.last().proposalOutcome)
    }

    @Test
    fun `a rejected offer cannot then be accepted`() = runTest(dispatcher) {
        val before = firstParagraph()
        val (presenter, view) = attach(
            ScriptedSession("Here.", listOf(rewriteFirstParagraph("Sneaked in."))),
            lessonScope,
        )

        presenter.onSend("tighten it")
        advanceUntilIdle()
        val messageId = view.latest.last().id
        presenter.onRejectProposal(messageId)
        presenter.onAcceptProposal(messageId)
        advanceUntilIdle()

        assertEquals(before, firstParagraph())
        assertEquals(ProposalOutcome.REJECTED, view.latest.last().proposalOutcome)
    }

    @Test
    fun `accepting twice applies once`() = runTest(dispatcher) {
        val (presenter, view) = attach(
            ScriptedSession("Here.", listOf(rewriteFirstParagraph("Once."))),
            lessonScope,
        )

        presenter.onSend("tighten it")
        advanceUntilIdle()
        val messageId = view.latest.last().id
        presenter.onAcceptProposal(messageId)
        presenter.onAcceptProposal(messageId)
        advanceUntilIdle()

        assertEquals("Once.", firstParagraph())
        // One undo entry, not two: a double tap must not cost the user two presses to get back.
        assertEquals(1, runBlocking { FakePathRepository.lessonRevisions("ml-101", "supervised") }.size)
    }

    @Test
    fun `the tutor is told what the user decided`() = runTest(dispatcher) {
        val session = ScriptedSession("Here.", listOf(rewriteFirstParagraph("Applied.")))
        val (presenter, view) = attach(session, lessonScope)

        presenter.onSend("tighten it")
        advanceUntilIdle()
        presenter.onAcceptProposal(view.latest.last().id)
        advanceUntilIdle()

        // So it stops re-offering a change that has already happened — or one already refused.
        assertTrue(session.settled["c0"]?.contains("accepted") == true)
    }

    @Test
    fun `an edit written against a lesson that has moved on fails visibly`() = runTest(dispatcher) {
        val (presenter, view) = attach(
            ScriptedSession(
                "Here.",
                // A block id that is not in any lesson the fake hands out, smuggled past the
                // proposal stage by being valid at the time — simulated here by naming a block
                // the repository will refuse.
                listOf(ToolCall("c0", TutorTool.DELETE_BLOCK, mapOf("block_id" to "b1"))),
            ),
            lessonScope,
        )

        presenter.onSend("drop it")
        advanceUntilIdle()
        // Take the block out from under the proposal before it is accepted.
        runBlocking {
            FakePathRepository.editLesson("ml-101", "supervised", listOf(LessonEdit.DeleteBlock("b1")))
        }
        presenter.onAcceptProposal(view.latest.last().id)
        advanceUntilIdle()

        assertEquals(ProposalOutcome.FAILED, view.latest.last().proposalOutcome)
        // The cards stay on screen saying so, rather than the offer quietly vanishing.
        assertNotNull(view.latest.last().proposal)
    }
}

package com.example.grasp.ui.feature.chat

import com.example.grasp.data.model.ChatMessage
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What the user sees while a reply is arriving, and when it never does.
 *
 * The failure path is the one every user eventually hits and the one that cannot be provoked
 * against the real backend, so it is tested through an injected [ChatSession] that simply throws.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatReplyTest {

    private val dispatcher = StandardTestDispatcher()

    private object NoChats : ChatRepository {
        override suspend fun loadMessages(chatId: String): List<ChatMessage> = emptyList()
        override suspend fun saveMessage(chatId: String, context: String, message: ChatMessage) = Unit
        override suspend fun existingChatIds(prefix: String): Set<String> = emptySet()
    }

    private object DefaultPrefs : UserRepository {
        override suspend fun getPreferences() = UserPreferences()
        override suspend fun setPreferences(prefs: UserPreferences) = Unit
    }

    /** Records everything the presenter renders, so a whole exchange can be replayed. */
    private class RecordingView : ChatContract.View {
        val frames = mutableListOf<List<ChatMessage>>()
        override fun showMessages(messages: List<ChatMessage>) { frames += messages }
        val latest: List<ChatMessage> get() = frames.last()
    }

    /**
     * Throws on its first [failures] calls, then streams [chunks].
     *
     * Failing by count rather than by swapping the session models a transient outage the way the
     * presenter actually meets one: it caches its session so a retry keeps the conversation's
     * history, which means the SAME object has to be able to fail and then recover.
     */
    private class ScriptedSession(
        private val chunks: List<String> = emptyList(),
        private var failures: Int = 0,
    ) : ChatSession {
        var calls = 0
            private set

        override fun sendMessageStream(userText: String): Flow<ChatChunk> = flow {
            calls++
            if (failures > 0) {
                failures--
                throw IllegalStateException("network is down")
            }
            chunks.forEach { emit(ChatChunk.Text(it)) }
        }
    }

    private fun attach(session: ChatSession): Pair<ChatPresenter, RecordingView> {
        val presenter = ChatPresenter(
            context = "your material",
            scope = ChatScope.General,
            repo = FakePathRepository,
            chatRepo = NoChats,
            userRepo = DefaultPrefs,
            sessionFactory = { _, _ -> session },
        )
        val view = RecordingView()
        presenter.attach(view)
        return presenter to view
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        FakePathRepository.clearEdits()
    }

    // ---- Streaming ---------------------------------------------------------------------------

    @Test
    fun `a reply stays pending until the stream closes`() = runTest(dispatcher) {
        val (presenter, view) = attach(ScriptedSession(chunks = listOf("Sure", ", here", " goes.")))

        presenter.onSend("why?")
        advanceUntilIdle()

        // Every frame that carries partial text must still say pending, or the UI has no way to
        // tell a half-written answer from a finished one.
        val partials = view.frames
            .mapNotNull { it.lastOrNull() }
            .filter { it.text.isNotEmpty() && it.text != "Sure, here goes." }
        assertTrue("expected intermediate frames", partials.isNotEmpty())
        assertTrue("a partial reply must be pending", partials.all { it.pending })

        val final = view.latest.last()
        assertEquals("Sure, here goes.", final.text)
        assertFalse(final.pending)
        assertFalse(final.failed)
    }

    // ---- Failure and retry -------------------------------------------------------------------

    @Test
    fun `a failed reply is marked failed and does not leak the exception`() = runTest(dispatcher) {
        val (presenter, view) = attach(ScriptedSession(failures = 1))

        presenter.onSend("why?")
        advanceUntilIdle()

        val reply = view.latest.last()
        assertTrue(reply.failed)
        assertFalse(reply.pending)
        assertFalse(
            "the exception class name must not reach the bubble",
            reply.text.contains("IllegalStateException"),
        )
        assertFalse("nor its message", reply.text.contains("network is down"))
    }

    @Test
    fun `retry resends the same question in place`() = runTest(dispatcher) {
        val session = ScriptedSession(failures = 2)
        val (presenter, view) = attach(session)

        presenter.onSend("why?")
        advanceUntilIdle()
        val failedId = view.latest.last().id

        presenter.onRetry(failedId)
        advanceUntilIdle()

        assertEquals("the question should have been asked again", 2, session.calls)
        // Two messages, not three: the failed bubble was reused rather than a second one appended.
        assertEquals(2, view.latest.size)
        assertEquals(failedId, view.latest.last().id)
    }

    @Test
    fun `a successful retry clears the failed state`() = runTest(dispatcher) {
        val (presenter, view) = attach(ScriptedSession(chunks = listOf("Yes."), failures = 1))

        presenter.onSend("why?")
        advanceUntilIdle()
        assertTrue(view.latest.last().failed)

        presenter.onRetry(view.latest.last().id)
        advanceUntilIdle()

        val reply = view.latest.last()
        assertFalse("a retried reply that arrives is not a failure", reply.failed)
        assertFalse(reply.pending)
        assertEquals("Yes.", reply.text)
    }

    @Test
    fun `retrying something that never failed does nothing`() = runTest(dispatcher) {
        val session = ScriptedSession(chunks = listOf("Yes."))
        val (presenter, view) = attach(session)

        presenter.onSend("why?")
        advanceUntilIdle()

        presenter.onRetry(view.latest.last().id)
        advanceUntilIdle()

        assertEquals("a delivered answer must not be re-requested", 1, session.calls)
    }
}

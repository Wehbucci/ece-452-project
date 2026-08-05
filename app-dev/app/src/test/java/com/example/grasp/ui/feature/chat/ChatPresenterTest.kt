package com.example.grasp.ui.feature.chat

import com.example.grasp.data.model.ChatMessage
import com.example.grasp.data.model.UserPreferences
import com.example.grasp.data.repository.ChatRepository
import com.example.grasp.data.repository.FakePathRepository
import com.example.grasp.data.repository.UserRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the tutor is actually told, per scope.
 *
 * These assert on the system instruction rather than on any reply, because the instruction is the
 * only part of the tutor's behaviour that is ours: the bug this replaced was not a bad answer, it
 * was a good answer to a question the model was never told the subject of.
 */
class ChatPresenterTest {

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

    private fun presenter(scope: ChatScope, context: String = "your material") =
        ChatPresenter(
            context = context,
            scope = scope,
            repo = FakePathRepository,
            chatRepo = NoChats,
            userRepo = DefaultPrefs,
        )

    private fun instructionFor(scope: ChatScope, context: String = "your material"): String =
        runBlocking { presenter(scope, context).buildSystemInstruction() }

    @After
    fun tearDown() {
        // FakePathRepository is a singleton holding edits in memory across tests.
        FakePathRepository.clearEdits()
    }

    private fun firstParagraph(pathId: String, nodeId: String) = runBlocking {
        FakePathRepository.subtopic(pathId, nodeId)!!.body
            .first { it is com.example.grasp.data.model.LessonBlock.Paragraph }
    }

    private fun firstNodeId(pathId: String) = runBlocking {
        FakePathRepository.learningPath(pathId)!!.nodes.first { !it.isBranchOut }.id
    }

    private fun path(pathId: String) = runBlocking { FakePathRepository.learningPath(pathId)!! }

    private fun guide(guideId: String) = runBlocking { FakePathRepository.tinkerGuide(guideId)!! }

    // ---- Tier 3: the tapped block ------------------------------------------------------------

    @Test
    fun `a block chat names the paragraph the user tapped`() {
        val nodeId = firstNodeId("ml-101")
        val block = firstParagraph("ml-101", nodeId)

        val instruction = instructionFor(ChatScope.Block("ml-101", nodeId, block.id))

        assertTrue(
            "the tutor must be told a specific part was tapped",
            instruction.contains("tapped THIS specific part"),
        )
        assertTrue("and which part it was", instruction.contains(block.text))
    }

    @Test
    fun `a block chat still carries the whole lesson as background`() {
        val nodeId = firstNodeId("ml-101")
        val block = firstParagraph("ml-101", nodeId)
        val lesson = runBlocking { FakePathRepository.subtopic("ml-101", nodeId)!! }

        val instruction = instructionFor(ChatScope.Block("ml-101", nodeId, block.id))

        assertTrue(instruction.contains(lesson.summary))
        assertTrue(instruction.contains("Why it matters: ${lesson.whyItMatters}"))
    }

    /**
     * A chat outlives the paragraph it was opened from — an edit can delete that block while the
     * conversation is still in the history list. Widening to the lesson is the correct answer.
     */
    @Test
    fun `a block that no longer exists quietly widens to its lesson`() {
        val nodeId = firstNodeId("ml-101")

        val instruction = instructionFor(ChatScope.Block("ml-101", nodeId, "b-deleted-long-ago"))

        assertFalse(instruction.contains("tapped THIS specific part"))
        assertTrue("the lesson is still the subject", instruction.contains("The user is studying:"))
    }

    // ---- Tier 2: one lesson ------------------------------------------------------------------

    @Test
    fun `a node chat does not claim anything was tapped`() {
        val nodeId = firstNodeId("ml-101")

        val instruction = instructionFor(ChatScope.Node("ml-101", nodeId))

        assertFalse(instruction.contains("tapped THIS specific part"))
        assertTrue(instruction.contains("The user is studying:"))
    }

    // ---- Tier 1: the roadmap -----------------------------------------------------------------

    @Test
    fun `a roadmap chat lists the sections instead of one lesson`() {
        val path = path("ml-101")
        val sections = path.nodes.filter { !it.isBranchOut }

        val instruction = instructionFor(ChatScope.Path("ml-101"))

        assertTrue(instruction.contains(path.title))
        sections.forEach {
            assertTrue("section \"${it.title}\" is missing", instruction.contains(it.title))
        }
        assertTrue(instruction.contains("asking about the roadmap as a whole"))
    }

    @Test
    fun `a roadmap chat does not paste in any lesson body`() {
        val nodeId = firstNodeId("ml-101")
        val lesson = runBlocking { FakePathRepository.subtopic("ml-101", nodeId)!! }

        val instruction = instructionFor(ChatScope.Path("ml-101"))

        assertFalse(
            "the whole point of the roadmap tier is that it is not one lesson",
            instruction.contains(lesson.summary),
        )
    }

    // ---- Tinkerer ----------------------------------------------------------------------------

    @Test
    fun `a guide chat lists every step`() {
        val guide = guide("omelette")

        val instruction = instructionFor(ChatScope.Guide("omelette"))

        assertTrue(instruction.contains(guide.title))
        guide.steps.forEach {
            assertTrue("step \"${it.instruction}\" is missing", instruction.contains(it.instruction))
        }
    }

    @Test
    fun `a step chat says which step the user is standing on`() {
        val guide = guide("omelette")
        val step = guide.steps[1]

        val instruction = instructionFor(ChatScope.Step("omelette", step.id))

        assertTrue(instruction.contains("They are on step ${step.order} right now"))
        assertTrue(instruction.contains("do not spoil them"))
    }

    @Test
    fun `a guide chat without a step makes no claim about where the user is`() {
        val instruction = instructionFor(ChatScope.Guide("omelette"))

        assertFalse(instruction.contains("right now"))
    }

    // ---- Fallbacks and preferences -----------------------------------------------------------

    @Test
    fun `a general chat falls back to the display context`() {
        val instruction = instructionFor(ChatScope.General, context = "Kalman filters")

        assertTrue(instruction.contains("The user is studying: Kalman filters"))
    }

    @Test
    fun `every scope carries the user's preferences`() {
        val scopes = listOf(
            ChatScope.General,
            ChatScope.Path("ml-101"),
            ChatScope.Guide("omelette"),
        )

        scopes.forEach {
            assertTrue("$it dropped the preferences", instructionFor(it).contains("User Preferences:"))
        }
    }
}

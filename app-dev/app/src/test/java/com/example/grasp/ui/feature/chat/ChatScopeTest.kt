package com.example.grasp.ui.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The scope decides which conversation the user is shown, so the interesting failures here are
 * two scopes agreeing when they shouldn't — that silently pours two conversations into one.
 */
class ChatScopeTest {

    @Test
    fun `a bare path id is the roadmap, not a lesson`() {
        assertEquals(ChatScope.Path("ml"), ChatScope.of(pathId = "ml"))
    }

    @Test
    fun `no path id at all is the general chat`() {
        assertEquals(ChatScope.General, ChatScope.of())
    }

    @Test
    fun `a node id narrows the roadmap to one lesson`() {
        assertEquals(ChatScope.Node("ml", "supervised"), ChatScope.of("ml", "supervised"))
    }

    @Test
    fun `a block id narrows the lesson to one paragraph`() {
        assertEquals(
            ChatScope.Block("ml", "supervised", "b12ab"),
            ChatScope.of("ml", "supervised", "b12ab"),
        )
    }

    @Test
    fun `the tinkerer flag is what tells a guide apart from a roadmap`() {
        assertEquals(ChatScope.Guide("omelette"), ChatScope.of(pathId = "omelette", tinkerer = true))
        assertEquals(ChatScope.Path("omelette"), ChatScope.of(pathId = "omelette"))
    }

    @Test
    fun `a guide and a roadmap with the same id do not share a conversation`() {
        assertNotEquals(
            ChatScope.Guide("omelette").chatId,
            ChatScope.Path("omelette").chatId,
        )
    }

    @Test
    fun `each tier gets its own conversation`() {
        val ids = listOf(
            ChatScope.General,
            ChatScope.Path("ml"),
            ChatScope.Node("ml", "supervised"),
            ChatScope.Block("ml", "supervised", "b12ab"),
            ChatScope.Guide("ml"),
            ChatScope.Step("ml", "s1"),
        ).map { it.chatId }

        assertEquals("every scope needs a distinct chat id", ids.size, ids.toSet().size)
    }

    @Test
    fun `two blocks of one lesson are two conversations`() {
        assertNotEquals(
            ChatScope.Block("ml", "supervised", "b111").chatId,
            ChatScope.Block("ml", "supervised", "b222").chatId,
        )
    }

    /**
     * The node- and guide-level ids are the ones that already have conversations stored against
     * them in Firestore. Changing either would orphan history that is still being read.
     */
    @Test
    fun `the chat ids that already have stored history keep their shape`() {
        assertEquals("ml__supervised", ChatScope.Node("ml", "supervised").chatId)
        assertEquals("tinker__omelette", ChatScope.Guide("omelette").chatId)
        assertEquals("general", ChatScope.General.chatId)
    }

    /**
     * The subtopic screen finds a block's indicator by stripping the node prefix off each stored
     * chat id, so the block id has to be all that is left.
     */
    @Test
    fun `a block chat id is its node chat id plus the block id`() {
        val node = ChatScope.Node("ml", "supervised")
        val block = ChatScope.Block("ml", "supervised", "b12ab")

        assertEquals("b12ab", block.chatId.removePrefix("${node.chatId}__"))
    }
}

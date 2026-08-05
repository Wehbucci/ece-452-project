package com.example.grasp.data.repository

import com.example.grasp.data.model.LessonBlock
import com.example.grasp.data.model.paragraphs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks the starter library the app actually ships.
 *
 * These examples are hand-authored rather than generated, which means they can be wrong in ways a
 * generator's output cannot: a child id that names no node, a title too long for a node circle, a
 * lesson with a heading and no prose under it. None of that surfaces until a real account seeds
 * them, by which point the bad copy is in somebody's library — and seeding runs once, so it does
 * not get fixed by trying again. Reading the shipped asset from disk is the point: a test over a
 * fixture would pass while the file everyone gets is broken.
 */
class StarterLibraryTest {

    /** The shipped asset. The unit test's working directory is the module root. */
    private val shipped: StarterContent =
        StarterLibrary.parse(File("src/main/assets/starter_library.json").readText())

    @Test
    fun `ships the two roadmaps and the guide`() {
        assertEquals(
            listOf("how-learning-works", "space-exploration"),
            shipped.paths.map { it.path.id },
        )
        assertEquals(listOf("example-french-press"), shipped.guides.map { it.id })
    }

    @Test
    fun `every roadmap is a single-parent tree rooted at its own id`() {
        shipped.paths.forEach { starter ->
            val nodes = starter.path.nodes
            val ids = nodes.map { it.id }
            assertEquals("root id must equal path id", starter.path.id, ids.first())
            assertEquals("node ids must be unique", ids.size, ids.toSet().size)

            val parentCounts = nodes.flatMap { it.children }.groupingBy { it }.eachCount()
            parentCounts.keys.forEach { childId ->
                assertTrue("dangling child id $childId", childId in ids)
            }
            assertNull("the root is nobody's child", parentCounts[ids.first()])
            ids.drop(1).forEach { id ->
                assertEquals("$id must have exactly one parent", 1, parentCounts[id])
            }

            // parentId is derived from `children`, so the two have to agree.
            nodes.forEach { node ->
                node.children.forEach { childId ->
                    assertEquals(node.id, nodes.first { it.id == childId }.parentId)
                }
            }
        }
    }

    @Test
    fun `node titles fit on a node circle`() {
        shipped.paths.flatMap { it.path.nodes }.forEach { node ->
            assertTrue(
                "'${node.title}' is longer than $MAX_TITLE_WORDS words",
                !isTitleTooLong(node.title),
            )
        }
    }

    @Test
    fun `every node ships a readable lesson`() {
        shipped.paths.forEach { starter ->
            starter.path.nodes.forEach { node ->
                val content = starter.content[node.id]
                assertNotNull("${node.id} has no lesson", content)
                requireNotNull(content)

                assertTrue("${node.id} has no summary", content.summary.isNotBlank())
                assertTrue("${node.id} has no 'why it matters'", content.whyItMatters.isNotBlank())
                // The bar the repository applies when reading a stored lesson back: no prose means
                // it reads as ungenerated and the app writes a fresh one over the top.
                assertTrue("${node.id} has no prose", content.body.paragraphs().isNotEmpty())
                assertEquals("${node.id} time estimate", node.estMinutes, content.estMinutes)
                assertTrue("${node.id} is marked unwritten", node.contentReady)
            }
        }
    }

    @Test
    fun `block ids are unique within a lesson`() {
        shipped.paths.forEach { starter ->
            starter.content.forEach { (nodeId, content) ->
                val ids = content.body.map { it.id }
                assertEquals("$nodeId has duplicate block ids", ids.size, ids.toSet().size)
            }
        }
    }

    @Test
    fun `diagrams are drawable`() {
        shipped.paths.flatMap { it.content.values }
            .flatMap { it.body }
            .filterIsInstance<LessonBlock.Diagram>()
            .forEach { diagram ->
                // Below two items there is nothing to compare, sequence or scale.
                assertTrue("'${diagram.text}' has ${diagram.items.size} item(s)", diagram.items.size >= 2)
                assertTrue("'${diagram.text}' has an unlabelled item", diagram.items.none { it.label.isBlank() })
                if (diagram.kind == com.example.grasp.data.model.DiagramKind.BAR) {
                    assertTrue("'${diagram.text}' has a zero bar", diagram.items.all { it.value > 0f })
                }
                // LessonDiagram silently drops a unit longer than two characters, so one that is
                // longer belongs in the caption instead of looking fine here and vanishing there.
                diagram.unit?.let {
                    assertTrue("'${diagram.text}' has an unrenderable unit '$it'", it.length <= 2)
                }
            }
    }

    @Test
    fun `every lesson links somewhere real`() {
        shipped.paths.forEach { starter ->
            starter.content.forEach { (nodeId, content) ->
                assertTrue("$nodeId has no resources", content.resources.isNotEmpty())
                content.resources.forEach { link ->
                    assertTrue("$nodeId has an untitled link", link.title.isNotBlank())
                    assertTrue("$nodeId links to '${link.url}'", link.url.startsWith("https://"))
                }
            }
        }
    }

    @Test
    fun `guide steps are numbered in the order they are written`() {
        shipped.guides.forEach { guide ->
            assertEquals((1..guide.steps.size).toList(), guide.steps.map { it.order })
            assertEquals(guide.steps.map { "step-${it.order}" }, guide.steps.map { it.id })
            guide.steps.forEach { assertTrue(it.instruction.isNotBlank()) }
        }
    }

    // ── Parser behaviour, on fixtures rather than the shipped file ──────────────────────────

    @Test
    fun `a node with no lesson is dropped rather than shipped blank`() {
        val parsed = StarterLibrary.parse(
            """
            { "paths": [ { "id": "p", "title": "P", "nodes": [
                { "id": "p", "title": "Root", "estMinutes": 5, "children": [],
                  "content": { "summary": "s", "whyItMatters": "w",
                    "body": [ { "type": "paragraph", "text": "prose" } ], "resources": [] } },
                { "id": "empty", "title": "Empty", "estMinutes": 5, "children": [] }
            ] } ] }
            """,
        )
        assertEquals(listOf("p"), parsed.paths.single().path.nodes.map { it.id })
    }

    @Test
    fun `a lesson with no prose is dropped`() {
        val parsed = StarterLibrary.parse(
            """
            { "paths": [ { "id": "p", "title": "P", "nodes": [
                { "id": "p", "title": "Root", "estMinutes": 5, "children": [],
                  "content": { "summary": "s", "whyItMatters": "w",
                    "body": [ { "type": "heading", "text": "Just a heading", "level": 1 } ],
                    "resources": [] } }
            ] } ] }
            """,
        )
        // Nothing usable left, so the roadmap itself is not offered.
        assertTrue(parsed.paths.isEmpty())
    }

    @Test
    fun `invented links are dropped`() {
        val parsed = StarterLibrary.parse(
            """
            { "paths": [ { "id": "p", "title": "P", "nodes": [
                { "id": "p", "title": "Root", "estMinutes": 5, "children": [],
                  "content": { "summary": "s", "whyItMatters": "w",
                    "body": [ { "type": "paragraph", "text": "prose" } ],
                    "resources": [
                      { "title": "Real", "url": "https://example.org/a", "kind": "GUIDE" },
                      { "title": "Relative", "url": "/nowhere" },
                      { "title": "", "url": "https://example.org/b" }
                    ] } }
            ] } ] }
            """,
        )
        val resources = parsed.paths.single().content.getValue("p").resources
        assertEquals(listOf("https://example.org/a"), resources.map { it.url })
        assertEquals(com.example.grasp.data.model.ResourceKind.GUIDE, resources.single().kind)
    }

    @Test
    fun `a missing file leaves an empty library rather than throwing`() {
        val parsed = StarterLibrary.parse("""{ }""")
        assertTrue(parsed.paths.isEmpty())
        assertTrue(parsed.guides.isEmpty())
    }
}

package com.example.grasp.data.repository

import android.util.Log
import com.example.grasp.GraspApp
import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.paragraphs
import com.example.grasp.data.model.ResourceKind
import com.example.grasp.data.model.ResourceLink
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.model.TinkerStep
import com.example.grasp.data.model.TreeNode
import org.json.JSONArray
import org.json.JSONObject

/**
 * The example roadmaps and guides every new account starts with.
 *
 * Home used to advertise "popular topics" that were suggestions and nothing more: tapping one
 * navigated to a path id that had never been created, so it opened an empty roadmap. They are
 * replaced by these, which are REAL saved items — written in `assets/starter_library.json`, seeded
 * into the user's library once, and from then on indistinguishable from something they generated
 * themselves. They can be opened, grown, edited and deleted like anything else, and deleting one
 * is permanent.
 *
 * Structure AND lessons are authored, not generated. An earlier version shipped the roadmaps empty
 * and let the first open build them, which meant the app's front door cost an AI round-trip, came
 * out different for every user, and showed a spinner to someone who had not asked for anything yet.
 * Reading them from an asset instead makes a starter instant, free, identical for everyone, and —
 * because [FirebasePathRepository.seedStarterLibrary] writes the lessons with the roadmap — never
 * regenerated after the one seeding pass.
 *
 * @see StarterPath for why the lessons travel beside the roadmap rather than inside it.
 */
object StarterLibrary {

    /** Marks a topic document as one of these, so it is recognisable in Firestore. */
    const val STARTER_FIELD = "starter"

    /** The file this whole library is authored in, under `app/src/main/assets`. */
    private const val ASSET_NAME = "starter_library.json"

    /** The learner roadmaps a new account finds in its library, lessons included. */
    internal fun learnerExamples(): List<StarterPath> = library().paths

    /** The tinkerer guide a new account finds in its library. */
    internal fun tinkerExamples(): List<TinkerGuide> = library().guides

    /**
     * The bundled roadmap with this id, or null if [id] is not one of ours.
     *
     * This is what makes a starter readable with no network and nothing cached. Its content is in
     * the APK on every device and every install, which is more than the repository can say for
     * anything it has merely written to Firestore — a `downloadState` is a claim about one
     * device's cache, and this is a fact about the app.
     */
    internal fun pathById(id: String): StarterPath? = library().pathById(id)

    /** The bundled guide with this id, or null if [id] is not one of ours. */
    internal fun guideById(id: String): TinkerGuide? = library().guideById(id)

    /** The authored lesson for one node of a bundled roadmap, if both are ours. */
    internal fun contentFor(pathId: String, nodeId: String): GeneratedContent? =
        library().contentFor(pathId, nodeId)

    /** Holds the parsed asset once it has been read successfully. */
    @Volatile
    private var cached: StarterContent? = null

    /**
     * Reads and parses the asset, at most once per process.
     *
     * Only a SUCCESSFUL read is cached. A failure here is the kind that can pass — the commonest
     * one is being called before `GraspApp.onCreate` has set the context it reads assets through —
     * and caching an empty library over that would mean this account never got seeded no matter
     * how many times it was asked. Home and Library ask on every attach, so a retry costs nothing
     * and the first one that lands settles it.
     */
    private fun library(): StarterContent = cached ?: synchronized(this) {
        cached ?: load().also { if (it.paths.isNotEmpty() || it.guides.isNotEmpty()) cached = it }
    }

    private fun load(): StarterContent = try {
        parse(GraspApp.context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() })
    } catch (e: Exception) {
        Log.e("StarterLibrary", "could not read $ASSET_NAME — will retry on the next attach", e)
        StarterContent(emptyList(), emptyList())
    }

    // ── Parsing ─────────────────────────────────────────────────────────────────────────────

    /**
     * Turns the asset's text into the library.
     *
     * Separate from [load] and free of Android APIs so the authored content can be checked by a
     * host-side test: the whole point of these being hand-written is that they are wrong in ways a
     * generator's output could not be — a dangling child id, a title too long for a node, a lesson
     * with no prose in it — and none of that shows up until a real account opens one.
     */
    internal fun parse(json: String): StarterContent {
        val root = JSONObject(json)
        return StarterContent(
            paths = root.objectList("paths").mapNotNull(::parsePath),
            guides = root.objectList("guides").mapNotNull(::parseGuide),
        )
    }

    private fun parsePath(json: JSONObject): StarterPath? {
        val id = json.optString("id").trim().ifEmpty { return null }
        val title = json.optString("title").trim().ifEmpty { return null }
        val entries = json.objectList("nodes").mapNotNull { parseNode(id, it) }
        if (entries.isEmpty()) return null

        // Parents are the inverse of `children`, exactly as the generator derives them.
        val parents = entries.flatMap { entry -> entry.node.children.map { it to entry.node.id } }.toMap()
        return StarterPath(
            path = LearningPath(
                id = id,
                title = title,
                nodes = entries.map { it.node.copy(parentId = parents[it.node.id]) },
                // True by construction: everything this object parses came out of the APK.
                isStarter = true,
            ),
            content = entries.associate { it.node.id to it.content },
        )
    }

    private fun parseNode(pathId: String, json: JSONObject): NodeEntry? {
        val id = json.optString("id").trim().ifEmpty { return null }
        val title = json.optString("title").trim().ifEmpty { return null }
        val estMinutes = json.optInt("estMinutes", 0)
        val content = parseContent(json.optJSONObject("content"), estMinutes) ?: return null
        return NodeEntry(
            node = TreeNode(
                id = id,
                title = title,
                estMinutes = estMinutes,
                children = json.stringList("children"),
                contentRef = "content/$pathId/$id.md",
                // The lesson is right here in the asset, so it is written the moment the roadmap
                // is. Nothing about a starter is ever "not generated yet".
                contentReady = true,
            ),
            content = content,
        )
    }

    /**
     * One authored lesson. Returns null for anything that would open as a blank page, so a typo in
     * the asset costs one node rather than silently shipping an empty lesson.
     *
     * [estMinutes] comes from the node rather than the lesson: the roadmap's estimate and the
     * lesson's are the same number for authored content, and storing it twice invites them to drift.
     */
    private fun parseContent(json: JSONObject?, estMinutes: Int): GeneratedContent? {
        if (json == null) return null
        val summary = json.optString("summary").trim()
        val body = lessonBlocks(json.optJSONArray("body").toValueList())
        // The same bar the repository applies when it reads a stored lesson back: no summary or no
        // prose and it counts as ungenerated, which would put the app straight back to writing one.
        if (summary.isEmpty() || body.paragraphs().isEmpty()) return null
        return GeneratedContent(
            summary = summary,
            whyItMatters = json.optString("whyItMatters").trim(),
            body = body,
            resources = json.objectList("resources").mapNotNull { resource ->
                val title = resource.optString("title").trim()
                val url = resource.optString("url").trim()
                if (title.isEmpty() || !url.startsWith("http")) return@mapNotNull null
                ResourceLink(title, url, resourceKind(resource.optString("kind")))
            },
            estMinutes = estMinutes,
        )
    }

    private fun parseGuide(json: JSONObject): TinkerGuide? {
        val id = json.optString("id").trim().ifEmpty { return null }
        val title = json.optString("title").trim().ifEmpty { return null }
        val steps = json.objectList("steps").mapIndexedNotNull { index, step ->
            val instruction = step.optString("instruction").trim().ifEmpty { return@mapIndexedNotNull null }
            TinkerStep(
                // Order is the authored order, and the id follows it — a guide's steps are a
                // sequence, so there is nothing else either of them could sensibly be.
                id = "step-${index + 1}",
                order = index + 1,
                instruction = instruction,
                detail = step.optString("detail").trim(),
                estMinutes = step.optInt("estMinutes", 0),
            )
        }
        return if (steps.isEmpty()) null else {
            TinkerGuide(id = id, title = title, steps = steps, isStarter = true)
        }
    }

    private fun resourceKind(raw: String): ResourceKind =
        ResourceKind.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
            ?: ResourceKind.ARTICLE

    // ── org.json → plain values ─────────────────────────────────────────────────────────────

    /**
     * Unwraps a `JSONArray` into the `List<Map<String, Any?>>` shape [lessonBlocks] reads.
     *
     * Going through the shared reader rather than parsing blocks here is deliberate: the asset and
     * a stored Firestore lesson are the same shape, and two readers for one shape would drift the
     * first time a block type is added.
     */
    private fun JSONArray?.toValueList(): List<Any?> =
        if (this == null) emptyList() else (0 until length()).map { unwrapJson(opt(it)) }

    private fun unwrapJson(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { unwrapJson(value.opt(it)) }
        is JSONArray -> (0 until value.length()).map { unwrapJson(value.opt(it)) }
        JSONObject.NULL -> null
        else -> value
    }
}

/**
 * Everything the asset holds, already parsed.
 *
 * The lookups live here rather than on [StarterLibrary] so they are reachable without an Android
 * context: the object's own accessors have to read an asset first, which a host test cannot do,
 * and these are exactly the paths whose failure mode is a regenerated lesson written over an
 * authored one rather than anything visibly broken.
 */
internal data class StarterContent(
    val paths: List<StarterPath>,
    val guides: List<TinkerGuide>,
) {
    fun pathById(id: String): StarterPath? = paths.firstOrNull { it.path.id == id }

    fun guideById(id: String): TinkerGuide? = guides.firstOrNull { it.id == id }

    fun contentFor(pathId: String, nodeId: String): GeneratedContent? =
        pathById(pathId)?.content?.get(nodeId)
}

/**
 * One starter roadmap and the lessons that belong to its nodes, keyed by node id.
 *
 * The lessons ride alongside rather than inside [LearningPath] because that is how they are stored:
 * a [TreeNode] is deliberately structure-only (see its docs) and each lesson lives on its own node
 * document. Keeping the two apart here means seeding can hand them straight to the same writer the
 * generator uses, with nothing to unpack in between.
 */
internal data class StarterPath(
    val path: LearningPath,
    val content: Map<String, GeneratedContent>,
)

/** One node as authored: its structure and its lesson, before the two are separated for storage. */
private data class NodeEntry(
    val node: TreeNode,
    val content: GeneratedContent,
)

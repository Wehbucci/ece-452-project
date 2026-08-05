package com.example.grasp.data.repository

import android.util.Log
import com.example.grasp.core.edit.EditAuthor
import com.example.grasp.core.edit.LessonEdit
import com.example.grasp.core.edit.RoadmapEdit
import com.example.grasp.core.edit.applyEdits
import com.example.grasp.core.edit.describeLessonEdits
import com.example.grasp.data.model.DownloadState
import com.example.grasp.data.model.LessonBlock
import com.example.grasp.data.model.LessonRevision
import com.example.grasp.data.model.Mode
import com.example.grasp.data.model.asRevision
import com.example.grasp.data.model.restoredTo
import com.example.grasp.data.model.TreeNode
import com.example.grasp.data.model.ChatMessage
import com.example.grasp.data.model.LearningPath
import com.example.grasp.data.model.paragraphs
import com.example.grasp.data.model.ResourceKind
import com.example.grasp.data.model.ResourceLink
import com.example.grasp.data.model.SavedItem
import com.example.grasp.data.model.Subtopic
import com.example.grasp.data.model.TinkerGuide
import com.example.grasp.data.model.TinkerStep
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.withTimeout
import com.example.grasp.GraspApp
import com.example.grasp.core.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebasePathRepository : PathRepository {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val uid: String? get() = auth.currentUser?.uid

    // Background scope for "Fire and Forget" cloud saves
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val networkMonitor = NetworkMonitor(GraspApp.context)

    private fun userDocRef(uid: String) = db.collection("users").document(uid)

    private fun topicsRef(uid: String) = userDocRef(uid).collection("topics")

    private fun nodesRef(uid: String, topicId: String) =
        topicsRef(uid).document(topicId).collection("nodes")

    /** A node's earlier lesson versions. Its own collection so reading a node never drags them in. */
    private fun revisionsRef(uid: String, topicId: String, nodeId: String) =
        nodesRef(uid, topicId).document(nodeId).collection("revisions")

    /**
     * Drops the in-memory copy of [pathId], so the next read rebuilds it from storage.
     *
     * [activePathCache] short-circuits [learningPath] entirely, and it outlives the screen that
     * filled it — it is static, so backing out of a roadmap and opening it again hits the same
     * stale object. Anything that changes a roadmap must therefore either replace that copy or
     * throw it away; a write that does neither is invisible until the process restarts.
     *
     * Throwing it away is safe offline as well as on: the pending write is already applied to
     * Firestore's local cache, so the re-read returns the new state rather than the old one.
     */
    private fun invalidatePathCache(pathId: String) {
        if (activePathCache?.id == pathId) activePathCache = null
    }

    /**
     * Writes the [StarterLibrary] examples — roadmaps, nodes and lessons — into this user's
     * library, exactly once per account.
     *
     * Guarded by a flag on the user document rather than by "is the library empty", because those
     * differ in the one case that matters: a user who deleted the examples has an empty library
     * and must NOT get them back. Deleting a starter is a decision, not a gap to be refilled.
     *
     * Also guarded in-process by [seedCheckedForUid], and serialised by [seedMutex] because Home
     * and Library both call this on attach: without the lock the loser of that race repeats the
     * whole batch, since the flag it would have checked has not been written yet.
     *
     * The whole library goes in ONE batch, the flag included. Nothing here is generated, so there
     * is no slow step to die in the middle of — and an atomic write is what guarantees the state
     * this is meant to prevent can never exist: a starter roadmap on the shelf with no nodes
     * inside it, which [com.example.grasp.ui.feature.path.PathPresenter] would read as "generate
     * this now" and rebuild from the AI, at which point the user's front door is neither free nor
     * the same twice.
     *
     * Works offline. Every byte of a starter ships in the APK, so there is nothing to fetch — the
     * SDK applies the batch to the local cache immediately and replays it on reconnect, which is
     * why a first launch with no network still opens onto a full library rather than an empty one.
     */
    override suspend fun seedStarterLibrary() {
        val uid = uid ?: return
        if (seedCheckedForUid == uid) return

        seedMutex.withLock {
            if (seedCheckedForUid == uid) return

            val online = networkMonitor.isOnline()
            try {
                val userDoc = readUserDoc(uid, online) ?: return
                if (userDoc.getBoolean(STARTERS_SEEDED) == true) {
                    seedCheckedForUid = uid
                    return
                }

                val starters = StarterLibrary.learnerExamples()
                val guides = StarterLibrary.tinkerExamples()
                // An asset that could not be read leaves the library empty, and setting the flag
                // over that would make an empty front door permanent for this account. Leaving it
                // unset costs one document read on the next launch and nothing else.
                if (starters.isEmpty() && guides.isEmpty()) {
                    Log.e("FirebasePathRepo", "starter library is empty; not seeding or flagging")
                    return
                }

                val batch = db.batch()
                starters.forEach { starter -> stageStarterPath(batch, uid, starter, online) }
                guides.forEach { guide -> stageStarterGuide(batch, uid, guide, online) }
                batch.set(userDocRef(uid), mapOf(STARTERS_SEEDED to true), SetOptions.merge())

                // Committing applies the whole batch to the local cache immediately; the returned
                // task resolves only when the SERVER acknowledges it. That difference is the whole
                // offline story — the starters are readable the instant this line runs, and the
                // acknowledgement is worth waiting for only when there is a server to give one.
                val commit = batch.commit()
                if (online) {
                    // Bounded, because a captive portal answers isOnline() yes and then swallows
                    // the request, and Library waits on this before it lists anything. Giving up
                    // on the ack loses nothing: the SDK replays the batch when it reconnects.
                    withTimeout(SEED_COMMIT_TIMEOUT_MS) { commit.await() }
                }

                seedCheckedForUid = uid
            } catch (e: Exception) {
                // A library without its examples is a worse library, not a broken one — and
                // leaving the flag unset means the next launch simply tries again.
                Log.e("FirebasePathRepo", "seedStarterLibrary failed", e)
            }
        }
    }

    /**
     * The user document, or null when we must not act on what we can see.
     *
     * Offline this reads the CACHE only, and a MISS is disqualifying rather than a green light.
     * An absent user document and an uncached one look identical from here, and only one of them
     * means "new account" — guessing wrong resurrects starters somebody deliberately deleted.
     * A brand-new account has one either way, because signing up writes the username before Home
     * is ever attached.
     */
    private suspend fun readUserDoc(uid: String, online: Boolean): DocumentSnapshot? = try {
        if (online) {
            withTimeout(USER_DOC_TIMEOUT_MS) { userDocRef(uid).get(Source.DEFAULT).await() }
        } else {
            userDocRef(uid).get(Source.CACHE).await().takeIf { it.exists() }
        }
    } catch (e: Exception) {
        // Offline with nothing cached for this account. Not an error — just not knowable yet.
        if (online) throw e else null
    }

    /**
     * Adds one starter roadmap and every lesson in it to [batch].
     *
     * Lessons go in through the same [nodeDoc] the generator's own writer uses, so an authored
     * lesson and a generated one are the same document — which is what makes a starter openable,
     * editable and undoable like anything else the user made.
     */
    private suspend fun stageStarterPath(
        batch: WriteBatch,
        uid: String,
        starter: StarterPath,
        online: Boolean,
    ) {
        val path = starter.path
        val docRef = topicsRef(uid).document(path.id)
        if (alreadyThere(docRef, online)) return // Do NOT overwrite existing progress

        path.nodes.forEachIndexed { index, node ->
            batch.set(
                nodesRef(uid, path.id).document(node.id),
                nodeDoc(node, index, node.parentId, starter.content[node.id]),
                SetOptions.merge(),
            )
        }

        batch.set(
            docRef,
            mapOf(
                "title" to path.title,
                "mode" to "learner",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "status" to "active",
                StarterLibrary.STARTER_FIELD to true,
            ),
            SetOptions.merge(),
        )
    }

    private suspend fun stageStarterGuide(
        batch: WriteBatch,
        uid: String,
        guide: TinkerGuide,
        online: Boolean,
    ) {
        val docRef = topicsRef(uid).document(guide.id)
        if (alreadyThere(docRef, online)) return // Do NOT overwrite existing progress

        batch.set(
            docRef,
            mapOf(
                "title" to guide.title,
                "mode" to "tinker",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "status" to "active",
                StarterLibrary.STARTER_FIELD to true,
                "steps" to guide.steps.map { step ->
                    mapOf(
                        "id" to step.id,
                        "order" to step.order,
                        "instruction" to step.instruction,
                        "detail" to step.detail,
                        "estMinutes" to step.estMinutes,
                        "done" to step.done,
                    )
                },
            ),
            SetOptions.merge(),
        )
    }

    /**
     * Whether [docRef] already holds something, so seeding leaves it alone.
     *
     * Offline this can only see the cache, and a miss reads as "not there". That is safe only
     * because of where it is called from: [seedStarterLibrary] has already established this
     * account has never been seeded, so there is nothing of the user's on the server for a cold
     * cache to be hiding.
     */
    private suspend fun alreadyThere(docRef: DocumentReference, online: Boolean): Boolean = try {
        docRef.get(if (online) Source.DEFAULT else Source.CACHE).await().exists()
    } catch (e: Exception) {
        if (online) throw e else false
    }

    /**
     * The account's finished-lesson count, remembered between calls.
     *
     * The interface's default recomputes this by reading the WHOLE library, and every roadmap open
     * asks for it — the HUD shows an account-wide level, which one roadmap cannot produce on its
     * own. Paying a full library scan to draw a board that is already in hand was the single
     * biggest cost of opening anything, and it fell hardest offline.
     *
     * Kept exact rather than merely fresh: the two places that change a completion adjust this by
     * the same step they just wrote, and the places that can remove a completed item outright drop
     * it. Static, like the other caches here, because each screen builds its own repository.
     */
    override suspend fun totalLessonsMastered(): Int {
        masteredTotal?.let { return it }
        return withContext(Dispatchers.IO) {
            savedItems().sumOf { it.lessonsMastered }.also { masteredTotal = it }
        }
    }

    /** Moves the memoised total by one, if we are holding one. */
    private fun nudgeMasteredTotal(completed: Boolean) {
        masteredTotal = masteredTotal?.plus(if (completed) 1 else -1)?.coerceAtLeast(0)
    }

    /**
     * What an item reports as its download state: [DownloadState.AVAILABLE] for a starter, whatever
     * is stored for everything else.
     *
     * Derived here rather than written to Firestore, and that distinction is the whole point. The
     * stored field is one document shared by all of an account's devices, so writing AVAILABLE into
     * it would be claiming, on every device, that a particular device's cache holds the content.
     * Being a starter is not a claim about a cache — the lessons are in the APK — so it is true
     * wherever it is read, and it cannot go stale.
     */
    private fun reportedState(stored: DownloadState, isStarter: Boolean) =
        if (isStarter) DownloadState.AVAILABLE else stored

    /**
     * One library row built entirely from the local cache, or null if it may not be shown offline.
     *
     * Split out of [savedItems] so the offline branch can run one of these per topic concurrently.
     */
    private suspend fun cachedSavedItem(uid: String, doc: DocumentSnapshot): SavedItem? {
        val id = doc.id
        val mode = doc.getString("mode") ?: "learner"
        val title = doc.getString("title") ?: id.replace('-', ' ').replaceFirstChar { it.uppercase() }
        val downloadState = try {
            DownloadState.valueOf(doc.getString("downloadState") ?: DownloadState.NONE.name)
        } catch (e: Exception) {
            if (doc.getBoolean("isDownloaded") == true) DownloadState.AVAILABLE else DownloadState.NONE
        }

        // Starters list offline without being downloaded — their content ships in the APK. Note
        // this reads the CACHED topic document, so one the user deleted is not here to be listed:
        // the exemption cannot resurrect anything.
        val isStarter = doc.getBoolean(StarterLibrary.STARTER_FIELD) == true
        if (downloadState != DownloadState.AVAILABLE && !isStarter) return null

        return if (mode == "tinker" || mode == "tinkerer") {
            val steps = (doc.get("steps") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
            TinkerGuide(
                id = id,
                title = title,
                steps = steps.map { step ->
                    TinkerStep(
                        id = step["id"] as? String ?: "",
                        order = (step["order"] as? Long)?.toInt() ?: 0,
                        instruction = step["instruction"] as? String ?: "",
                        detail = step["detail"] as? String ?: "",
                        estMinutes = (step["estMinutes"] as? Long)?.toInt() ?: 0,
                        done = step["done"] as? Boolean ?: false,
                    )
                },
                downloadState = reportedState(downloadState, isStarter),
                isStarter = isStarter,
            )
        } else {
            val nodesSnap = nodesRef(uid, id).get(Source.CACHE).await()
            val nodes = nodesSnap.documents.mapNotNull { it.toTreeNode() }
                .ifEmpty { StarterLibrary.pathById(id)?.path?.nodes.orEmpty() }
            LearningPath(
                id = id,
                title = title,
                nodes = nodes,
                downloadState = reportedState(downloadState, isStarter),
                isStarter = isStarter,
            )
        }
    }

    override suspend fun savedItems(forceCache: Boolean): List<SavedItem> = withContext(Dispatchers.IO) {
        val uid = uid ?: return@withContext emptyList()
        val source = if (forceCache || !networkMonitor.isOnline()) Source.CACHE else Source.DEFAULT

        try {
            // If offline, strictly read from CACHE only.
            if (source == Source.CACHE) {
                val snapshot = topicsRef(uid).get(Source.CACHE).await()
                // Every roadmap's nodes are read AT ONCE. This used to be a plain `mapNotNull`,
                // which made each roadmap's node query wait for the one before it — and since
                // opening any single roadmap costs a whole-library read (for the account's XP
                // total), that serialisation was most of what made offline feel slow. Both online
                // branches below already fan out; this one had simply lost the `async`.
                return@withContext coroutineScope {
                    snapshot.documents.map { doc ->
                        async { cachedSavedItem(uid, doc) }
                    }.awaitAll().filterNotNull()
                }
            }

            // Online path: load all topic documents first
            val snapshot = withTimeout(3000L) { topicsRef(uid).get(Source.DEFAULT).await() }

            snapshot.documents.map { doc ->
                async {
                    val id = doc.id
                    val mode = doc.getString("mode") ?: "learner"
                    if (mode == "tinker") {
                        tinkerGuide(id)
                    } else {
                        learningPath(id)
                    }
                }
            }.awaitAll().filterNotNull().mapNotNull { item ->
                // Filter out non-available items when strictly loading from cache after failure
                if (!networkMonitor.isOnline() && item.downloadState != DownloadState.AVAILABLE) null else item
            }
        } catch (e: Exception) {
            Log.w("FirebasePathRepo", "savedItems failed (source=$source), falling back to CACHE: ${e.message}")
            if (source == Source.DEFAULT) {
                try {
                    val cacheSnapshot = topicsRef(uid).get(Source.CACHE).await()
                    cacheSnapshot.documents.map { doc ->
                        async {
                            val id = doc.id
                            val mode = doc.getString("mode") ?: "learner"
                            val item = if (mode == "tinker") {
                                tinkerGuide(id)
                            } else {
                                learningPath(id)
                            }
                            // SECURITY: Only return items that are explicitly available for offline use
                            if (item?.downloadState == DownloadState.AVAILABLE) item else null
                        }
                    }.awaitAll().filterNotNull()
                } catch (cacheEx: Exception) {
                    emptyList()
                }
            } else emptyList()
        }
    }

    override suspend fun learningPath(id: String): LearningPath? = withContext(Dispatchers.IO) {
        val uid = uid ?: return@withContext FakePathRepository.learningPath(id)

        // Fast path: use the in-memory cache if it matches the requested id.
        activePathCache?.takeIf { it.id == id }?.let { return@withContext it }

        // If we are offline, strictly read from CACHE only.
        val source = if (networkMonitor.isOnline()) Source.DEFAULT else Source.CACHE

        try {
            val topicDoc = if (source == Source.DEFAULT) {
                withTimeout(1500L) { topicsRef(uid).document(id).get(Source.DEFAULT).await() }
            } else {
                topicsRef(uid).document(id).get(Source.CACHE).await()
            }

            if (!topicDoc.exists()) return@withContext null

            // A tinker guide's doc exists in the same collection too — don't treat it as a roadmap.
            val docMode = topicDoc.getString("mode") ?: "learner"
            if (docMode != "learner") return@withContext null

            val topicTitle = topicDoc.getString("title") ?: id.replace('-', ' ').replaceFirstChar { it.uppercase() }
            val downloadState = try {
                DownloadState.valueOf(topicDoc.getString("downloadState") ?: DownloadState.NONE.name)
            } catch (e: Exception) {
                if (topicDoc.getBoolean("isDownloaded") == true) DownloadState.AVAILABLE else DownloadState.NONE
            }

            // SECURITY/ROBUSTNESS: If offline, and not marked AVAILABLE, do not return it. A
            // starter is exempt: this guard exists to avoid offering content we do not hold, and a
            // starter's content is in the APK — it needs no download and cannot fail to be there.
            val isStarter = topicDoc.getBoolean(StarterLibrary.STARTER_FIELD) == true
            if (source == Source.CACHE && downloadState != DownloadState.AVAILABLE && !isStarter) {
                Log.d("FirebasePathRepo", "Topic $id found in cache but not marked for offline use. Denying access.")
                return@withContext null
            }

            val nodesSnap = if (source == Source.DEFAULT) {
                withTimeout(1500L) { nodesRef(uid, id).get(Source.DEFAULT).await() }
            } else {
                nodesRef(uid, id).get(Source.CACHE).await()
            }

            val storedNodes = nodesSnap.documents
                .mapNotNull { doc ->
                    val node = doc.toTreeNode() ?: return@mapNotNull null
                    val order = doc.getLong("order")?.toInt() ?: Int.MAX_VALUE
                    Pair(order, node)
                }
                .sortedBy { it.first }
                .map { it.second }

            // A starter whose nodes are not in this cache — a reinstall, a cleared cache, a second
            // device — is rebuilt from the APK rather than handed back empty. Empty is the one
            // shape PathPresenter reads as "generate this", which offline fails and online quietly
            // replaces authored lessons with model output.
            val nodes = storedNodes.ifEmpty {
                StarterLibrary.pathById(id)?.path?.nodes.orEmpty().also {
                    if (it.isNotEmpty()) Log.d("FirebasePathRepo", "Serving $id from the bundled asset")
                }
            }

            val path = LearningPath(
                id = id,
                title = topicTitle,
                nodes = nodes,
                downloadState = reportedState(downloadState, isStarter),
                isStarter = isStarter,
            )
            activePathCache = path

            // Auto-sync: if online and already downloaded, ensure all lessons are cached.
            // ONLY if nodes were actually found; an empty path (just generated/starter) doesn't need sync yet.
            if (source == Source.DEFAULT && downloadState == DownloadState.AVAILABLE && nodes.isNotEmpty()) {
                backgroundScope.launch {
                    val lessons = nodes.filter { !it.isBranchOut && !it.contentReady }
                    if (lessons.isNotEmpty()) {
                        Log.d("FirebasePathRepo", "Auto-syncing ${lessons.size} lessons for $id")
                        lessons.forEach { subtopic(id, it.id) }
                    }
                }
            }
            path
        } catch (e: Exception) {
            if (source == Source.DEFAULT) {
                // Try cache on failure
                try {
                    val topicDoc = topicsRef(uid).document(id).get(Source.CACHE).await()

                    if (!topicDoc.exists()) return@withContext null

                    // Same guard on the cache-fallback read.
                    val docMode = topicDoc.getString("mode") ?: "learner"
                    if (docMode != "learner") return@withContext null

                    val downloadStateStr = topicDoc.getString("downloadState") ?: DownloadState.NONE.name
                    val stored = try {
                        DownloadState.valueOf(downloadStateStr)
                    } catch (ex: Exception) {
                        if (topicDoc.getBoolean("isDownloaded") == true) DownloadState.AVAILABLE else DownloadState.NONE
                    }
                    val isStarter = topicDoc.getBoolean(StarterLibrary.STARTER_FIELD) == true
                    val downloadState = reportedState(stored, isStarter)

                    // If marked AVAILABLE, return it from cache. Otherwise null.
                    if (downloadState == DownloadState.AVAILABLE) {
                        val topicTitle = topicDoc.getString("title") ?: id.replace('-', ' ').replaceFirstChar { it.uppercase() }
                        val nodesSnap = nodesRef(uid, id).get(Source.CACHE).await()
                        val nodes = nodesSnap.documents
                            .mapNotNull { doc ->
                                val node = doc.toTreeNode() ?: return@mapNotNull null
                                val order = doc.getLong("order")?.toInt() ?: Int.MAX_VALUE
                                Pair(order, node)
                            }
                            .sortedBy { it.first }
                            .map { it.second }
                        val path = LearningPath(
                            id = id,
                            title = topicTitle,
                            nodes = nodes,
                            downloadState = downloadState,
                            isStarter = isStarter,
                        )
                        activePathCache = path
                        path
                    } else if (topicDoc.exists()) {
                        // Topic exists in cache but not fully available?
                        // Return it anyway if it has nodes, to allow instant local viewing while online fetch fails.
                        val topicTitle = topicDoc.getString("title") ?: id.replace('-', ' ').replaceFirstChar { it.uppercase() }
                        val nodesSnap = nodesRef(uid, id).get(Source.CACHE).await()
                        val nodes = nodesSnap.documents
                            .mapNotNull { doc ->
                                val node = doc.toTreeNode() ?: return@mapNotNull null
                                val order = doc.getLong("order")?.toInt() ?: Int.MAX_VALUE
                                Pair(order, node)
                            }
                            .sortedBy { it.first }
                            .map { it.second }
                        if (nodes.isNotEmpty()) {
                            val path = LearningPath(
                            id = id,
                            title = topicTitle,
                            nodes = nodes,
                            downloadState = downloadState,
                            isStarter = isStarter,
                        )
                            activePathCache = path
                            path
                        } else null
                    } else {
                        null
                    }
                } catch (cacheEx: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }

    override suspend fun tinkerGuide(id: String): TinkerGuide? = withContext(Dispatchers.IO) {
        val uid = uid ?: return@withContext null
        val source = if (networkMonitor.isOnline()) Source.DEFAULT else Source.CACHE

        try {
            val doc = topicsRef(uid).document(id).get(source).await()
            if (!doc.exists()) return@withContext null

            val downloadState = try {
                DownloadState.valueOf(doc.getString("downloadState") ?: DownloadState.NONE.name)
            } catch (e: Exception) {
                if (doc.getBoolean("isDownloaded") == true) DownloadState.AVAILABLE else DownloadState.NONE
            }

            // SECURITY/ROBUSTNESS: If offline, and not marked AVAILABLE, do not return it — unless
            // it is a starter, whose steps are in the APK and need no download. See [learningPath].
            val isStarter = doc.getBoolean(StarterLibrary.STARTER_FIELD) == true
            if (source == Source.CACHE && downloadState != DownloadState.AVAILABLE && !isStarter) {
                return@withContext null
            }

            val title = doc.getString("title") ?: id.replace('-', ' ').replaceFirstChar { it.uppercase() }
            val steps = (doc.get("steps") as? List<*>).orEmpty()
                .filterIsInstance<Map<*, *>>()
                .mapIndexedNotNull { index, raw ->
                    val instruction = raw["instruction"] as? String ?: return@mapIndexedNotNull null
                    TinkerStep(
                        id = raw["id"] as? String ?: "step-${index + 1}",
                        order = (raw["order"] as? Long)?.toInt() ?: index + 1,
                        instruction = instruction,
                        detail = raw["detail"] as? String ?: "",
                        estMinutes = (raw["estMinutes"] as? Long)?.toInt() ?: 0,
                        done = raw["done"] as? Boolean ?: false,
                    )
                }
                .sortedBy { it.order }
            TinkerGuide(
                id = id,
                title = title,
                steps = steps,
                downloadState = reportedState(downloadState, isStarter),
                isStarter = isStarter,
            )
        } catch (e: Exception) {
            if (source == Source.DEFAULT) {
                try {
                    val doc = topicsRef(uid).document(id).get(Source.CACHE).await()
                    val downloadStateStr = doc.getString("downloadState") ?: DownloadState.NONE.name
                    val stored = try {
                        DownloadState.valueOf(downloadStateStr)
                    } catch (ex: Exception) {
                        if (doc.getBoolean("isDownloaded") == true) DownloadState.AVAILABLE else DownloadState.NONE
                    }
                    val isStarter = doc.getBoolean(StarterLibrary.STARTER_FIELD) == true
                    val downloadState = reportedState(stored, isStarter)

                    if (downloadState == DownloadState.AVAILABLE) {
                        val title = doc.getString("title") ?: id.replace('-', ' ').replaceFirstChar { it.uppercase() }
                        val steps = (doc.get("steps") as? List<*>).orEmpty()
                            .filterIsInstance<Map<*, *>>()
                            .mapIndexedNotNull { index, raw ->
                                val instruction = raw["instruction"] as? String ?: return@mapIndexedNotNull null
                                TinkerStep(
                                    id = raw["id"] as? String ?: "step-${index + 1}",
                                    order = (raw["order"] as? Long)?.toInt() ?: index + 1,
                                    instruction = instruction,
                                    detail = raw["detail"] as? String ?: "",
                                    estMinutes = (raw["estMinutes"] as? Long)?.toInt() ?: 0,
                                    done = raw["done"] as? Boolean ?: false,
                                )
                            }
                            .sortedBy { it.order }
                        TinkerGuide(
                            id = id,
                            title = title,
                            steps = steps,
                            downloadState = downloadState,
                            isStarter = isStarter,
                        )
                    } else {
                        null
                    }
                } catch (cacheEx: Exception) {
                    null
                }
            } else null
        }
    }

    /**
     * Resolves a node's lesson, reading the copy [createTopic] cached on the node document.
     *
     * Falls back to generating it here for anything that copy is missing — a node whose up-front
     * generation failed, or one restored from an older topic. Every later open, including offline,
     * reads the cache.
     */
    override suspend fun subtopic(pathId: String, nodeId: String): Subtopic? =
        withContext(Dispatchers.IO) {
            val uid = uid ?: return@withContext FakePathRepository.subtopic(pathId, nodeId)
            try {
                // Use cache first if available
                val path = activePathCache?.takeIf { it.id == pathId } ?: learningPath(pathId)
                if (path == null) return@withContext null

                val node = path.nodes.firstOrNull { it.id == nodeId } ?: return@withContext null

                val source = if (networkMonitor.isOnline()) Source.DEFAULT else Source.CACHE
                val cached = try {
                    if (source == Source.DEFAULT) {
                        withTimeout(1500L) { nodesRef(uid, pathId).document(nodeId).get(Source.DEFAULT).await().toGeneratedContent() }
                    } else {
                        nodesRef(uid, pathId).document(nodeId).get(Source.CACHE).await().toGeneratedContent()
                    }
                } catch (e: Exception) {
                    if (source == Source.DEFAULT) {
                        nodesRef(uid, pathId).document(nodeId).get(Source.CACHE).await().toGeneratedContent()
                    } else null
                }

                // The bundled lesson comes before generating one. For a starter this is the SAME
                // text that was seeded, so a cache that has lost it costs nothing — and without
                // this, a starter opened on a cold cache would try to write a replacement, which
                // offline fails outright and online silently supplants the authored lesson.
                val content = cached
                    ?: StarterLibrary.contentFor(pathId, nodeId)
                    ?: generateAndCache(uid, pathId, path, node)

                // Lessons are what get numbered; the branch-out affordance isn't one.
                val lessons = path.nodes.filter { !it.isBranchOut }
                val position = lessons.indexOfFirst { it.id == nodeId }
                Subtopic(
                    nodeId = node.id,
                    title = node.title,
                    sectionLabel = "Section ${position + 1} of ${lessons.size}",
                    summary = content.summary,
                    whyItMatters = content.whyItMatters,
                    body = content.body,
                    resources = content.resources,
                    estMinutes = content.estMinutes,
                    completed = node.completed,
                    edited = content.edited,
                )
            } catch (e: Exception) {
                Log.e("FirebasePathRepo", "subtopic failed for $pathId/$nodeId", e)
                FakePathRepository.subtopic(pathId, nodeId)
            }
        }

    /**
     * Writes the lesson for every node in [lessons], several at a time.
     *
     * Concurrent because a roadmap is 6-12 nodes and doing them one after another would take
     * minutes; [MAX_PARALLEL_GENERATIONS] keeps the burst polite to the AI service. Nodes whose
     * generation fails are simply absent from the result — they fall back to being written on
     * first open rather than sinking the whole roadmap.
     */
    private suspend fun generateContentFor(
        pathTitle: String,
        lessons: List<TreeNode>,
    ): Map<String, GeneratedContent> = coroutineScope {
        val gate = Semaphore(MAX_PARALLEL_GENERATIONS)
        lessons
            .mapIndexed { index, node ->
                async {
                    val content = gate.withPermit {
                        generateSubtopicContent(
                            pathTitle = pathTitle,
                            nodeTitle = node.title,
                            previousTitles = lessons.take(index).takeLast(3).map { it.title },
                            upcomingTitles = lessons.drop(index + 1).take(2).map { it.title },
                            estMinutes = node.estMinutes,
                        )
                    }
                    if (content != null) node.id to content else null
                }
            }
            .awaitAll()
            .filterNotNull()
            .toMap()
    }

    /**
     * Writes the lesson for [node] and stores it on the node document so it is only ever paid for
     * once. A failed generation is NOT cached — reopening the node retries.
     */
    private suspend fun generateAndCache(
        uid: String,
        pathId: String,
        path: LearningPath,
        node: TreeNode,
    ): GeneratedContent {
        val lessons = path.nodes.filter { !it.isBranchOut }
        val position = lessons.indexOfFirst { it.id == node.id }.coerceAtLeast(0)

        val prefs = FirebaseUserRepository().getPreferences()

        val generated = generateSubtopicContent(
            pathTitle = path.title,
            nodeTitle = node.title,
            // Only the immediate neighbours: enough to place the lesson without bloating the prompt.
            previousTitles = lessons.take(position).takeLast(3).map { it.title },
            upcomingTitles = lessons.drop(position + 1).take(2).map { it.title },
            estMinutes = node.estMinutes,
            prefs = prefs
        ) ?: return placeholderContent(node.title, node.estMinutes)

        try {
            nodesRef(uid, pathId).document(node.id).set(
                contentFields(generated) + ("updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            ).await()
        } catch (e: Exception) {
            // The lesson is already written — failing to cache it only costs a re-generation.
            Log.e("FirebasePathRepo", "caching content failed for $pathId/${node.id}", e)
        }
        return generated
    }

    override suspend fun growBranch(
        pathId: String,
        fromNodeId: String,
        topic: String,
    ): List<TreeNode> = withContext(Dispatchers.IO) {
        val path = learningPath(pathId) ?: return@withContext emptyList()
        val from = path.nodes.firstOrNull { it.id == fromNodeId } ?: return@withContext emptyList()

        val generated = buildBranch(
            pathId = pathId,
            pathTitle = path.title,
            fromTitle = from.title,
            topic = topic,
            takenIds = path.nodes.mapTo(mutableSetOf()) { it.id },
        )
        val branch = generated.mapIndexed { index, node ->
            if (index == 0) node.copy(parentId = from.id) else node
        }

        // Same deal as a new topic: the branch's lessons are written now, not on first open.
        val content = generateContentFor(path.title, branch.filter { !it.isBranchOut })
        val grown = branch.map { node ->
            content[node.id]?.let { node.copy(estMinutes = it.estMinutes) } ?: node
        }

        val uid = uid ?: return@withContext grown // signed out: the branch lives in memory only
        try {
            // 1) The new nodes, appended after everything already on the path.
            grown.forEachIndexed { index, node ->
                nodesRef(uid, pathId).document(node.id)
                    .set(
                        nodeDoc(node, order = path.nodes.size + index, content = content[node.id]),
                        SetOptions.merge(),
                    )
                    .await()
            }
            // 2) Hang the branch off the node the user picked, keeping everything it already led to.
            nodesRef(uid, pathId).document(from.id).set(
                mapOf(
                    "children" to from.children + grown.first().id,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
            topicsRef(uid).document(pathId)
                .set(mapOf("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("FirebasePathRepo", "growBranch failed for $pathId/$fromNodeId", e)
        }
        // The roadmap has new nodes and `from` has a new child; only storage knows the whole shape.
        invalidatePathCache(pathId)
        grown
    }

    override suspend fun branchSuggestions(pathId: String, fromNodeId: String): List<String> =
        withContext(Dispatchers.IO) {
            val path = learningPath(pathId) ?: return@withContext emptyList()
            val from = path.nodes.firstOrNull { it.id == fromNodeId }
            suggestBranchTopics(
                pathTitle = path.title,
                fromTitle = from?.title ?: path.title,
                existingTitles = path.nodes.filter { !it.isBranchOut }.map { it.title },
            )
        }

    override suspend fun sampleChat(): List<ChatMessage> = emptyList()

    override suspend fun createTopic(query: String, mode: Mode): SavedItem? =
        withContext(Dispatchers.IO) {
            val uid = uid ?: return@withContext FakePathRepository.createTopic(query, mode)
            val normalizedId = query.trim().lowercase()
                .replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "topic" }
            val title = query.trim().replaceFirstChar { it.uppercase() }

            when (mode) {
                Mode.LEARNER -> createLearnerTopic(uid, normalizedId, title)
                Mode.TINKERER -> createTinkerTopic(uid, normalizedId, title)
            }
        }

    /**
     * A learner roadmap: the tree, then every lesson in it, written up front.
     *
     * Re-running a topic the user already has (same query, same normalised id) must NOT write over
     * lessons they have edited by hand (FR4.5) — hence the pre-read of which nodes are marked
     * `edited`, which is what tells [nodeDoc] to leave their stored content alone.
     *
     * A starter is refused outright, before anything is generated. Titles normalise to ids, so
     * typing "Space Exploration" into the create field lands on precisely the id a starter already
     * occupies — and regenerating there would swap hand-written lessons for model output that only
     * happens to be about the same subject. The user asked for that topic and already has it, so
     * they get the one they have.
     */
    private suspend fun createLearnerTopic(uid: String, normalizedId: String, title: String): LearningPath? {
        val docRef = topicsRef(uid).document(normalizedId)
        val existing = try {
            docRef.get().await()
        } catch (e: Exception) {
            Log.e("FirebasePathRepo", "could not check $normalizedId before generating", e)
            null
        }
        if (existing?.getBoolean(StarterLibrary.STARTER_FIELD) == true) {
            Log.d("FirebasePathRepo", "$normalizedId is a starter — opening it, not regenerating")
            return learningPath(normalizedId)
        }

        val prefs = FirebaseUserRepository().getPreferences()
        val nodes = buildLearnerTree(normalizedId, title, prefs)
        // Every lesson is written NOW, so the roadmap is complete and readable (and offline) the
        // moment it opens. [subtopic] still generates on demand for anything missing here.
        val content = generateContentFor(title, nodes.filter { !it.isBranchOut })
        return try {
            docRef.set(
                mapOf(
                    "title" to title,
                    "mode" to "learner",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "status" to "active",
                    // Never a starter: one would have been handed back above rather than reaching
                    // the generator at all.
                    StarterLibrary.STARTER_FIELD to false,
                    "preferences" to mapOf(
                        "difficulty" to "beginner",
                        "length" to "standard",
                        "format" to "text",
                    ),
                ),
                SetOptions.merge(),
            ).await()

            val storedNodes = nodesRef(uid, normalizedId).get().await().documents
            val editedNodeIds = storedNodes
                .filter { it.getBoolean("edited") == true }
                .mapTo(mutableSetOf()) { it.id }
            // Which sections the user had already finished. A freshly generated tree is completed
            // nowhere, so writing its `completed` over the stored one wiped the user's progress —
            // and with it the XP that progress was worth — for the sole crime of asking for a
            // topic they already had. Progress belongs to the user, not to the generation run.
            val completedNodeIds = storedNodes
                .filter { it.getBoolean("completed") == true }
                .mapTo(mutableSetOf()) { it.id }

            val parentByNodeId = linkedMapOf<String, String?>()
            nodes.forEach { parentByNodeId[it.id] = null }
            nodes.forEach { node -> node.children.forEach { childId -> parentByNodeId[childId] = node.id } }

            nodes.forEachIndexed { index, node ->
                nodesRef(uid, normalizedId).document(node.id)
                    .set(
                        nodeDoc(
                            node.copy(completed = node.completed || node.id in completedNodeIds),
                            index,
                            parentByNodeId[node.id],
                            content[node.id],
                            keepStoredContent = node.id in editedNodeIds,
                        ),
                        SetOptions.merge(),
                    )
                    .await()
            }
            val hydrated = nodes.map { node ->
                val generated = content[node.id]
                val timed = if (generated != null) {
                    node.copy(estMinutes = generated.estMinutes, contentReady = true)
                } else {
                    node
                }
                timed.copy(completed = timed.completed || timed.id in completedNodeIds)
            }
            LearningPath(id = normalizedId, title = title, nodes = hydrated, downloadState = DownloadState.NONE)
        } catch (e: Exception) {
            Log.e("FirebasePathRepo", "createLearnerTopic failed for $title", e)
            FakePathRepository.learningPath(normalizedId)
        }
    }


    /**
     * Tinker steps carry their content inline (no lazy contentRef), so this doesn't touch the
     * node generation at all, the whole guide is a single document write.
     *
     * Returns a [TinkerGuide] purely to satisfy the shared return type; only its
     * `id` gets read by [HomePresenter] before routing to `openTinker(id)`.
     */
    private suspend fun createTinkerTopic(uid: String, normalizedId: String, title: String): TinkerGuide? {
        val prefs = FirebaseUserRepository().getPreferences()
        val guide = buildTinkerGuide(normalizedId, title, prefs)
        return try {
            topicsRef(uid).document(normalizedId).set(
                mapOf(
                    "title" to title,
                    "mode" to "tinker",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "status" to "active",
                    "steps" to guide.steps.map { step ->
                        mapOf(
                            "id" to step.id,
                            "order" to step.order,
                            "instruction" to step.instruction,
                            "detail" to step.detail,
                            "estMinutes" to step.estMinutes,
                            "done" to step.done,
                        )
                    },
                ),
                SetOptions.merge(),
            ).await()
            guide
        } catch (e: Exception) {
            Log.e("FirebasePathRepo", "createTinkerTopic failed for $title", e)
            FakePathRepository.tinkerGuide(normalizedId)
        }
    }

    /**
     * Applies the edits, then writes back ONLY the fields they actually changed.
     *
     * Rewriting the whole node document for a one-word fix would send the entire lesson — every
     * block, every picture credit — over the wire each keystroke-sized change, and would hand the
     * loser of two overlapping edits a document with the winner's work erased. A partial write
     * keeps a summary edit and a paragraph edit from treading on each other at all.
     */
    override suspend fun editLesson(
        pathId: String,
        nodeId: String,
        edits: List<LessonEdit>,
        author: EditAuthor,
    ): Subtopic? = withContext(Dispatchers.IO) {
        val before = subtopic(pathId, nodeId) ?: return@withContext null
        val after = before.applyEdits(edits, author) ?: return@withContext null
        // Signed out there is nowhere to write; the caller still gets the edited lesson.
        val uid = uid ?: return@withContext after

        val changed = changedLessonFields(before, after)
        // The version goes down FIRST. If the write below then fails the user has an extra entry
        // in their history and nothing else; the other order risks a change landing with no way
        // back from it.
        saveRevision(
            uid = uid,
            pathId = pathId,
            nodeId = nodeId,
            revision = before.asRevision(
                id = newRevisionId(),
                savedAt = System.currentTimeMillis(),
                label = describeLessonEdits(edits),
                byAssistant = author == EditAuthor.ASSISTANT,
            ),
        )
        try {
            nodesRef(uid, pathId).document(nodeId).set(
                changed + mapOf(
                    // Recorded even when nothing else changed, because it is what tells a later
                    // generation pass to keep its hands off this lesson.
                    "edited" to true,
                    "contentStatus" to CONTENT_GENERATED,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
            topicsRef(uid).document(pathId)
                .set(mapOf("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("FirebasePathRepo", "editLesson failed for $pathId/$nodeId", e)
        }
        after
    }

    override suspend fun lessonRevisions(pathId: String, nodeId: String): List<LessonRevision> =
        withContext(Dispatchers.IO) {
            val uid = uid ?: return@withContext emptyList()
            try {
                revisionsRef(uid, pathId, nodeId)
                    .orderBy("savedAt", Query.Direction.DESCENDING)
                    .get().await()
                    .documents.mapNotNull { it.toLessonRevision() }
            } catch (e: Exception) {
                Log.e("FirebasePathRepo", "lessonRevisions failed for $pathId/$nodeId", e)
                emptyList()
            }
        }

    override suspend fun undoLastLessonEdit(pathId: String, nodeId: String): Subtopic? =
        withContext(Dispatchers.IO) {
            val uid = uid ?: return@withContext null
            val newest = lessonRevisions(pathId, nodeId).firstOrNull() ?: return@withContext null
            val restored = restoreTo(uid, pathId, nodeId, newest) ?: return@withContext null
            // Consumed, so pressing undo again steps back further instead of bouncing.
            try {
                revisionsRef(uid, pathId, nodeId).document(newest.id).delete().await()
            } catch (e: Exception) {
                Log.e("FirebasePathRepo", "dropping undone revision failed for $pathId/$nodeId", e)
            }
            restored
        }

    override suspend fun restoreLesson(
        pathId: String,
        nodeId: String,
        revisionId: String,
    ): Subtopic? = withContext(Dispatchers.IO) {
        val uid = uid ?: return@withContext null
        val wanted = lessonRevisions(pathId, nodeId).firstOrNull { it.id == revisionId }
            ?: return@withContext null
        val current = subtopic(pathId, nodeId) ?: return@withContext null
        // Kept, not consumed: reverting to something from last week must not cost this morning.
        saveRevision(
            uid = uid,
            pathId = pathId,
            nodeId = nodeId,
            revision = current.asRevision(
                id = newRevisionId(),
                savedAt = System.currentTimeMillis(),
                label = "Went back to an earlier version",
                byAssistant = false,
            ),
        )
        restoreTo(uid, pathId, nodeId, wanted)
    }

    /** Writes [revision]'s content over the node's lesson, returning the lesson as it now stands. */
    private suspend fun restoreTo(
        uid: String,
        pathId: String,
        nodeId: String,
        revision: LessonRevision,
    ): Subtopic? {
        val current = subtopic(pathId, nodeId) ?: return null
        val restored = current.restoredTo(revision)
        try {
            nodesRef(uid, pathId).document(nodeId).set(
                changedLessonFields(current, restored) + mapOf(
                    "edited" to true,
                    "contentStatus" to CONTENT_GENERATED,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
        } catch (e: Exception) {
            Log.e("FirebasePathRepo", "restoring failed for $pathId/$nodeId", e)
            return null
        }
        return restored
    }

    /**
     * Stores one earlier version, then drops anything past [MAX_REVISIONS].
     *
     * Bounded because a node document's history is unbounded otherwise and nobody is ever going to
     * scroll to the twentieth entry — but generous enough that a run of small edits doesn't push
     * the version someone actually wants off the end.
     */
    private suspend fun saveRevision(
        uid: String,
        pathId: String,
        nodeId: String,
        revision: LessonRevision,
    ) {
        try {
            revisionsRef(uid, pathId, nodeId).document(revision.id).set(
                mapOf(
                    "savedAt" to revision.savedAt,
                    "label" to revision.label,
                    "byAssistant" to revision.byAssistant,
                    "summary" to revision.summary,
                    "whyItMatters" to revision.whyItMatters,
                    "body" to revision.body.map { it.toMap() },
                    "resources" to revision.resources.map(::resourceMap),
                ),
            ).await()

            revisionsRef(uid, pathId, nodeId)
                .orderBy("savedAt", Query.Direction.DESCENDING)
                .get().await()
                .documents.drop(MAX_REVISIONS)
                .forEach { it.reference.delete().await() }
        } catch (e: Exception) {
            // An edit whose history entry didn't save is still a saved edit; losing the undo is
            // worth telling the log about, not worth refusing the change over.
            Log.e("FirebasePathRepo", "saveRevision failed for $pathId/$nodeId", e)
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toLessonRevision(): LessonRevision? {
        val savedAt = getLong("savedAt") ?: return null
        return LessonRevision(
            id = id,
            savedAt = savedAt,
            label = getString("label").orEmpty().ifEmpty { "Edited" },
            byAssistant = getBoolean("byAssistant") ?: false,
            summary = getString("summary").orEmpty(),
            whyItMatters = getString("whyItMatters").orEmpty(),
            body = lessonBlocks((get("body") as? List<*>).orEmpty()),
            resources = (get("resources") as? List<*>).orEmpty().toResourceLinks(),
        )
    }

    override suspend fun editRoadmap(pathId: String, edits: List<RoadmapEdit>): LearningPath? =
        withContext(Dispatchers.IO) {
            val before = learningPath(pathId) ?: return@withContext null
            val after = before.applyEdits(edits) ?: return@withContext null
            val uid = uid ?: return@withContext after

            val wasById = before.nodes.associateBy { it.id }
            val wasOrder = before.nodes.withIndex().associate { (index, node) -> node.id to index }
            try {
                after.nodes.forEachIndexed { index, node ->
                    // Untouched nodes that also haven't shifted are left alone entirely.
                    if (wasById[node.id] == node && wasOrder[node.id] == index) return@forEachIndexed
                    nodesRef(uid, pathId).document(node.id)
                        .set(
                            // A structural edit never touches a lesson — not even the moved node's.
                            nodeDoc(node, index, keepStoredContent = true),
                            SetOptions.merge(),
                        )
                        .await()
                }
                (wasById.keys - after.nodes.mapTo(mutableSetOf()) { it.id }).forEach { goneId ->
                    nodesRef(uid, pathId).document(goneId).delete().await()
                }
                topicsRef(uid).document(pathId)
                    .set(mapOf("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.e("FirebasePathRepo", "editRoadmap failed for $pathId", e)
            }
            // `after` IS the roadmap now, so it becomes what the next read returns.
            activePathCache = after
            // A structural edit can delete a section the user had finished, so the account total
            // has to be counted again rather than nudged.
            if (after.nodes.size != before.nodes.size) masteredTotal = null
            after
        }

    /**
     * Cuts this roadmap's over-long section titles down to board size, once.
     *
     * Roadmaps generated before titles were constrained are full of names too long to read under a
     * node, and re-generating the roadmap to fix that would throw away the user's progress and
     * their lessons. So only the titles are rewritten, in place: one AI call, a title-only write
     * per changed node, and nothing else about the roadmap touched.
     *
     * Self-limiting — afterwards every title is short, so the guard at the top finds nothing to do
     * and no further call is ever made for this path.
     */
    override suspend fun shortenNodeTitles(pathId: String): LearningPath? =
        withContext(Dispatchers.IO) {
            val path = learningPath(pathId) ?: return@withContext null
            if (path.nodes.none { isTitleTooLong(it.title) }) return@withContext null

            val shortened = withShortTitles(path.title, path.nodes)
            val changed = shortened.filterIndexed { index, node -> node.title != path.nodes[index].title }
            if (changed.isEmpty()) return@withContext null

            val uid = uid ?: return@withContext path.copy(nodes = shortened)
            try {
                changed.forEach { node ->
                    nodesRef(uid, pathId).document(node.id).set(
                        mapOf("title" to node.title, "updatedAt" to FieldValue.serverTimestamp()),
                        SetOptions.merge(),
                    ).await()
                }
            } catch (e: Exception) {
                Log.e("FirebasePathRepo", "shortenNodeTitles failed for $pathId", e)
                // The board can still show them short for this session even if the write missed.
            }
            path.copy(nodes = shortened).also { activePathCache = it }
        }

    override suspend fun updateNodeCompletion(pathId: String, nodeId: String, completed: Boolean) {
        val uid = uid ?: return
        Log.d("FirebasePathRepo", "Cloud sync: Node $nodeId -> completed=$completed")

        // BEFORE the write, and regardless of whether it lands. [activePathCache] is what the next
        // read of this roadmap returns, so leaving it holding the pre-completion copy is what made
        // finishing a lesson and coming back show the lesson unfinished again — the write was fine,
        // the thing we handed back afterwards was a snapshot taken before it.
        activePathCache?.takeIf { it.id == pathId }?.let { cached ->
            // Only move the account total if this is a real change of state — the presenter guards
            // against re-completing a node, but nothing here should depend on that.
            if (cached.nodes.firstOrNull { it.id == nodeId }?.completed != completed) {
                nudgeMasteredTotal(completed)
            }
            activePathCache = cached.copy(
                nodes = cached.nodes.map { if (it.id == nodeId) it.copy(completed = completed) else it },
            )
        } ?: nudgeMasteredTotal(completed)

        try {
            // Use .set() with SetOptions.merge() instead of .update().
            // .update() fails if the document doesn't exist yet.
            // .set() with merge will create it or update it if it exists.
            nodesRef(uid, pathId).document(nodeId)
                .set(
                    mapOf(
                        "completed" to completed,
                        "state" to if (completed) "completed" else "active",
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()
            Log.d("FirebasePathRepo", "Cloud sync: Successfully updated node $nodeId")
        } catch (e: Exception) {
            Log.e("FirebasePathRepo", "Cloud sync: Failed to update node $nodeId", e)
        }
    }


    override suspend fun updateTinkerStepCompletion(guideId: String, stepId: String, completed: Boolean) {
        val uid = uid ?: return
        nudgeMasteredTotal(completed) // A finished step is XP too — see [totalLessonsMastered].
        try {
            val docRef = topicsRef(uid).document(guideId)

            // Try to get from DEFAULT first (online), then CACHE if offline
            val source = if (networkMonitor.isOnline()) Source.DEFAULT else Source.CACHE
            val doc = try {
                docRef.get(source).await()
            } catch (e: Exception) {
                docRef.get(Source.CACHE).await()
            }

            val steps = (doc.get("steps") as? List<*>).orEmpty()
                .filterIsInstance<Map<String, Any?>>()
                .map { raw ->
                    if (raw["id"] == stepId) raw + ("done" to completed) else raw
                }

            docRef.set(
                mapOf(
                    "steps" to steps,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()

            Log.d("FirebasePathRepo", "Tinker step $stepId updated (completed=$completed)")
        } catch (e: Exception) {
            Log.e("FirebasePathRepo", "updateTinkerStepCompletion failed for $guideId/$stepId", e)
        }
    }

    override suspend fun deleteTopic(pathId: String) {
        val uid = uid ?: return
        invalidatePathCache(pathId) // Nothing may hand this roadmap back once it is gone.
        // Its finished lessons went with it, and how many that was is no longer knowable here.
        masteredTotal = null
        try {
            val nodeCollection = nodesRef(uid, pathId)
            val nodesSnapshot = nodeCollection.get().await()
            for (document in nodesSnapshot.documents) {
                document.reference.delete().await()
            }
            topicsRef(uid).document(pathId).delete().await()
            Log.d("FirebasePathRepo", "Deleted topic: $pathId")
        } catch (e: Exception) {
            Log.e("FirebasePathRepo", "deleteTopic failed for $pathId", e)
        }
    }

    override suspend fun downloadTopic(pathId: String): Boolean = withContext(Dispatchers.IO) {
        val uid = uid ?: return@withContext false

        // Check mobile data constraint
        if (!isMobileDataAllowed() && !networkMonitor.isOnWifi()) {
            Log.d("FirebasePathRepo", "Sync skipped: Mobile data not allowed and currently on cellular.")
            return@withContext false
        }

        try {
            // Mark as downloading
            topicsRef(uid).document(pathId).set(
                mapOf("downloadState" to DownloadState.DOWNLOADING.name, "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            ).await()

            // For roadmaps: fetch all subtopic content to trigger generation/caching.
            val path = learningPath(pathId)
            if (path != null) {
                val lessons = path.nodes.filter { !it.isBranchOut }
                lessons.forEach { node ->
                    val content = subtopic(pathId, node.id)
                    // Image pre-fetching: ensure images are cached on disk during download
                    content?.body?.forEach { block ->
                        if (block is LessonBlock.Image) {
                            Log.d("FirebasePathRepo", "Pre-fetching image: ${block.url}")
                            com.example.grasp.ui.components.preloadImage(GraspApp.context, block.url)
                        }
                    }
                }
            } else {
                // For tinker guides: ensure we can load it (steps are already inline).
                tinkerGuide(pathId) ?: return@withContext false
            }

            // Mark as available in metadata
            topicsRef(uid).document(pathId).set(
                mapOf("downloadState" to DownloadState.AVAILABLE.name, "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            ).await()
            // The cached copy still says whatever it said before the download.
            invalidatePathCache(pathId)
            true
        } catch (e: Exception) {
            Log.e("FirebasePathRepo", "downloadTopic failed for $pathId", e)
            topicsRef(uid).document(pathId).set(
                mapOf("downloadState" to DownloadState.FAILED.name, "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            ).await()
            invalidatePathCache(pathId)
            false
        }
    }

    override suspend fun cancelDownload(pathId: String) {
        val uid = uid ?: return
        topicsRef(uid).document(pathId).set(
            mapOf("downloadState" to DownloadState.NONE.name),
            SetOptions.merge()
        ).await()
        invalidatePathCache(pathId)
    }

    override suspend fun removeDownload(pathId: String) {
        val uid = uid ?: return
        topicsRef(uid).document(pathId).set(
            mapOf("downloadState" to DownloadState.NONE.name, "isDownloaded" to false), // clear both for safety
            SetOptions.merge()
        ).await()
        // Note: Firestore doesn't provide a way to clear specific documents from the local cache
        // through the standard SDK easily. We just mark it as not downloaded in the metadata.
        invalidatePathCache(pathId)
    }

    override suspend fun getStorageUsage(): Long = withContext(Dispatchers.IO) {
        // Dummy implementation since actual cache size is hard to get per-path.
        // Assuming ~100KB per downloaded roadmap on average.
        val uid = uid ?: return@withContext 0L
        val snap = topicsRef(uid).whereEqualTo("downloadState", DownloadState.AVAILABLE.name).get(Source.CACHE).await()
        snap.size() * 1024L * 100L
    }

    override suspend fun clearAllDownloads() {
        val uid = uid ?: return
        val snap = topicsRef(uid).get(Source.CACHE).await()
        snap.documents.forEach { doc ->
            val state = doc.getString("downloadState")
            if (state == DownloadState.AVAILABLE.name || state == DownloadState.FAILED.name) {
                doc.reference.update("downloadState", DownloadState.NONE.name).await()
            }
        }
    }

    override suspend fun setMobileDataAllowed(enabled: Boolean) {
        val uid = uid ?: return
        userDocRef(uid).set(mapOf("useMobileData" to enabled), SetOptions.merge()).await()
    }

    /**
     * Bounded and cache-aware like every other read here — this one gates the download button, so
     * an unbounded wait on an unresponsive server reads as the button doing nothing at all.
     *
     * Total: both callers invoke it outside any try, from inside a `launch`, where a thrown
     * exception is an uncaught crash rather than a failed download.
     */
    override suspend fun isMobileDataAllowed(): Boolean = try {
        val uid = uid ?: return false
        readUserDoc(uid, networkMonitor.isOnline())?.getBoolean("useMobileData") ?: false
    } catch (e: Exception) {
        Log.e("FirebasePathRepo", "isMobileDataAllowed failed; assuming Wi-Fi only", e)
        false
    }

    /**
     * The Firestore shape of one node — one place, so every writer stays in sync.
     *
     * [content] is the node's generated lesson when it was written up front. [keepStoredContent]
     * leaves whatever lesson is already on the document untouched, which is how a re-created topic
     * avoids writing over one the user has edited — note that includes its `contentStatus`, since
     * marking an edited lesson "not generated" would invite the app to generate one over the top.
     */
    private fun nodeDoc(
        node: TreeNode,
        order: Int,
        parentId: String? = node.parentId,
        content: GeneratedContent? = null,
        keepStoredContent: Boolean = false,
    ): Map<String, Any?> = mapOf(
        "id" to node.id,
        "title" to node.title,
        "updatedAt" to FieldValue.serverTimestamp(),
        "order" to order,
        "completed" to node.completed,
        "estMinutes" to node.estMinutes,
        "parentId" to parentId,
        "children" to node.children,
        "contentRef" to node.contentRef,
        "state" to when {
            node.isBranchOut -> "branch-out"
            node.completed -> "completed"
            else -> "active"
        },
        "tier" to node.tier,
        "contentStatus" to if (node.contentReady) CONTENT_GENERATED else "not_generated"
    ) + when {
        keepStoredContent -> emptyMap()
        content != null -> contentFields(content)
        else -> mapOf("contentStatus" to "not_generated")
    }

    /** The generated-lesson half of a node document, shared by the up-front and lazy writers. */
    private fun contentFields(content: GeneratedContent): Map<String, Any?> = mapOf(
        "summary" to content.summary,
        "whyItMatters" to content.whyItMatters,
        "body" to content.body.map { it.toMap() },
        "resources" to content.resources.map(::resourceMap),
        "estMinutes" to content.estMinutes,
        "contentStatus" to CONTENT_GENERATED,
        "edited" to content.edited,
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toTreeNode(): TreeNode? {
        val nodeId = getString("id") ?: id
        val title = getString("title") ?: nodeId
        val children = (get("children") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        return TreeNode(
            id = nodeId,
            title = title,
            completed = getBoolean("completed") ?: false,
            estMinutes = getLong("estMinutes")?.toInt() ?: 0,
            children = children,
            parentId = getString("parentId"),
            contentRef = getString("contentRef"),
            contentReady = getString("contentStatus") == CONTENT_GENERATED,
            isBranchOut = (getString("state") ?: "").equals("branch-out", ignoreCase = true),
            // A stored "lane" on an older document is ignored: the board derives every position
            // from the shape of the tree now, so a saved coordinate would only fight it.
            tier = getString("tier"),
        )
    }

    /** The cached lesson on a node document, or null if it hasn't been generated yet. */
    private fun com.google.firebase.firestore.DocumentSnapshot.toGeneratedContent(): GeneratedContent? {
        val edited = getBoolean("edited") ?: false
        if (!edited && getString("contentStatus") != CONTENT_GENERATED) return null
        val summary = getString("summary").orEmpty()
        // Lessons saved before headings existed are plain strings; [lessonBlocks] reads both.
        val body = lessonBlocks((get("body") as? List<*>).orEmpty())
        // This bar judges GENERATION — a lesson that came back too thin to show is worth another
        // try. It must not be applied to a lesson the user has edited: they are allowed to cut it
        // down to a single heading if they like, and regenerating over that destroys their work
        // (FR4.5).
        if (!edited && (summary.isBlank() || body.paragraphs().isEmpty())) return null
        val resources = (get("resources") as? List<*>).orEmpty().toResourceLinks()
        return GeneratedContent(
            summary = summary,
            whyItMatters = getString("whyItMatters").orEmpty(),
            body = body,
            resources = resources,
            estMinutes = getLong("estMinutes")?.toInt() ?: 0,
            edited = edited,
        )
    }

    private companion object {
        /** `contentStatus` marking a node whose lesson has been written and cached. */
        const val CONTENT_GENERATED = "generated"

        /** User-document flag: this account has already been given its starter examples. */
        const val STARTERS_SEEDED = "startersSeeded"

        /**
         * The uid whose seeding has already been settled this run.
         *
         * Static because every screen builds its own repository instance, and without it the
         * "have we seeded?" read would happen once per presenter rather than once per session.
         */
        @Volatile
        var seedCheckedForUid: String? = null

        /**
         * Serialises seeding across every repository instance.
         *
         * [seedCheckedForUid] alone is only a fast path: Home and Library attach at nearly the
         * same moment, and both would read the flag before either had written it, then commit the
         * same twenty-odd documents twice. Static for the same reason the flag is — each screen
         * builds its own repository, so a per-instance lock would guard nothing.
         */
        val seedMutex = Mutex()

        /**
         * How long to wait for the server to acknowledge the seeding batch.
         *
         * Longer than the read timeouts elsewhere because this is a write of the whole starter
         * library, and bounded at all because a captive portal reports itself as online and then
         * never answers — with Library waiting on this before it lists anything.
         */
        const val SEED_COMMIT_TIMEOUT_MS = 15_000L

        /**
         * How long a server read of the user document may hold a screen up.
         *
         * Same bound as the roadmap reads, and for the same reason: past this, the cached copy is
         * a better answer than a longer wait.
         */
        const val USER_DOC_TIMEOUT_MS = 1500L

        /**
         * The most recently loaded roadmap, cached to avoid redundant database reads when
         * opening its individual lessons.
         */
        @Volatile
        var activePathCache: LearningPath? = null

        /**
         * The account's finished-lesson count once something has counted it.
         *
         * Null means "not counted yet", which is the only state that pays for a library read.
         * See [totalLessonsMastered].
         */
        @Volatile
        var masteredTotal: Int? = null

        /** How many lessons to write at once when generating a whole roadmap or branch. */
        const val MAX_PARALLEL_GENERATIONS = 4

        /** How far back a lesson's undo history goes. */
        const val MAX_REVISIONS = 20

        fun newRevisionId(): String = java.util.UUID.randomUUID().toString()
    }
}

/** The stored shape of a "dive deeper" link, shared by every writer of one. */
private fun resourceMap(link: ResourceLink): Map<String, Any> =
    mapOf("title" to link.title, "url" to link.url, "kind" to link.kind.name)

/** Reads back stored "dive deeper" links, dropping any that lost their title or url. */
private fun List<*>.toResourceLinks(): List<ResourceLink> = filterIsInstance<Map<*, *>>()
    .mapNotNull { resource ->
        val title = resource["title"] as? String ?: return@mapNotNull null
        val url = resource["url"] as? String ?: return@mapNotNull null
        val kind = ResourceKind.entries
            .firstOrNull { it.name.equals(resource["kind"] as? String, ignoreCase = true) }
            ?: ResourceKind.ARTICLE
        ResourceLink(title, url, kind)
    }

/**
 * The stored lesson fields that differ between [before] and [after] — nothing else.
 *
 * Pure and separate from the write so the "only what changed" part is testable without Firestore:
 * it is the whole point of the partial write, and a version of this that quietly returns
 * everything would look identical from the outside until two people edited the same lesson.
 */
internal fun changedLessonFields(before: Subtopic, after: Subtopic): Map<String, Any?> = buildMap {
    if (after.summary != before.summary) put("summary", after.summary)
    if (after.whyItMatters != before.whyItMatters) put("whyItMatters", after.whyItMatters)
    if (after.body != before.body) put("body", after.body.map { it.toMap() })
    if (after.resources != before.resources) put("resources", after.resources.map(::resourceMap))
}
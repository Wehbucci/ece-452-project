package com.example.grasp.data.model

/**
 * A lesson as it stood before one change, kept so the change can be taken back.
 *
 * Version history stops being a nicety the moment the assistant can rewrite content (FR5.4): a
 * user who accepts a proposal is trusting a suggestion they have only skimmed, and the honest
 * answer to "actually, put it back" cannot be that the original is gone. It covers their own
 * mistakes too — deleting the wrong paragraph is the easiest thing to do in an editor.
 *
 * Holds the WHOLE lesson rather than a diff. A lesson is a few kilobytes of text, and storing
 * states means restoring one can never half-apply or fail to reverse; a chain of diffs is only as
 * sound as its weakest link, and the whole point of this is to be the thing that doesn't fail.
 *
 * @property savedAt when the change this undoes was made, in epoch milliseconds.
 * @property label one line saying what the change was, e.g. "Rewrote 2 blocks".
 * @property byAssistant whether the assistant made it rather than the user — the version worth
 *           finding quickly is almost always the one just before the AI touched something.
 * @property summary the lesson's fields as they were.
 */
data class LessonRevision(
    val id: String,
    val savedAt: Long,
    val label: String,
    val byAssistant: Boolean,
    val summary: String,
    val whyItMatters: String,
    val body: List<LessonBlock>,
    val resources: List<ResourceLink>,
)

/** The revision that restoring would produce — this lesson's content, under [revision]'s. */
fun Subtopic.restoredTo(revision: LessonRevision): Subtopic = copy(
    summary = revision.summary,
    whyItMatters = revision.whyItMatters,
    body = revision.body,
    resources = revision.resources,
    // Restoring is not un-editing: the lesson is still not what the generator wrote.
    edited = true,
)

/** This lesson captured as a revision, for storing before a change lands on top of it. */
fun Subtopic.asRevision(id: String, savedAt: Long, label: String, byAssistant: Boolean) = LessonRevision(
    id = id,
    savedAt = savedAt,
    label = label,
    byAssistant = byAssistant,
    summary = summary,
    whyItMatters = whyItMatters,
    body = body,
    resources = resources,
)

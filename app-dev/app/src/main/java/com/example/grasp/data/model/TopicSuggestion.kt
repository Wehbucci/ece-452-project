package com.example.grasp.data.model

/**
 * A "popular topic" suggestion shown on the Home screen 
 * a tappable chip/card that pre-fills a topic, showing how many subtopics it expands into.
 *
 * @property id id of the path that would open (or be generated) when tapped
 * @property title the topic/task name
 * @property subtopicCount preview count shown on the card, e.g. "8 subtopics"
 * @property mode whether tapping it starts a Learner or Tinkerer flow
 */
data class TopicSuggestion(
    val id: String,
    val title: String,
    val subtopicCount: Int,
    val mode: Mode,
)

package com.example.grasp.data.model

/**
 * User learning preferences that influence AI generation and tutor personality.
 */
data class UserPreferences(
    val pace: Pace = Pace.STANDARD,
    val style: Style = Style.BALANCED,
    val tone: Tone = Tone.PROFESSIONAL
)

enum class Pace(val label: String, val prompt: String, val description: String) {
    OVERVIEW("Overview", "Create a high-level, fast-paced roadmap with fewer, broader nodes.", "A fast, birds-eye view. Only the absolute essentials to get you started."),
    STANDARD("Standard", "", "A balanced foundational journey. Covers the core concepts and common practices."),
    COMPREHENSIVE("Comprehensive", "Create a detailed, deep-dive roadmap that covers foundational concepts thoroughly.", "The deep dive. Exhaustive detail including history, advanced nuances, and expert tips.")
}

enum class Style(val label: String, val prompt: String, val description: String) {
    ACTIONABLE("Actionable", "Prioritize concrete tasks, hands-on examples, and practical application.", "Less talk, more action. Prioritizes code blocks, exercises, and immediate 'how-to' steps."),
    BALANCED("Balanced", "", "The best of both worlds. Blends clear definitions with practical examples."),
    THEORETICAL("Theoretical", "Prioritize first-principles, conceptual understanding, and history.", "Focus on the 'Why.' Deep exploration of first-principles and underlying logic.")
}

enum class Tone(val label: String, val prompt: String, val description: String) {
    MINIMALIST("Minimalist", "Be extremely concise, direct, and skip all conversational pleasantries.", "Direct and clinical. No fluff or small talk—just the facts and answers you need."),
    PROFESSIONAL("Professional", "", "Clear and objective. Polished academic tone suitable for any subject."),
    ENCOURAGING("Encouraging", "Use a friendly, motivating tone with supportive language.", "Your personal cheerleader. Uses motivational language and emojis to keep you focused.")
}

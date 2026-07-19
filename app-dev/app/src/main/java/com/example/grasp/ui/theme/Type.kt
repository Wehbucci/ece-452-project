package com.example.grasp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Font families for the gamified learning-path surface.
 *
 * The design calls for **Fredoka** (rounded, friendly — used for titles, big numerals and
 * CTAs) and **Nunito** (humanist body — used for labels and reading text). To avoid shipping
 * font assets right now we map BOTH to the system family (the "closest existing family"), which
 * keeps the geometry playful-neutral. When we bundle the real fonts, swap the two lines below
 * (e.g. `FontFamily(Font(R.font.fredoka_semibold, FontWeight.SemiBold))`) and the whole journey
 * screen re-skins with no other change — that is the entire point of centralizing them here.
 *
 * Composables reach for these via `fontFamily = FredokaFamily` on a `MaterialTheme.typography`
 * style, so we never hard-code a family inside a screen.
 */
val FredokaFamily: FontFamily = FontFamily.Default
val NunitoFamily: FontFamily = FontFamily.Default

/**
 * Grasp typographic scale.
 *
 * Goals: a clear, calm hierarchy that keeps screens uncluttered and
 * keeps long-form generated content comfortable to read. We use the system font family
 * for now (fast, no asset cost); swap [FontFamily.Default] here for a bundled font later
 * and the whole app updates.
 *
 * Quick guide for contributors (which style to reach for):
 *  - displayLarge/Medium .... big hero numbers / splash, used rarely
 *  - headlineMedium ......... screen titles ("What do you want to learn?")
 *  - titleLarge/Medium ...... card titles, section headers, subtopic names
 *  - bodyLarge .............. primary reading text for generated content
 *  - bodyMedium ............. secondary text, descriptions
 *  - labelLarge/Medium ...... buttons, chips, tab labels, metadata (e.g. "5 min")
 */
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

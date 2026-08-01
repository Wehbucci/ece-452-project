package com.example.grasp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.ui.theme.GraspTheme
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathChipNeutralBg
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathMuted

/**
 * The standing notice that everything in Grasp — lessons, roadmaps, tutor answers — is written by
 * an AI and can be wrong.
 *
 * Grasp generates all of its teaching material, so the user has no author to judge it by and no
 * edition to check it against. Saying so once in an About page would not reach the person reading
 * a confidently-worded paragraph, so this sits wherever generated content actually is: under the
 * tutor's header, at the end of a lesson, under a guide's steps.
 *
 * It is deliberately the quietest thing on any screen it appears on. A warning loud enough to
 * compete with the lesson would undermine the material the app just spent an AI call writing; the
 * job here is to be present and readable when someone stops to check, not to be argued with.
 *
 * @param text what to say. The default covers generated lessons and tutor answers alike; the
 *        callers that mean something narrower ("this reply", "these steps") pass their own.
 * @param compact drops the tinted background for places already inside a card or panel, where a
 *        second surface would read as another block of content.
 */
@Composable
fun AiDisclaimer(
    modifier: Modifier = Modifier,
    text: String = DEFAULT_AI_DISCLAIMER,
    compact: Boolean = false,
) {
    if (compact) {
        DisclaimerLine(text = text, modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp))
        return
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = PathChipNeutralBg,
        modifier = modifier.fillMaxWidth(),
    ) {
        DisclaimerLine(text = text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
    }
}

/** The notice itself: one warning glyph and one line of small print. */
@Composable
private fun DisclaimerLine(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "⚠",
            fontSize = 12.sp,
            color = PathFaint,
        )
        Text(
            text = text,
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = PathMuted,
        )
    }
}

/**
 * What Grasp says about its own content everywhere it isn't being more specific.
 *
 * Phrased as something to do rather than only something to fear: "can be inaccurate" on its own
 * leaves the reader with a problem and no move, and the one move that helps is checking anything
 * that matters against a real source.
 */
const val DEFAULT_AI_DISCLAIMER: String =
    "AI-generated content can be inaccurate. Double-check anything you rely on against a " +
        "trusted source."

/** The tutor's variant — the same point, about a conversation rather than a written lesson. */
const val CHAT_AI_DISCLAIMER: String =
    "This tutor is AI and can be inaccurate. Double-check anything important."

/**
 * The Tinkerer variant.
 *
 * A guide is instructions the user is about to CARRY OUT on something real, so this one names the
 * cases where being wrong costs more than a misremembered fact.
 */
const val GUIDE_AI_DISCLAIMER: String =
    "These steps are AI-generated and can be inaccurate. Check them against the manufacturer's " +
        "instructions before doing anything involving tools, electricity, food safety or money."

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun AiDisclaimerPreview() {
    GraspTheme {
        AiDisclaimer(modifier = Modifier.padding(16.dp))
    }
}

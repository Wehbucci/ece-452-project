package com.example.grasp.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text = remember(text) { text.parseMarkdown() },
        style = style,
        modifier = modifier,
    )
}

fun String.parseMarkdown(): AnnotatedString = buildAnnotatedString {
    val lines = this@parseMarkdown.split("\n")
    lines.forEachIndexed { lineIndex, line ->
        when {
            line.startsWith("# ") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 1.3.em))
                appendInline(line.removePrefix("# "))
                pop()
            }
            line.startsWith("## ") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 1.15.em))
                appendInline(line.removePrefix("## "))
                pop()
            }
            line.startsWith("### ") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                appendInline(line.removePrefix("### "))
                pop()
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                append("• ")
                appendInline(line.drop(2))
            }
            else -> appendInline(line)
        }
        if (lineIndex < lines.lastIndex) append("\n")
    }
}

private fun AnnotatedString.Builder.appendInline(text: String) {
    var cursor = 0
    while (cursor < text.length) {
        when {
            text.startsWith("**", cursor) -> {
                val close = text.indexOf("**", cursor + 2)
                if (close != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(cursor + 2, close))
                    pop()
                    cursor = close + 2
                } else {
                    append(text[cursor++])
                }
            }
            text.startsWith("*", cursor) || text.startsWith("_", cursor) -> {
                val marker = text[cursor]
                val close = text.indexOf(marker, cursor + 1)
                if (close != -1 && close > cursor + 1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(cursor + 1, close))
                    pop()
                    cursor = close + 1
                } else {
                    append(text[cursor++])
                }
            }
            text.startsWith("`", cursor) -> {
                val close = text.indexOf("`", cursor + 1)
                if (close != -1) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                    append(text.substring(cursor + 1, close))
                    pop()
                    cursor = close + 1
                } else {
                    append(text[cursor++])
                }
            }
            else -> append(text[cursor++])
        }
    }
}

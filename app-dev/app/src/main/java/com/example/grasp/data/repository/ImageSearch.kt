package com.example.grasp.data.repository

import android.util.Log
import com.example.grasp.data.model.LessonBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Finds real illustrations for a lesson on Wikimedia Commons.
 *
 * The AI decides WHAT to look for and writes the caption; the lookup itself happens here against a
 * real catalogue. That split is deliberate: asking a language model for image URLs directly gets
 * you plausible-looking links that 404, and asking an image model to draw one gets you a picture
 * whose labels are confidently wrong. Commons gives real, freely-licensed images with the
 * attribution the licences require.
 *
 * Runs once, when the lesson is generated — the resolved URL is stored with the lesson, so opening
 * a node never waits on a search.
 */
private const val TAG = "ImageSearch"

/** Width requested from Commons. Comfortably over the widest phone at 3x, and not more. */
private const val THUMB_WIDTH = 1000

private const val TIMEOUT_MS = 8_000

/**
 * The best Commons match for [query], or null if nothing usable came back.
 *
 * @param caption the AI-written caption to carry into the block; the search result's own filename
 *        is not shown to the user.
 */
suspend fun findLessonImage(query: String, caption: String): LessonBlock.Image? =
    withContext(Dispatchers.IO) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return@withContext null
        try {
            val json = JSONObject(fetch(commonsUrl(cleaned)))
            val pages = json.optJSONObject("query")?.optJSONObject("pages")
                ?: return@withContext null

            // `pages` is keyed by page id; search order comes from each page's "index".
            val best = pages.keys().asSequence()
                .mapNotNull { pages.optJSONObject(it) }
                .sortedBy { it.optInt("index", Int.MAX_VALUE) }
                .firstNotNullOfOrNull { page ->
                    val info = page.optJSONArray("imageinfo")?.optJSONObject(0)
                    val url = info?.optString("thumburl").orEmpty().ifEmpty {
                        info?.optString("url").orEmpty()
                    }
                    if (url.startsWith("http")) page to url else null
                } ?: return@withContext null

            val (page, url) = best
            val info = page.optJSONArray("imageinfo")?.optJSONObject(0)
            LessonBlock.Image(
                text = caption.trim().ifEmpty { page.optString("title").removePrefix("File:") },
                url = url,
                sourceUrl = info?.optString("descriptionurl").orEmpty(),
                credit = creditFor(info),
            )
        } catch (e: Exception) {
            // A lesson without its picture is fine; a lesson that failed to generate is not.
            Log.e(TAG, "image search failed for \"$cleaned\"", e)
            null
        }
    }

/**
 * Search Commons' File namespace (6) and return each hit's thumbnail plus its licence metadata in
 * one round trip.
 */
private fun commonsUrl(query: String): String {
    val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
    return "https://commons.wikimedia.org/w/api.php" +
        "?action=query&format=json&generator=search" +
        "&gsrsearch=$encoded&gsrnamespace=6&gsrlimit=3" +
        "&prop=imageinfo&iiprop=url|extmetadata&iiurlwidth=$THUMB_WIDTH"
}

/** "Author — CC BY-SA 4.0", falling back to just whichever half is present. */
private fun creditFor(info: JSONObject?): String {
    val meta = info?.optJSONObject("extmetadata")
    val artist = meta?.optJSONObject("Artist")?.optString("value").orEmpty().stripHtml()
    val licence = meta?.optJSONObject("LicenseShortName")?.optString("value").orEmpty().stripHtml()
    return listOf(artist, licence).filter { it.isNotBlank() }.joinToString(" · ")
        .ifBlank { "Wikimedia Commons" }
}

/** Commons returns these fields as HTML fragments (usually a link around the author's name). */
private fun String.stripHtml(): String =
    replace(Regex("<[^>]*>"), "").replace("&amp;", "&").trim().take(80)

private fun fetch(url: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        // Commons' API policy asks for a descriptive agent so abuse can be traced to an app.
        setRequestProperty("User-Agent", "Grasp/1.0 (learning app; ECE 452 course project)")
    }
    return try {
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

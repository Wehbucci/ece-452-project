package com.example.grasp.ui.components

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.data.model.LessonBlock
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathCard
import com.example.grasp.ui.theme.PathChipNeutralBg
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * A sourced illustration inside a lesson, with the attribution its licence requires.
 *
 * Tapping the credit opens the image's page on Wikimedia Commons, which is where the licence and
 * full author details live — the one-line credit under the picture is a summary, not the licence.
 */
@Composable
fun LessonImage(
    image: LessonBlock.Image,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberRemoteImage(image.url)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PathCard,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PathChipNeutralBg),
                contentAlignment = Alignment.Center,
            ) {
                when (val loaded = bitmap) {
                    null -> Text(
                        // Also what an offline reader sees, hence "not loaded" and not "loading".
                        text = "Image unavailable",
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = PathFaint,
                        modifier = Modifier.padding(24.dp),
                    )

                    else -> Image(
                        bitmap = loaded,
                        contentDescription = image.text,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (image.text.isNotBlank()) VisualCaption(image.text, Modifier.fillMaxWidth())

            if (image.credit.isNotBlank()) {
                Text(
                    text = image.credit,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = PathMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = image.sourceUrl.isNotBlank()) {
                            onOpenSource(image.sourceUrl)
                        },
                )
            }
        }
    }
}

/**
 * Loads [url] off the main thread, caching it on disk so a lesson re-read — including offline —
 * doesn't need the network (NFR 3.2). Null until it's ready, and null forever if it can't be got.
 *
 * Deliberately small rather than an image-loading library: a lesson shows at most a couple of
 * pictures, so request dedup, transformations and memory pressure handling would all be machinery
 * for a problem this screen doesn't have.
 */
@Composable
internal fun rememberRemoteImage(url: String): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.IO) { loadImage(File(context.cacheDir, IMAGE_DIR), url) }
    }
    return bitmap
}

fun preloadImage(context: android.content.Context, url: String) {
    loadImage(File(context.cacheDir, IMAGE_DIR), url)
}

private const val IMAGE_DIR = "lesson-images"
private const val TIMEOUT_MS = 10_000
private const val TAG = "LessonImage"

private fun loadImage(cacheDir: File, url: String): ImageBitmap? {
    if (url.isBlank()) return null
    val cached = File(cacheDir, url.hashCode().toString())
    if (cached.exists()) {
        BitmapFactory.decodeFile(cached.path)?.let { return it.asImageBitmap() }
    }
    return try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "Grasp/1.0 (learning app; ECE 452 course project)")
        }
        val bytes = try {
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
        // Write the cache before decoding, so a decode failure doesn't cost a second download.
        cacheDir.mkdirs()
        cached.writeBytes(bytes)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (e: Exception) {
        Log.e(TAG, "couldn't load $url", e)
        null
    }
}

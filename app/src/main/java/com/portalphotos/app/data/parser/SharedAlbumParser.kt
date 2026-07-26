package com.portalphotos.app.data.parser

import com.portalphotos.app.data.model.MediaItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ParsedAlbumResult(
    val id: String,
    val title: String,
    val coverImageUrl: String?,
    val mediaItems: List<MediaItemEntity>
)

data class VideoStreamInfo(
    val isVideo: Boolean,
    val isLivePhoto: Boolean,
    val streamUrl: String?
)

class SharedAlbumParser(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    suspend fun parseAlbumUrl(rawUrl: String): ParsedAlbumResult = withContext(Dispatchers.IO) {
        val cleanUrl = rawUrl.trim()
        require(cleanUrl.isNotEmpty()) { "Album URL cannot be empty" }

        val (initialHtml, finalUrl) = fetchPageHtml(cleanUrl)

        val targetHtml: String
        if (!initialHtml.contains("og:title") && (cleanUrl.contains("photos.app.goo.gl") || !finalUrl.contains("photos.google.com/share/"))) {
            val shareUrlRegex = Regex("""https://photos\.google\.com/share/[a-zA-Z0-9\-_?&=]+""")
            val shareMatch = shareUrlRegex.find(initialHtml)
            if (shareMatch != null) {
                val realShareUrl = shareMatch.value.replace("&amp;", "&")
                val (resolvedHtml, _) = fetchPageHtml(realShareUrl)
                targetHtml = resolvedHtml
            } else {
                targetHtml = initialHtml
            }
        } else {
            targetHtml = initialHtml
        }

        val targetDoc = Jsoup.parse(targetHtml)

        val rawTitle = targetDoc.select("meta[property=og:title]").attr("content").ifEmpty {
            targetDoc.select("div.PpgQdd").text().ifEmpty {
                targetDoc.select("meta[name=title]").attr("content").ifEmpty {
                    targetDoc.title()
                }
            }
        }
        val title = cleanAlbumTitle(rawTitle)
        val coverImage = targetDoc.select("meta[property=og:image]").attr("content").ifEmpty { null }
        val albumId = md5(cleanUrl)

        // Parse media items from target HTML
        val mediaItems = parseMediaItemsFromHtml(targetHtml, albumId, title).toMutableList()

        // Extract continuation token if album contains additional paginated pages
        val continuationToken = extractContinuationToken(targetHtml)
        if (!continuationToken.isNullOrEmpty()) {
            val paginatedItems = fetchPaginatedMediaItems(albumId, title, continuationToken)
            mediaItems.addAll(paginatedItems)
        }

        ParsedAlbumResult(
            id = albumId,
            title = title,
            coverImageUrl = coverImage,
            mediaItems = mediaItems.distinctBy { it.id }
        )
    }

    private fun fetchPageHtml(url: String): Pair<String, String> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 10)")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to load album page. HTTP code: ${response.code}")
            }
            val body = response.body?.string() ?: ""
            val finalUrl = response.request.url.toString()
            return Pair(body, finalUrl)
        }
    }

    private suspend fun parseMediaItemsFromHtml(html: String, albumId: String, albumTitle: String): List<MediaItemEntity> = withContext(Dispatchers.IO) {
        val urlSet = LinkedHashSet<String>()

        val photoUrlPattern = Pattern.compile("(https://lh[0-9]\\.googleusercontent\\.com/(?:pw/|a/|[a-zA-Z0-9\\-_]+)[a-zA-Z0-9\\-_]+)")
        val matcher = photoUrlPattern.matcher(html)

        while (matcher.find()) {
            val rawMatchedUrl = matcher.group(1)
            if (rawMatchedUrl != null && rawMatchedUrl.length > 35) {
                if (!html.contains("$rawMatchedUrl=s40") && !rawMatchedUrl.contains("/a/ACg8")) {
                    val baseUrl = rawMatchedUrl.substringBefore("=").substringBefore("?")
                    if (baseUrl.length > 35) {
                        urlSet.add(baseUrl)
                    }
                }
            }
        }

        val urlList = urlSet.toList()
        val videoCheckDeferreds = urlList.map { baseUrl ->
            async(Dispatchers.IO) {
                checkHighQualityVideoInfo(baseUrl)
            }
        }
        val videoInfos = videoCheckDeferreds.awaitAll()

        val items = mutableListOf<MediaItemEntity>()
        val now = System.currentTimeMillis()

        for (index in urlList.indices) {
            val baseUrl = urlList[index]
            val info = videoInfos[index]

            val highResPhotoUrl = "$baseUrl=w1920-h1080"
            val itemId = md5("$albumId-$baseUrl")

            items.add(
                MediaItemEntity(
                    id = itemId,
                    albumId = albumId,
                    mediaUrl = highResPhotoUrl,
                    videoStreamUrl = info.streamUrl,
                    isVideo = info.isVideo,
                    isLivePhoto = info.isLivePhoto,
                    durationMs = if (info.isVideo) 30000L else 0L,
                    width = 1920,
                    height = 1080,
                    albumTitle = albumTitle,
                    timestamp = now - (index * 1000L)
                )
            )
        }

        items
    }

    private fun checkHighQualityVideoInfo(baseUrl: String): VideoStreamInfo {
        // First check =dv format (Download Video endpoint)
        try {
            val dvRequest = Request.Builder()
                .url("$baseUrl=dv")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .head()
                .build()

            client.newCall(dvRequest).execute().use { response ->
                val contentType = response.header("Content-Type") ?: ""
                val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L

                val isVid = response.isSuccessful || response.code == 200 || contentType.contains("video") || contentType.contains("mp4")

                if (isVid) {
                    if (contentLength >= 5_000_000L) {
                        // Real standalone video (>= 5 MB): Use =dv for original HD source MP4!
                        return VideoStreamInfo(isVideo = true, isLivePhoto = false, streamUrl = "$baseUrl=dv")
                    } else {
                        // Live Photo / Motion Photo (< 5 MB): Use =m22 for Google's smooth 3.0s 60fps HD motion stream!
                        return VideoStreamInfo(isVideo = true, isLivePhoto = true, streamUrl = "$baseUrl=m22")
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to checking stream parameters below
        }

        // Fallback quality checks: =m37 (1080p) -> =m22 (720p) -> =m18 (360p)
        val formatList = listOf("m37", "m22", "m18")

        for (fmt in formatList) {
            try {
                val headRequest = Request.Builder()
                    .url("$baseUrl=$fmt")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .head()
                    .build()

                client.newCall(headRequest).execute().use { response ->
                    val contentType = response.header("Content-Type") ?: ""
                    val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L

                    val isVid = response.isSuccessful || response.code == 200 || contentType.contains("video") || contentType.contains("mp4")

                    if (isVid) {
                        val isLive = (contentLength in 1L..1_500_000L)
                        val streamUrl = "$baseUrl=$fmt"
                        return VideoStreamInfo(isVideo = true, isLivePhoto = isLive, streamUrl = streamUrl)
                    }
                }
            } catch (e: Exception) {
                // Continue checking
            }
        }

        return VideoStreamInfo(isVideo = false, isLivePhoto = false, streamUrl = null)
    }

    private fun extractContinuationToken(html: String): String? {
        val regex = Regex(""""([a-zA-Z0-9_-]{50,})"""")
        val match = regex.find(html)
        return match?.groupValues?.get(1)
    }

    private suspend fun fetchPaginatedMediaItems(albumId: String, albumTitle: String, token: String): List<MediaItemEntity> {
        try {
            val rpcUrl = "https://photos.google.com/_/PhotosUi/data/batchexecute"
            val requestBody = FormBody.Builder()
                .add("f.req", """[[["sn12d","[null,null,\"$token\"]",null,"1"]]]""")
                .build()

            val request = Request.Builder()
                .url(rpcUrl)
                .post(requestBody)
                .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 10)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: return emptyList()
                    return parseMediaItemsFromHtml(bodyStr, albumId, albumTitle)
                }
            }
        } catch (e: Exception) {
            // Ignore pagination errors and return initial page batch
        }
        return emptyList()
    }

    private fun cleanAlbumTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\s*-\\s*Google Photos?.*"), "")
            .replace(Regex("(?i)\\s*-\\s*Google.*"), "")
            .replace(Regex("""\s*·\s*.*"""), "")
            .replace(Regex("""[\uD83C-\uDBFF\uDC00-\uDFFF]+"""), "")
            .trim()
            .ifEmpty { "Shared Album" }
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

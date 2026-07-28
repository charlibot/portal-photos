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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ParsedAlbumResult(
    val id: String,
    val title: String,
    val coverImageUrl: String?,
    val mediaItems: List<MediaItemEntity>,
    val albumDate: String? = null
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

        val pageTitle = targetDoc.title()
        val rawTitle = if (pageTitle.isNotBlank() && pageTitle.contains("- Google Photos")) {
            pageTitle.substringBefore("- Google Photos").trim()
        } else {
            val ogTitle = targetDoc.select("meta[property=og:title]").attr("content")
            if (ogTitle.isNotBlank()) {
                ogTitle.substringBefore("·").trim()
            } else {
                targetDoc.select("div.PpgQdd").text().ifEmpty { "Shared Album" }
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

        // Extract human-readable album date from og:title or photo timestamps
        val rawOgTitle = targetDoc.select("meta[property=og:title]").attr("content")
        val dateFromOgTitle = if (rawOgTitle.contains("·")) {
            rawOgTitle.substringAfter("·")
                .replace(Regex("""[\uD800-\uDBFF][\uDC00-\uDFFF]|[\u2600-\u27BF]"""), "")
                .trim()
        } else null

        val validTsList = mediaItems.map { it.timestamp }.filter { it in 1000000000000L..2000000000000L }
        val minTs = validTsList.minOrNull()
        val dateFromTs = if (minTs != null) {
            SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(Date(minTs))
        } else null

        val albumDate = dateFromOgTitle ?: dateFromTs

        ParsedAlbumResult(
            id = albumId,
            title = title,
            coverImageUrl = coverImage,
            mediaItems = mediaItems.distinctBy { it.id },
            albumDate = albumDate
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

        for (index in urlList.indices) {
            val baseUrl = urlList[index]
            val info = videoInfos[index]

            val highResPhotoUrl = "$baseUrl=w1920-h1080"
            val itemId = md5("$albumId-$baseUrl")
            val itemTimestamp = extractTimestampForUrl(html, baseUrl) ?: (System.currentTimeMillis() - (index * 1000L))

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
                    timestamp = itemTimestamp
                )
            )
        }

        items
    }

    private fun extractTimestampForUrl(html: String, baseUrl: String): Long? {
        val pattern = Pattern.compile(Pattern.quote(baseUrl))
        val matcher = pattern.matcher(html)
        val candidateMs = mutableListOf<Long>()

        while (matcher.find()) {
            val idx = matcher.start()
            // Focus on occurrences inside the main JSON data block (idx > 50000)
            if (idx > 50000) {
                val start = (idx - 200).coerceAtLeast(0)
                val end = (idx + 1500).coerceAtMost(html.length)
                val chunk = html.substring(start, end)

                // Try 13-digit epoch ms (e.g. 1784707094790)
                val msMatcher = Pattern.compile("""\b(1[4-8]\d{11})\b""").matcher(chunk)
                while (msMatcher.find()) {
                    val msStr = msMatcher.group(1)
                    val ms = msStr?.toLongOrNull()
                    // Filter out 1785... (album sync/edit dates) to target real photo EXIF capture timestamps
                    if (ms != null && ms in 1000000000000L..2000000000000L && !msStr.startsWith("1785")) {
                        candidateMs.add(ms)
                    }
                }
            }
        }

        return candidateMs.firstOrNull()
    }

    private fun checkHighQualityVideoInfo(baseUrl: String): VideoStreamInfo {
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
                        return VideoStreamInfo(isVideo = true, isLivePhoto = false, streamUrl = "$baseUrl=dv")
                    } else if (contentLength in 1L..4_999_999L) {
                        return VideoStreamInfo(isVideo = true, isLivePhoto = true, streamUrl = "$baseUrl=dv")
                    } else {
                        return VideoStreamInfo(isVideo = true, isLivePhoto = false, streamUrl = "$baseUrl=dv")
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore error
        }

        return VideoStreamInfo(isVideo = false, isLivePhoto = false, streamUrl = null)
    }

    private fun extractContinuationToken(html: String): String? {
        val pattern = Pattern.compile("\"([^\"]+)\",\\s*\"AF_[^\"]+\"")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val token = matcher.group(1)
            if (token != null && token.length > 20 && !token.startsWith("http")) {
                return token
            }
        }
        return null
    }

    private suspend fun fetchPaginatedMediaItems(
        albumId: String,
        albumTitle: String,
        continuationToken: String
    ): List<MediaItemEntity> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItemEntity>()

        try {
            val requestBody = FormBody.Builder()
                .add("f.req", "[[[\"sn:x\", \"[\\\"$continuationToken\\\"]\"]]]")
                .build()

            val request = Request.Builder()
                .url("https://photos.google.com/_/PhotosUi/data/batchexecute")
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 10)")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseHtml = response.body?.string() ?: ""
                    items.addAll(parseMediaItemsFromHtml(responseHtml, albumId, albumTitle))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        items
    }

    private fun cleanAlbumTitle(rawTitle: String): String {
        return rawTitle
            .replace(" - Google Photos", "")
            .replace(" - Google Images", "")
            .trim()
            .ifEmpty { "Shared Album" }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

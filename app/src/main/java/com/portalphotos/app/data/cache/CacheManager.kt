package com.portalphotos.app.data.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import coil.Coil
import coil.request.ImageRequest
import com.portalphotos.app.data.crop.SmartCropDetector
import com.portalphotos.app.data.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@OptIn(UnstableApi::class)
class CacheManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val smartCropDetector = SmartCropDetector()

    // ExoPlayer Video Cache Directory (6 GB max for HD video and motion streams)
    val exoPlayerCache: SimpleCache by lazy {
        val cacheDir = File(context.cacheDir, "exoplayer_video_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(6000 * 1024 * 1024L) // 6GB
        val databaseProvider = StandaloneDatabaseProvider(context)
        SimpleCache(cacheDir, evictor, databaseProvider)
    }

    // ExoPlayer Cache DataSource Factory
    val cacheDataSourceFactory: CacheDataSource.Factory by lazy {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")

        val upstreamFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        CacheDataSource.Factory()
            .setCache(exoPlayerCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    // Cached Focal Points for Smart Crop (URL -> Offset)
    val focalPointCache = ConcurrentHashMap<String, androidx.compose.ui.geometry.Offset>()

    /**
     * Preloads images, pre-calculates smart crop focal points in background, and pre-buffers video stream thumbnails.
     */
    fun preloadItems(
        items: List<MediaItem>,
        currentIndex: Int,
        lookaheadDepth: Int = 10,
        lookbackDepth: Int = 3
    ) {
        if (items.isEmpty()) return

        val startIndex = (currentIndex - lookbackDepth).coerceAtLeast(0)
        val endIndex = (currentIndex + lookaheadDepth).coerceAtMost(items.size - 1)

        scope.launch {
            for (i in startIndex..endIndex) {
                if (i == currentIndex) continue

                val item = items[i]
                when (item) {
                    is MediaItem.Photo -> {
                        preloadImage(item.displayUrl)
                    }
                    is MediaItem.Video -> {
                        preloadImage(item.displayUrl)
                    }
                }
            }
        }
    }

    private fun preloadImage(url: String) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false) // Allow bitmap extraction for background Smart Crop calculation
            .crossfade(true)
            .target { drawable ->
                if (drawable is android.graphics.drawable.BitmapDrawable) {
                    val bitmap = drawable.bitmap
                    if (!focalPointCache.containsKey(url)) {
                        scope.launch(Dispatchers.Default) {
                            try {
                                val focal = smartCropDetector.calculateFocalPoint(bitmap)
                                focalPointCache[url] = focal
                            } catch (e: Exception) {
                                // Ignore crop errors
                            }
                        }
                    }
                }
            }
            .build()

        Coil.imageLoader(context).enqueue(request)
    }

    fun release() {
        try {
            exoPlayerCache.release()
            smartCropDetector.close()
        } catch (e: Exception) {
            // Ignore if already released
        }
    }
}

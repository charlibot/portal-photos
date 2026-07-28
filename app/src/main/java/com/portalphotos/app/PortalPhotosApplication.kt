package com.portalphotos.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.portalphotos.app.data.cache.CacheManager
import com.portalphotos.app.data.crop.SmartCropDetector
import com.portalphotos.app.data.db.PortalPhotosDatabase
import com.portalphotos.app.data.prefs.AppPreferences
import com.portalphotos.app.data.repository.AlbumRepository
import com.portalphotos.app.data.server.LocalWebServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PortalPhotosApplication : Application(), ImageLoaderFactory {

    lateinit var cacheManager: CacheManager
        private set
    lateinit var database: PortalPhotosDatabase
        private set
    lateinit var repository: AlbumRepository
        private set
    lateinit var appPreferences: AppPreferences
        private set
    lateinit var smartCropDetector: SmartCropDetector
        private set
    lateinit var localWebServer: LocalWebServer
        private set

    override fun onCreate() {
        super.onCreate()
        cacheManager = CacheManager(this)
        database = PortalPhotosDatabase.getDatabase(this)
        repository = AlbumRepository(database.albumDao())
        appPreferences = AppPreferences(this)
        smartCropDetector = SmartCropDetector()
        localWebServer = LocalWebServer(repository)

        // Seed default shared album URL if database is empty
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.addAlbumUrlAsync("https://photos.app.goo.gl/AwVsXmT9iwdmjKAa7")
            } catch (e: Exception) {
                // Ignore DB initialization race condition
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // 25% of available app RAM memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(4000L * 1024L * 1024L) // 4 GB disk cache limit for high-res photos
                    .build()
            }
            .crossfade(true)
            .build()
    }
}

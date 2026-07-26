package com.portalphotos.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.portalphotos.app.data.cache.CacheManager

class PortalPhotosApplication : Application(), ImageLoaderFactory {

    lateinit var cacheManager: CacheManager
        private set

    override fun onCreate() {
        super.onCreate()
        cacheManager = CacheManager(this)
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

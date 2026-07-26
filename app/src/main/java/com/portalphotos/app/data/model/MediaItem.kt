package com.portalphotos.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
sealed class MediaItem {
    abstract val id: String
    abstract val albumId: String
    abstract val albumTitle: String
    abstract val displayUrl: String
    abstract val timestamp: Long

    data class Photo(
        override val id: String,
        override val albumId: String,
        override val albumTitle: String,
        override val displayUrl: String,
        val width: Int = 0,
        val height: Int = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : MediaItem()

    data class Video(
        override val id: String,
        override val albumId: String,
        override val albumTitle: String,
        override val displayUrl: String, // Cover / Thumbnail image URL
        val streamUrl: String,            // Direct MP4 / Video playback stream URL
        val isLivePhoto: Boolean = false, // True if micro-video motion track (< 1.5MB)
        val durationMs: Long = 0L,
        val width: Int = 0,
        val height: Int = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : MediaItem()
}

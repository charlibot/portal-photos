package com.portalphotos.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey
    val id: String, // Normalized Album URL hash or unique ID
    val title: String,
    val shareUrl: String,
    val coverImageUrl: String? = null,
    val itemCount: Int = 0,
    val isSelected: Boolean = true,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "media_items",
    indices = [Index("albumId"), Index("timestamp")]
)
data class MediaItemEntity(
    @PrimaryKey
    val id: String,
    val albumId: String,
    val mediaUrl: String,
    val videoStreamUrl: String? = null,
    val isVideo: Boolean = false,
    val isLivePhoto: Boolean = false,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val albumTitle: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

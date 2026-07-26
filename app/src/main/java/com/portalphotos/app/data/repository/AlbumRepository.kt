package com.portalphotos.app.data.repository

import com.portalphotos.app.data.db.AlbumDao
import com.portalphotos.app.data.model.AlbumEntity
import com.portalphotos.app.data.model.MediaItem
import com.portalphotos.app.data.model.MediaItemEntity
import com.portalphotos.app.data.parser.SharedAlbumParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.security.MessageDigest

class AlbumRepository(
    private val albumDao: AlbumDao,
    private val parser: SharedAlbumParser = SharedAlbumParser()
) {

    private val bgScope = CoroutineScope(Dispatchers.IO)

    val allAlbums: Flow<List<AlbumEntity>> = albumDao.getAllAlbums()

    val selectedAlbums: Flow<List<AlbumEntity>> = albumDao.getSelectedAlbums()

    val mediaItemsForSelectedAlbums: Flow<List<MediaItem>> = albumDao.getMediaItemsForSelectedAlbums()
        .map { entities -> entities.map { it.toDomainModel() } }

    /**
     * Non-blocking Async Album Addition:
     * Instantly inserts a placeholder album entry into Room DB and returns immediately,
     * then fetches and populates album media items asynchronously in the background.
     */
    fun addAlbumUrlAsync(shareUrl: String, onComplete: ((Result<AlbumEntity>) -> Unit)? = null) {
        val cleanUrl = shareUrl.trim()
        val albumId = md5(cleanUrl)

        bgScope.launch {
            try {
                // Check if album already exists
                val existing = albumDao.getAlbumById(albumId)
                if (existing == null) {
                    // Create placeholder entry immediately
                    val placeholder = AlbumEntity(
                        id = albumId,
                        title = "Fetching album...",
                        shareUrl = cleanUrl,
                        coverImageUrl = null,
                        itemCount = 0,
                        isSelected = true,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                    albumDao.insertAlbum(placeholder)
                }

                // Perform asynchronous network fetch & parse in background
                val parsedResult = parser.parseAlbumUrl(cleanUrl)

                val updatedAlbum = AlbumEntity(
                    id = albumId,
                    title = parsedResult.title,
                    shareUrl = cleanUrl,
                    coverImageUrl = parsedResult.coverImageUrl ?: parsedResult.mediaItems.firstOrNull()?.mediaUrl,
                    itemCount = parsedResult.mediaItems.size,
                    isSelected = true,
                    lastSyncedAt = System.currentTimeMillis()
                )

                albumDao.insertAlbum(updatedAlbum)
                if (parsedResult.mediaItems.isNotEmpty()) {
                    albumDao.insertMediaItems(parsedResult.mediaItems)
                }

                onComplete?.invoke(Result.success(updatedAlbum))
            } catch (e: Exception) {
                onComplete?.invoke(Result.failure(e))
            }
        }
    }

    suspend fun addAlbumFromUrl(shareUrl: String): Result<AlbumEntity> {
        return runCatching {
            val parsedResult = parser.parseAlbumUrl(shareUrl)

            val albumEntity = AlbumEntity(
                id = parsedResult.id,
                title = parsedResult.title,
                shareUrl = shareUrl,
                coverImageUrl = parsedResult.coverImageUrl ?: parsedResult.mediaItems.firstOrNull()?.mediaUrl,
                itemCount = parsedResult.mediaItems.size,
                isSelected = true,
                lastSyncedAt = System.currentTimeMillis()
            )

            albumDao.insertAlbum(albumEntity)
            if (parsedResult.mediaItems.isNotEmpty()) {
                albumDao.insertMediaItems(parsedResult.mediaItems)
            }

            albumEntity
        }
    }

    suspend fun toggleAlbumSelection(albumId: String, isSelected: Boolean) {
        albumDao.updateAlbumSelection(albumId, isSelected)
    }

    suspend fun deleteAlbum(albumId: String) {
        albumDao.deleteMediaItemsForAlbum(albumId)
        albumDao.deleteAlbum(albumId)
    }

    suspend fun refreshAlbum(albumId: String): Result<Unit> {
        return runCatching {
            val album = albumDao.getAlbumById(albumId)
                ?: throw IllegalArgumentException("Album not found")

            val parsedResult = parser.parseAlbumUrl(album.shareUrl)

            val updatedAlbum = album.copy(
                title = parsedResult.title,
                coverImageUrl = parsedResult.coverImageUrl ?: parsedResult.mediaItems.firstOrNull()?.mediaUrl ?: album.coverImageUrl,
                itemCount = parsedResult.mediaItems.size,
                lastSyncedAt = System.currentTimeMillis()
            )

            albumDao.insertAlbum(updatedAlbum)
            if (parsedResult.mediaItems.isNotEmpty()) {
                albumDao.insertMediaItems(parsedResult.mediaItems)
            }
        }
    }

    suspend fun refreshAllAlbums() {
        val albums = allAlbums.first()
        for (album in albums) {
            refreshAlbum(album.id)
        }
    }

    private fun MediaItemEntity.toDomainModel(): MediaItem {
        return if (isVideo && !videoStreamUrl.isNullOrEmpty()) {
            MediaItem.Video(
                id = id,
                albumId = albumId,
                albumTitle = albumTitle,
                displayUrl = mediaUrl,
                streamUrl = videoStreamUrl,
                isLivePhoto = isLivePhoto,
                durationMs = durationMs,
                width = width,
                height = height,
                timestamp = timestamp
            )
        } else {
            MediaItem.Photo(
                id = id,
                albumId = albumId,
                albumTitle = albumTitle,
                displayUrl = mediaUrl,
                width = width,
                height = height,
                timestamp = timestamp
            )
        }
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

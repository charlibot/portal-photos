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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlbumRepository(
    private val albumDao: AlbumDao,
    private val parser: SharedAlbumParser = SharedAlbumParser()
) {

    private val bgScope = CoroutineScope(Dispatchers.IO)

    val allAlbums: Flow<List<AlbumEntity>> = albumDao.getAllAlbums()

    val selectedAlbums: Flow<List<AlbumEntity>> = albumDao.getSelectedAlbums()

    val mediaItemsForSelectedAlbums: Flow<List<MediaItem>> = albumDao.getMediaItemsForSelectedAlbums()
        .map { entities -> entities.map { it.toDomainModel() } }

    fun addAlbumUrlAsync(shareUrl: String, onComplete: ((Result<AlbumEntity>) -> Unit)? = null) {
        val cleanUrl = shareUrl.trim()
        val albumId = md5(cleanUrl)

        bgScope.launch {
            try {
                val existing = albumDao.getAlbumById(albumId)
                if (existing == null) {
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

                val result = addAlbumFromUrl(cleanUrl)
                onComplete?.invoke(result)
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
                lastSyncedAt = System.currentTimeMillis(),
                albumDate = parsedResult.albumDate
            )

            albumDao.insertAlbum(albumEntity)
            if (parsedResult.mediaItems.isNotEmpty()) {
                albumDao.deleteMediaItemsForAlbum(parsedResult.id)
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
                lastSyncedAt = System.currentTimeMillis(),
                albumDate = parsedResult.albumDate ?: album.albumDate
            )

            albumDao.insertAlbum(updatedAlbum)
            if (parsedResult.mediaItems.isNotEmpty()) {
                albumDao.deleteMediaItemsForAlbum(albumId)
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

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

fun MediaItemEntity.toDomainModel(): MediaItem {
    return if (this.isVideo) {
        MediaItem.Video(
            id = this.id,
            albumId = this.albumId,
            albumTitle = this.albumTitle,
            displayUrl = this.mediaUrl,
            streamUrl = this.videoStreamUrl ?: this.mediaUrl,
            isLivePhoto = this.isLivePhoto,
            durationMs = this.durationMs,
            width = this.width,
            height = this.height,
            timestamp = this.timestamp
        )
    } else {
        MediaItem.Photo(
            id = this.id,
            albumId = this.albumId,
            albumTitle = this.albumTitle,
            displayUrl = this.mediaUrl,
            width = this.width,
            height = this.height,
            timestamp = this.timestamp
        )
    }
}

package com.portalphotos.app.data.db

import androidx.room.*
import com.portalphotos.app.data.model.AlbumEntity
import com.portalphotos.app.data.model.MediaItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY lastSyncedAt DESC")
    fun getAllAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE isSelected = 1")
    fun getSelectedAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :albumId LIMIT 1")
    suspend fun getAlbumById(albumId: String): AlbumEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumEntity)

    @Query("UPDATE albums SET isSelected = :isSelected WHERE id = :albumId")
    suspend fun updateAlbumSelection(albumId: String, isSelected: Boolean)

    @Query("DELETE FROM albums WHERE id = :albumId")
    suspend fun deleteAlbum(albumId: String)

    @Query("SELECT media_items.* FROM media_items INNER JOIN albums ON media_items.albumId = albums.id WHERE albums.isSelected = 1 ORDER BY media_items.timestamp ASC")
    fun getMediaItemsForSelectedAlbums(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE albumId = :albumId ORDER BY timestamp ASC")
    suspend fun getMediaItemsForAlbum(albumId: String): List<MediaItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItems(items: List<MediaItemEntity>)

    @Query("DELETE FROM media_items WHERE albumId = :albumId")
    suspend fun deleteMediaItemsForAlbum(albumId: String)
}

package com.portalphotos.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.portalphotos.app.data.model.AlbumEntity
import com.portalphotos.app.data.model.MediaItemEntity

@Database(
    entities = [AlbumEntity::class, MediaItemEntity::class],
    version = 2,
    exportSchema = false
)
abstract class PortalPhotosDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao

    companion object {
        @Volatile
        private var INSTANCE: PortalPhotosDatabase? = null

        fun getDatabase(context: Context): PortalPhotosDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PortalPhotosDatabase::class.java,
                    "portal_photos_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}

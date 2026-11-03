package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookmarkEntity::class,
        RecentFileEntity::class,
        NetworkProfileEntity::class,
        CloudAccountEntity::class,
        TrashItemEntity::class,
        TransferJobEntity::class,
        SearchQueryEntity::class,
        SafGrantEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class OpenFileDatabase : RoomDatabase() {
    abstract fun dao(): OpenFileDao

    companion object {
        @Volatile
        private var INSTANCE: OpenFileDatabase? = null

        @Volatile
        private var boundAppContext: Context? = null

        fun getInstance(context: Context): OpenFileDatabase {
            val appCtx = context.applicationContext
            val existing = INSTANCE
            if (existing != null && boundAppContext === appCtx) {
                return existing
            }
            return synchronized(this) {
                val current = INSTANCE
                if (current != null && boundAppContext === appCtx) {
                    current
                } else {
                    Room.databaseBuilder(
                        appCtx,
                        OpenFileDatabase::class.java,
                        "openfile_metadata.db"
                    )
                        .fallbackToDestructiveMigration(true)
                        .build()
                        .also {
                            INSTANCE = it
                            boundAppContext = appCtx
                        }
                }
            }
        }
    }
}

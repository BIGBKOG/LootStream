package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MediaItemEntity::class,
        MediaServerEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun mediaServerDao(): MediaServerDao

    companion object {
        const val DB_NAME = "lootstream_media.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                ).fallbackToDestructiveMigration(true)
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("PRAGMA synchronous = NORMAL")
                            db.execSQL("PRAGMA temp_store = MEMORY")
                            db.execSQL("PRAGMA cache_size = -2000")
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun vacuumAndCompact(context: Context) {
            runCatching {
                val db = getInstance(context)
                val writableDb = db.openHelper.writableDatabase
                writableDb.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
                writableDb.execSQL("VACUUM")
            }
        }
    }
}

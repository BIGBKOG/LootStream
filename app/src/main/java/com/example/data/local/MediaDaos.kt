package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items ORDER BY lastPlayedAt DESC, addedAt DESC")
    fun observeAll(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items")
    suspend fun getAllOnce(): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MediaItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MediaItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MediaItemEntity>)

    @Query(
        "UPDATE media_items SET playbackPositionMs = :positionMs, durationMs = CASE WHEN :durationMs > 0 THEN :durationMs ELSE durationMs END, watchStatus = :watchStatus, lastPlayedAt = :lastPlayedAt, playCount = playCount + :playIncrement WHERE id = :id"
    )
    suspend fun updatePlaybackProgress(
        id: String,
        positionMs: Long,
        durationMs: Long,
        watchStatus: String,
        lastPlayedAt: Long,
        playIncrement: Int = 0
    )

    @Query("UPDATE media_items SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE media_items SET isWatchlist = :isWatchlist WHERE id = :id")
    suspend fun updateWatchlist(id: String, isWatchlist: Boolean)

    @Query("UPDATE media_items SET watchStatus = :watchStatus WHERE id = :id")
    suspend fun updateWatchStatus(id: String, watchStatus: String)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM media_items WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: String)

    @Query("DELETE FROM media_items WHERE sourceType != 'PC_HOST'")
    suspend fun deleteAllNonServerMedia()

    @Query("DELETE FROM media_items")
    suspend fun clearAll()
}

@Dao
interface MediaServerDao {
    @Query("SELECT * FROM media_servers ORDER BY lastSyncedAt DESC")
    fun observeAll(): Flow<List<MediaServerEntity>>

    @Query("SELECT * FROM media_servers")
    suspend fun getAllOnce(): List<MediaServerEntity>

    @Query("SELECT * FROM media_servers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MediaServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(server: MediaServerEntity)

    @Query("DELETE FROM media_servers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM media_servers")
    suspend fun clearAll()
}

package com.exapps.velox.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.exapps.velox.core.data.local.entity.PlaylistEntity
import com.exapps.velox.core.data.local.entity.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY createdAtEpochSeconds DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun trackCount(playlistId: Long): Int

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    /** Phase 2 backup/restore: merge-by-name support. */
    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): PlaylistEntity?

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Insert
    suspend fun insertItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND mediaItemId = :mediaItemId")
    suspend fun removeItem(playlistId: Long, mediaItemId: Long)

    @Transaction
    suspend fun addTracksAtEnd(playlistId: Long, mediaItemIds: List<Long>) {
        var next = maxPosition(playlistId) + 1
        insertItems(mediaItemIds.map { PlaylistItemEntity(playlistId, it, next++) })
    }

    @Transaction
    suspend fun reorder(playlistId: Long, fromPosition: Int, toPosition: Int) {
        val items = observeItemsSnapshot(playlistId).toMutableList()
        if (fromPosition !in items.indices || toPosition !in items.indices) return
        val moved = items.removeAt(fromPosition)
        items.add(toPosition, moved)
        // Delete + reinsert rather than updating positions in place: the primary key
        // is (playlistId, position), so shifting positions one row at a time can
        // transiently collide with a row that hasn't moved yet. A full replace for
        // one playlist's item list is cheap and always correct.
        deleteAllItems(playlistId)
        insertItems(items.mapIndexed { index, item -> item.copy(position = index) })
    }

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun observeItemsSnapshot(playlistId: Long): List<PlaylistItemEntity>

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deleteAllItems(playlistId: Long)
}

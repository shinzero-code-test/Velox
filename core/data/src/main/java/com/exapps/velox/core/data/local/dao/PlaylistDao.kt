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

    /**
     * M13 (data-layer review): the previous version did a SELECT MAX(position)
     * followed by an INSERT in two separate statements, so two concurrent
     * callers could both compute the same starting position and the second
     * INSERT would violate the (playlistId, position) primary key. The
     * repository now serialises per-playlist writes through a Mutex (see
     * PlaylistRepositoryImpl.addTracks) and the DAO additionally re-numbers
     * positions densely so the (playlistId, position) PK is always 0..N-1.
     */
    @Transaction
    suspend fun addTracksAtEnd(playlistId: Long, mediaItemIds: List<Long>) {
        if (mediaItemIds.isEmpty()) return
        var next = maxPosition(playlistId) + 1
        insertItems(mediaItemIds.map { PlaylistItemEntity(playlistId, it, next++) })
        // Defensive: in case the same (playlistId, mediaItemId) appears more than
        // once in the table (legacy state or a failed concurrent insert that
        // slipped past the repository lock), collapse to a single row at the
        // earliest position. The (playlistId, position) PK cannot prevent this
        // because two different mediaItemId values can collide on (playlistId,
        // position) only after a renumber.
        deleteDuplicatePlaceholders(playlistId)
        renumberPositions(playlistId)
    }

    /** M13: keep only the earliest (lowest rowid) row per (playlistId, mediaItemId). */
    @Query(
        """DELETE FROM playlist_items
           WHERE playlistId = :playlistId
             AND rowid NOT IN (
                 SELECT MIN(rowid) FROM playlist_items
                 WHERE playlistId = :playlistId
                 GROUP BY mediaItemId
             )"""
    )
    suspend fun deleteDuplicatePlaceholders(playlistId: Long)

    /** M13: assign 0..N-1 by ordering on the existing position column. */
    @Query(
        """UPDATE playlist_items SET position = (
               SELECT COUNT(*) FROM playlist_items pi2
               WHERE pi2.playlistId = playlist_items.playlistId
                 AND pi2.position < playlist_items.position
           )
           WHERE playlistId = :playlistId"""
    )
    suspend fun renumberPositions(playlistId: Long)

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


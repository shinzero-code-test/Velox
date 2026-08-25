package com.exapps.velox.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.exapps.velox.core.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

/** Phase 2 "Bookmarks" — per-item marker CRUD. */
@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE mediaItemId = :mediaItemId ORDER BY positionMs ASC")
    fun observeForItem(mediaItemId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAtEpochSeconds DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun delete(bookmarkId: Long)

    @Query("DELETE FROM bookmarks WHERE mediaItemId = :mediaItemId")
    suspend fun deleteForItem(mediaItemId: Long)

    @Query("SELECT COUNT(*) FROM bookmarks")
    suspend fun count(): Int
}

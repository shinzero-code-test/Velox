package com.exapps.velox.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.exapps.velox.core.data.local.entity.PlayHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayHistoryDao {
    @Insert
    suspend fun insert(entry: PlayHistoryEntity)

    /** Phase 2 backup/restore + statistics. */
    @Query("SELECT * FROM play_history ORDER BY playedAtEpochSeconds DESC")
    fun observeAll(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT COUNT(*) FROM play_history WHERE mediaItemId = :mediaItemId")
    suspend fun countForItem(mediaItemId: Long): Int

    /** Keeps the table from growing unbounded on heavy listeners — Recently/Most
     * Played reads off MediaItemEntity's denormalized columns, so history beyond
     * this cap only matters once Phase 2's history-with-statistics view exists. */
    @Query(
        """DELETE FROM play_history WHERE id NOT IN (
             SELECT id FROM play_history ORDER BY playedAtEpochSeconds DESC LIMIT :keepMostRecent
           )""",
    )
    suspend fun trimTo(keepMostRecent: Int)

    /** Settings → Storage → "Clear playback history" (SCREEN_SETTINGS.md §8). */
    @Query("DELETE FROM play_history")
    suspend fun clearAll()
}

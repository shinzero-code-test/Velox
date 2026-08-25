package com.exapps.velox.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Aggregation rows for the Statistics screen (Phase 2). */
data class TrackPlayAggregate(
    val mediaItemId: Long,
    val title: String,
    val artistName: String?,
    val artworkUri: String?,
    val durationMs: Long,
    val plays: Int,
)

data class DayPlayCount(val dayEpochSeconds: Long, val plays: Int)

data class PlaybackTotals(val plays: Int, val distinctTracks: Int, val totalMs: Long)

/** Phase 2 "Playback statistics & history": read-only aggregates over play_history. */
@Dao
interface StatsDao {

    @Query(
        """SELECT m.id AS mediaItemId, m.title AS title, m.artistName AS artistName,
                  m.artworkUri AS artworkUri, m.durationMs AS durationMs, COUNT(*) AS plays
           FROM play_history h JOIN media_items m ON m.id = h.mediaItemId
           GROUP BY h.mediaItemId ORDER BY plays DESC LIMIT :limit"""
    )
    fun observeMostPlayedTracks(limit: Int): Flow<List<TrackPlayAggregate>>

    /** Day buckets keyed by UTC-day epoch seconds (playedAt is stored in seconds). */
    @Query(
        """SELECT (h.playedAtEpochSeconds / 86400) * 86400 AS dayEpochSeconds, COUNT(*) AS plays
           FROM play_history h GROUP BY dayEpochSeconds ORDER BY dayEpochSeconds DESC LIMIT :days"""
    )
    fun observeDailyPlays(days: Int): Flow<List<DayPlayCount>>

    @Query(
        """SELECT COUNT(*) AS plays,
                  COUNT(DISTINCT h.mediaItemId) AS distinctTracks,
                  COALESCE(SUM(m.durationMs), 0) AS totalMs
           FROM play_history h JOIN media_items m ON m.id = h.mediaItemId"""
    )
    fun observeTotals(): Flow<PlaybackTotals>
}

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

    /**
     * Day buckets keyed by local-day epoch seconds (M5: was UTC, which made
     * "today" shift by an entire day once the user crossed a midnight UTC
     * boundary that wasn't midnight locally). The bucket is computed at query
     * time using the device's offset-at-the-time-of-the-row — the GROUP BY
     * uses the offset at query time, which is a small but accepted trade-off
     * (rows written yesterday with a different offset would still bucket into
     * the same offset-today, which is the desired UX).
     */
    @Query(
        """SELECT ((h.playedAtEpochSeconds + :offsetSeconds) / 86400) * 86400 - :offsetSeconds
                  AS dayEpochSeconds, COUNT(*) AS plays
           FROM play_history h GROUP BY dayEpochSeconds ORDER BY dayEpochSeconds DESC LIMIT :days"""
    )
    fun observeDailyPlays(days: Int, offsetSeconds: Long): Flow<List<DayPlayCount>>

    @Query(
        """SELECT COUNT(*) AS plays,
                  COUNT(DISTINCT h.mediaItemId) AS distinctTracks,
                  COALESCE(SUM(m.durationMs), 0) AS totalMs
           FROM play_history h JOIN media_items m ON m.id = h.mediaItemId"""
    )
    fun observeTotals(): Flow<PlaybackTotals>
}

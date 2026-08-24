package com.exapps.velox.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Only [PlaylistType.USER] playlists (see domain model) are persisted here — the
 * system playlists (Favorites, Recently Played, ...) are derived queries against
 * [MediaItemEntity], not rows in this table. */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtEpochSeconds: Long,
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId"), Index("mediaItemId")],
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val mediaItemId: Long,
    val position: Int,
)

/** Backs "Recently Played" / "Most Played" (FEATURES.md §2) with one row per play
 * event, rather than only a rolling `lastPlayedAt` on [MediaItemEntity] — this is
 * what makes a true play-count-over-time history possible later (Phase 2 "Playback
 * history with statistics"). */
@Entity(tableName = "play_history", indices = [Index("mediaItemId"), Index("playedAtEpochSeconds")])
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaItemId: Long,
    val playedAtEpochSeconds: Long,
)

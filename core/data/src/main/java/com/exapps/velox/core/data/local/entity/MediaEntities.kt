package com.exapps.velox.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * TECHNICAL_PLAN.md §6.4: Room entities for the media library. The primary key mirrors
 * `MediaStore`'s own `_id` for the matching content URI (audio vs. video), so re-scans
 * can upsert by id instead of diffing full rows.
 */
@Entity(
    tableName = "media_items",
    indices = [Index("folderPath"), Index("albumId"), Index("artistName"), Index("mediaType")],
)
data class MediaItemEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val title: String,
    @ColumnInfo(name = "mediaType") val mediaType: String, // MediaType.name
    val durationMs: Long,
    val artistName: String?,
    val albumId: Long?,
    val albumTitle: String?,
    val artworkUri: String?,
    val folderPath: String?,
    val dateAddedEpochSeconds: Long,
    val sizeBytes: Long,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedEpochSeconds: Long? = null,
)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artistName: String?,
    val artworkUri: String?,
    val trackCount: Int,
    val year: Int?,
    val totalDurationMs: Long,
)

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val artworkUri: String?,
)

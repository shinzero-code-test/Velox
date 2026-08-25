package com.exapps.velox.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 2 "Bookmarks": named timestamped markers on a media item. Also the storage
 * behind the Chapters sheet's "Your markers" section.
 */
@Entity(
    tableName = "bookmarks",
    indices = [Index("mediaItemId")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaItemId: Long,
    val positionMs: Long,
    val label: String,
    val createdAtEpochSeconds: Long,
)

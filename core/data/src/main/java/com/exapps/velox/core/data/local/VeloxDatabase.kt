package com.exapps.velox.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.exapps.velox.core.data.local.dao.AlbumDao
import com.exapps.velox.core.data.local.dao.BookmarkDao
import com.exapps.velox.core.data.local.dao.ArtistDao
import com.exapps.velox.core.data.local.dao.MediaItemDao
import com.exapps.velox.core.data.local.dao.PlaylistDao
import com.exapps.velox.core.data.local.dao.StatsDao
import com.exapps.velox.core.data.local.entity.AlbumEntity
import com.exapps.velox.core.data.local.entity.BookmarkEntity
import com.exapps.velox.core.data.local.entity.ArtistEntity
import com.exapps.velox.core.data.local.entity.MediaItemEntity
import com.exapps.velox.core.data.local.entity.PlayHistoryEntity
import com.exapps.velox.core.data.local.entity.PlaylistEntity
import com.exapps.velox.core.data.local.entity.PlaylistItemEntity

@Database(
    entities = [
        MediaItemEntity::class,
        BookmarkEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        PlayHistoryEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class VeloxDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playHistoryDao(): com.exapps.velox.core.data.local.dao.PlayHistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun statsDao(): StatsDao

    companion object {
        const val DATABASE_NAME = "velox.db"
    }
}

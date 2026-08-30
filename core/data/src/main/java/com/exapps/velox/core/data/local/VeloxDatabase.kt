package com.exapps.velox.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.exapps.velox.core.data.local.dao.AlbumDao
import com.exapps.velox.core.data.local.dao.BookmarkDao
import com.exapps.velox.core.data.local.dao.ArtistDao
import com.exapps.velox.core.data.local.dao.MediaItemDao
import com.exapps.velox.core.data.local.dao.PlaylistDao
import com.exapps.velox.core.data.local.dao.StatsDao
import com.exapps.velox.core.data.local.dao.TrackAnalysisDao
import com.exapps.velox.core.data.local.entity.AlbumEntity
import com.exapps.velox.core.data.local.entity.BookmarkEntity
import com.exapps.velox.core.data.local.entity.ArtistEntity
import com.exapps.velox.core.data.local.entity.MediaItemEntity
import com.exapps.velox.core.data.local.entity.PlayHistoryEntity
import com.exapps.velox.core.data.local.entity.PlaylistEntity
import com.exapps.velox.core.data.local.entity.PlaylistItemEntity
import com.exapps.velox.core.data.local.entity.TrackChapterEntity
import com.exapps.velox.core.data.local.entity.TrackIntroOutroEntity

@Database(
    entities = [
        MediaItemEntity::class,
        BookmarkEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        PlayHistoryEntity::class,
        // Phase 3 / Wave 3 / Round 2: silence-run results and
        // auto-generated chapter boundaries for the audio-analysis
        // module. Added in migration 3→4.
        TrackIntroOutroEntity::class,
        TrackChapterEntity::class,
    ],
    version = 4,
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
    abstract fun trackAnalysisDao(): TrackAnalysisDao

    companion object {
        const val DATABASE_NAME = "velox.db"
    }
}

package com.exapps.velox.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Room
import com.exapps.velox.core.data.local.VeloxDatabase
import com.exapps.velox.core.data.local.dao.AlbumDao
import com.exapps.velox.core.data.local.dao.BookmarkDao
import com.exapps.velox.core.data.local.dao.StatsDao
import com.exapps.velox.core.data.local.dao.ArtistDao
import com.exapps.velox.core.data.local.dao.MediaItemDao
import com.exapps.velox.core.data.local.dao.PlayHistoryDao
import com.exapps.velox.core.data.local.dao.PlaylistDao
import com.exapps.velox.core.data.repository.MediaLibraryRepositoryImpl
import com.exapps.velox.core.data.repository.PlaylistRepositoryImpl
import com.exapps.velox.core.domain.repository.MediaLibraryRepository
import com.exapps.velox.core.data.preferences.PlaybackPositionStoreImpl
import com.exapps.velox.core.domain.player.PlaybackPositionStore
import com.exapps.velox.core.domain.repository.PlaylistRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.veloxPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "velox_preferences")

/** Ordered migration history. v2: genre + fileName on media_items (Phase 1 M2/M3:
 * Genres tab + sidecar lyrics lookups). */
/** Ordered migration history.
 *
 * C1 (data-layer review): `ADD COLUMN ... DEFAULT NULL` is deliberately avoided —
 * SQLite stores an explicit default of NULL which Room's TableInfo comparison
 * reports as `"NULL"` vs the entity's absent default → post-migration validation
 * crash. Bare ADD COLUMN matches the entity (Kotlin defaults are compile-time).
 */
internal val ALL_DATABASE_MIGRATIONS: Array<Migration> = arrayOf(
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE media_items ADD COLUMN fileName TEXT")
            db.execSQL("ALTER TABLE media_items ADD COLUMN genre TEXT")
        }
    },
    // Phase 2 bookmarks.
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `bookmarks` (
                   `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                   `mediaItemId` INTEGER NOT NULL,
                   `positionMs` INTEGER NOT NULL,
                   `label` TEXT NOT NULL,
                   `createdAtEpochSeconds` INTEGER NOT NULL)
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_mediaItemId ON bookmarks (mediaItemId)")
        }
    },
)

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideVeloxDatabase(@ApplicationContext context: Context): VeloxDatabase =
        Room.databaseBuilder(context, VeloxDatabase::class.java, VeloxDatabase.DATABASE_NAME)
            .addMigrations(*ALL_DATABASE_MIGRATIONS)
            .apply {
                // M14 (data-layer review): the destructive escape hatch is now
                // debug-builds-only. All shipped schema versions have explicit
                // migrations (1→2→3), so a missing path in release should crash
                // loudly during development instead of wiping user data silently.
                if (com.exapps.velox.core.data.BuildConfig.DEBUG) {
                    fallbackToDestructiveMigration(dropAllTables = false)
                }
            }
            .build()

    @Provides
    fun provideMediaItemDao(database: VeloxDatabase): MediaItemDao = database.mediaItemDao()

    @Provides
    fun provideAlbumDao(database: VeloxDatabase): AlbumDao = database.albumDao()

    @Provides
    fun provideArtistDao(database: VeloxDatabase): ArtistDao = database.artistDao()

    @Provides
    fun providePlaylistDao(database: VeloxDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun providePlayHistoryDao(database: VeloxDatabase): PlayHistoryDao = database.playHistoryDao()

    @Provides
    fun provideBookmarkDao(database: VeloxDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun provideStatsDao(database: VeloxDatabase): StatsDao = database.statsDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.veloxPreferencesDataStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMediaLibraryRepository(impl: MediaLibraryRepositoryImpl): MediaLibraryRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackPositionStore(impl: PlaybackPositionStoreImpl): PlaybackPositionStore
}

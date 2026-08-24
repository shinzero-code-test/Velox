package com.exapps.velox.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.exapps.velox.core.data.local.VeloxDatabase
import com.exapps.velox.core.data.local.dao.AlbumDao
import com.exapps.velox.core.data.local.dao.ArtistDao
import com.exapps.velox.core.data.local.dao.MediaItemDao
import com.exapps.velox.core.data.local.dao.PlayHistoryDao
import com.exapps.velox.core.data.local.dao.PlaylistDao
import com.exapps.velox.core.data.repository.MediaLibraryRepositoryImpl
import com.exapps.velox.core.data.repository.PlaylistRepositoryImpl
import com.exapps.velox.core.domain.repository.MediaLibraryRepository
import com.exapps.velox.core.domain.repository.PlaylistRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.veloxPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "velox_preferences")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideVeloxDatabase(@ApplicationContext context: Context): VeloxDatabase =
        Room.databaseBuilder(context, VeloxDatabase::class.java, VeloxDatabase.DATABASE_NAME)
            // Phase 0 has no prior schema version to migrate from. The first real
            // migration should replace this before the room.schemaDirectory history
            // (core/data/schemas/) gets a v2 folder — see PROGRESS.md.
            .fallbackToDestructiveMigration(dropAllTables = false)
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
}

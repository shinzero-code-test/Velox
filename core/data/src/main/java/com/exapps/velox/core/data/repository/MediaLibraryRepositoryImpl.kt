package com.exapps.velox.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.exapps.velox.core.common.di.IoDispatcher
import com.exapps.velox.core.data.local.dao.AlbumDao
import com.exapps.velox.core.data.local.dao.ArtistDao
import com.exapps.velox.core.data.local.dao.MediaItemDao
import com.exapps.velox.core.data.local.entity.PlayHistoryEntity
import com.exapps.velox.core.data.local.mapper.toDomain
import com.exapps.velox.core.data.scanner.MediaStoreScanner
import com.exapps.velox.core.domain.model.Album
import com.exapps.velox.core.domain.model.Artist
import com.exapps.velox.core.domain.model.Folder
import com.exapps.velox.core.domain.model.Genre
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.model.MediaType
import com.exapps.velox.core.domain.model.SortOrder
import com.exapps.velox.core.domain.recommendation.RecommendationEngine
import com.exapps.velox.core.domain.repository.MediaLibraryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import androidx.room.withTransaction
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaLibraryRepositoryImpl @Inject constructor(
    private val mediaItemDao: MediaItemDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val playHistoryDao: com.exapps.velox.core.data.local.dao.PlayHistoryDao,
    private val bookmarkDao: com.exapps.velox.core.data.local.dao.BookmarkDao,
    private val scanner: MediaStoreScanner,
    private val database: com.exapps.velox.core.data.local.VeloxDatabase,
    private val preferencesDataStore: DataStore<Preferences>,
    // Phase 3 / Wave 3 / Round 3 — Milestone 7. Notify the
    // recommender whenever the play history changes so the
    // forYou / upNext / becauseYouListened flows re-emit on the
    // next subscription.
    private val recommendationEngine: RecommendationEngine,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MediaLibraryRepository {

    override fun observeTracks(sortOrder: SortOrder): Flow<List<MediaItem>> =
        mediaItemDao.observeByTypeOrderedByTitle(MediaType.AUDIO.name)
            .map { entities -> entities.map { it.toDomain() }.sortedWith(sortOrder) }

    override fun observeVideos(sortOrder: SortOrder): Flow<List<MediaItem>> =
        mediaItemDao.observeByTypeOrderedByTitle(MediaType.VIDEO.name)
            .map { entities -> entities.map { it.toDomain() }.sortedWith(sortOrder) }

    override fun observeAlbums(): Flow<List<Album>> =
        albumDao.observeAll().map { it.map { entity -> entity.toDomain() } }

    override fun observeArtists(): Flow<List<Artist>> =
        artistDao.observeAll().map { it.map { entity -> entity.toDomain() } }

    override fun observeFolders(parentPath: String?): Flow<List<Folder>> =
        mediaItemDao.observeFolderSummaries().map { summaries ->
            summaries
                .filter { parentPath == null || File(it.path).parent == parentPath }
                .map { summary ->
                    Folder(
                        path = summary.path,
                        displayName = File(summary.path).name,
                        itemCount = summary.count,
                        parentPath = File(summary.path).parent,
                    )
                }
        }

    override fun observeFolderContents(path: String): Flow<List<MediaItem>> =
        mediaItemDao.observeByFolder(path).map { it.map { entity -> entity.toDomain() } }

    override fun observeGenres(): Flow<List<Genre>> =
        mediaItemDao.observeGenreSummaries().map { list -> list.map { Genre(it.name, it.trackCount) } }

    override fun observeGenreTracks(genre: String): Flow<List<MediaItem>> =
        mediaItemDao.observeByGenre(genre).map { it.map { entity -> entity.toDomain() } }

    // Phase 2 bookmarks
    override fun observeBookmarks(mediaItemId: Long) =
        bookmarkDao.observeForItem(mediaItemId).map { list ->
            list.map { com.exapps.velox.core.domain.model.Bookmark(it.id, it.mediaItemId, it.positionMs, it.label) }
        }

    override suspend fun addBookmark(mediaItemId: Long, positionMs: Long, label: String): Long =
        bookmarkDao.insert(
            com.exapps.velox.core.data.local.entity.BookmarkEntity(
                mediaItemId = mediaItemId,
                positionMs = positionMs,
                label = label,
                createdAtEpochSeconds = System.currentTimeMillis() / 1000,
            ),
        )

    override suspend fun deleteBookmark(bookmarkId: Long) = bookmarkDao.delete(bookmarkId)

    override fun observeAlbumTracks(albumId: Long): Flow<List<MediaItem>> =
        mediaItemDao.observeByAlbum(albumId).map { it.map { entity -> entity.toDomain() } }

    override fun observeArtistTracks(artistId: Long): Flow<List<MediaItem>> =
        // Artist rows are keyed by a hash of their name (see MediaStoreScanner) since
        // MediaStore doesn't expose a stable artist id — look the name back up rather
        // than threading a second id scheme through the DAO layer.
        flow {
            val artist = artistDao.getById(artistId)
            if (artist == null) {
                emit(emptyList())
            } else {
                emitAll(mediaItemDao.observeByArtist(artist.name).map { entities -> entities.map { it.toDomain() } })
            }
        }

    override fun observeFavorites(): Flow<List<MediaItem>> =
        mediaItemDao.observeFavorites().map { it.map { entity -> entity.toDomain() } }

    override fun observeRecentlyPlayed(limit: Int): Flow<List<MediaItem>> =
        mediaItemDao.observeRecentlyPlayed(limit).map { it.map { entity -> entity.toDomain() } }

    override fun observeMostPlayed(limit: Int): Flow<List<MediaItem>> =
        mediaItemDao.observeMostPlayed(limit).map { it.map { entity -> entity.toDomain() } }

    override suspend fun getById(id: Long): MediaItem? =
        withContext(ioDispatcher) { mediaItemDao.getById(id)?.toDomain() }

    override fun search(query: String, type: MediaType?): Flow<List<MediaItem>> =
        mediaItemDao.search(MediaItemDao.escapeLike(query), type?.name)
            .map { it.map { entity -> entity.toDomain() } }

    override suspend fun setFavorite(id: Long, favorite: Boolean) =
        withContext(ioDispatcher) { mediaItemDao.setFavorite(id, favorite) }

    override suspend fun updateTrackMetadata(id: Long, title: String, artistName: String?, albumTitle: String?) =
        withContext(ioDispatcher) {
            mediaItemDao.updateTrackMetadata(id, title.trim(), artistName?.trim()?.ifEmpty { null }, albumTitle?.trim()?.ifEmpty { null })
        }

    override suspend fun recordPlayed(id: Long) = withContext(ioDispatcher) {
        val now = System.currentTimeMillis() / 1000
        mediaItemDao.recordPlayed(id, now)
        playHistoryDao.insert(PlayHistoryEntity(mediaItemId = id, playedAtEpochSeconds = now))
        // M5 (data-layer review): the KDoc promised a cap; enforce it so the
        // history table can't grow unbounded on heavy listeners.
        playHistoryDao.trimTo(PLAY_HISTORY_KEEP_MOST_RECENT)
        // Milestone 7: notify the recommender. The next subscriber
        // to forYou/upNext/becauseYouListened re-runs the build.
        recommendationEngine.onPlayHistoryChanged()
    }

    override suspend fun rescanLibrary() = withContext(ioDispatcher) {
        // M3 (data-layer review): snapshot → upsert → delete → restore spans many
        // statements. A concurrent recordPlayed between snapshot and restore used to
        // be overwritten by the stale snapshot, and a mid-way crash left the tables
        // torn — run the whole DB portion as one transaction.
        database.withTransaction {
            // H1 (data-layer review): snapshot EVERY row (see getUserMetadataSnapshot)
            // so tag-editor-only overrides survive the REPLACE-upsert too.
            val userMetadata = mediaItemDao.getUserMetadataSnapshot()

            val result = scanner.scan()
            mediaItemDao.upsertAll(result.mediaItems)

            // C2 (data-layer review): empty scan results previously produced invalid
            // `NOT IN ()` SQL and silently aborted the whole rescan. Skip deletion on
            // an empty result set — that's protective against transient MediaStore
            // failures rather than destructive.
            val mediaIds = result.mediaItems.map { it.id }
            if (mediaIds.isNotEmpty()) mediaItemDao.deleteMissing(mediaIds)

            userMetadata.forEach { meta ->
                if (mediaIds.isEmpty() || mediaIds.contains(meta.id)) {
                    mediaItemDao.restoreUserMetadata(
                        id = meta.id,
                        title = meta.title,
                        artistName = meta.artistName,
                        albumTitle = meta.albumTitle,
                        isFavorite = meta.isFavorite,
                        playCount = meta.playCount,
                        lastPlayedEpochSeconds = meta.lastPlayedEpochSeconds,
                    )
                }
            }

            albumDao.upsertAll(result.albums)
            val albumIds = result.albums.map { it.id }
            if (albumIds.isNotEmpty()) albumDao.deleteMissing(albumIds)

            artistDao.upsertAll(result.artists)
            val artistIds = result.artists.map { it.id }
            if (artistIds.isNotEmpty()) artistDao.deleteMissing(artistIds)
        }
        preferencesDataStore.edit { it[HAS_SCANNED_KEY] = true }
        Unit
    }

    /** Settings → Storage (SCREEN_SETTINGS.md §8). Also zeroes the denormalized
     * play statistics so Recently/Most Played reset along with the raw log.
     * M3: paired writes run atomically. */
    override suspend fun clearPlayHistory() = withContext(ioDispatcher) {
        database.withTransaction {
            playHistoryDao.clearAll()
            mediaItemDao.resetPlayStatistics()
        }
        // Milestone 7: dropping the history invalidates the
        // co-occurrence matrix.
        recommendationEngine.onPlayHistoryChanged()
    }

    override fun hasScannedBefore(): Flow<Boolean> =
        preferencesDataStore.data.map { it[HAS_SCANNED_KEY] ?: false }

    private companion object {
        val HAS_SCANNED_KEY = booleanPreferencesKey("has_scanned_library")

        /** M5: cap for play_history table (enforced after each insert). */
        const val PLAY_HISTORY_KEEP_MOST_RECENT = 500
    }
}

private fun List<MediaItem>.sortedWith(sortOrder: SortOrder): List<MediaItem> = when (sortOrder) {
    SortOrder.TITLE -> sortedBy { it.title.lowercase() }
    SortOrder.DATE_ADDED -> sortedByDescending { it.dateAddedEpochSeconds }
    SortOrder.DURATION -> sortedByDescending { it.durationMs }
    SortOrder.SIZE -> sortedByDescending { it.sizeBytes }
    SortOrder.PATH -> sortedBy { it.folderPath ?: "" }
}

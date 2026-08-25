package com.exapps.velox.core.domain.repository

import com.exapps.velox.core.domain.model.Album
import com.exapps.velox.core.domain.model.Artist
import com.exapps.velox.core.domain.model.Folder
import com.exapps.velox.core.domain.model.Genre
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.model.MediaType
import com.exapps.velox.core.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow

/**
 * `:core:data` provides the real implementation (Room + MediaStore). Everything here
 * returns `Flow` so the UI stays reactive to scanner updates (TECHNICAL_PLAN.md §6.5:
 * "Diff-based updates to avoid full UI reloads") instead of polling.
 */
interface MediaLibraryRepository {

    fun observeTracks(sortOrder: SortOrder = SortOrder.TITLE): Flow<List<MediaItem>>
    fun observeVideos(sortOrder: SortOrder = SortOrder.TITLE): Flow<List<MediaItem>>
    fun observeAlbums(): Flow<List<Album>>
    fun observeArtists(): Flow<List<Artist>>
    fun observeFolders(parentPath: String? = null): Flow<List<Folder>>

    fun observeGenres(): Flow<List<Genre>>

    fun observeBookmarks(mediaItemId: Long): Flow<List<com.exapps.velox.core.domain.model.Bookmark>>

    suspend fun addBookmark(mediaItemId: Long, positionMs: Long, label: String): Long

    suspend fun deleteBookmark(bookmarkId: Long)

    fun observeGenreTracks(genre: String): Flow<List<MediaItem>>
    fun observeFolderContents(path: String): Flow<List<MediaItem>>
    fun observeAlbumTracks(albumId: Long): Flow<List<MediaItem>>
    fun observeArtistTracks(artistId: Long): Flow<List<MediaItem>>
    fun observeFavorites(): Flow<List<MediaItem>>
    fun observeRecentlyPlayed(limit: Int = 50): Flow<List<MediaItem>>
    fun observeMostPlayed(limit: Int = 50): Flow<List<MediaItem>>

    suspend fun getById(id: Long): MediaItem?
    fun search(query: String, type: MediaType? = null): Flow<List<MediaItem>>

    suspend fun setFavorite(id: Long, favorite: Boolean)

    /** Tag editor (Phase 1.1) — library-level override; file tags are not rewritten. */
    suspend fun updateTrackMetadata(id: Long, title: String, artistName: String?, albumTitle: String?)
    suspend fun recordPlayed(id: Long)

    /** Triggers a MediaStore + folder rescan (TECHNICAL_PLAN.md §6.1). Suspends until
     * the initial pass completes; ongoing incremental updates continue to flow through
     * the `observe*` Flows above. */
    suspend fun rescanLibrary()

    /** Settings → Storage: wipes play history + Recently/Most Played statistics. */
    suspend fun clearPlayHistory()

    /** Whether at least one scan has ever completed — drives the onboarding →
     * library handoff and the empty-state vs. loading-state distinction. */
    fun hasScannedBefore(): Flow<Boolean>
}

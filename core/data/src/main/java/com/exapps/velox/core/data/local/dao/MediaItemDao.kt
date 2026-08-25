package com.exapps.velox.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.exapps.velox.core.data.local.entity.GenreProjection
import com.exapps.velox.core.data.local.entity.MediaItemEntity
import com.exapps.velox.core.data.local.entity.UserMetadataProjection
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {

    // Sorting beyond title/date-added is applied in the repository layer on top of
    // these two — see MediaLibraryRepositoryImpl. That keeps this DAO small; a
    // large-library performance pass (thousands of items — ARCHITECTURE.md §7:
    // "60 fps" target) is exactly where pushing every SortOrder into SQL earns
    // its complexity, but Phase 0 doesn't need that yet.

    @Query("SELECT * FROM media_items WHERE mediaType = :type ORDER BY title COLLATE NOCASE ASC")
    fun observeByTypeOrderedByTitle(type: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE folderPath = :path ORDER BY title COLLATE NOCASE ASC")
    fun observeByFolder(path: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE albumId = :albumId ORDER BY title COLLATE NOCASE ASC")
    fun observeByAlbum(albumId: Long): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE artistName = :artistName ORDER BY title COLLATE NOCASE ASC")
    fun observeByArtist(artistName: String): Flow<List<MediaItemEntity>>

    @Query(
        """SELECT genre AS name, COUNT(*) AS trackCount FROM media_items
           WHERE genre IS NOT NULL AND genre != ''
           GROUP BY genre ORDER BY genre COLLATE NOCASE ASC"""
    )
    fun observeGenreSummaries(): Flow<List<GenreProjection>>

    @Query("SELECT * FROM media_items WHERE genre = :genre ORDER BY title COLLATE NOCASE ASC")
    fun observeByGenre(genre: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isFavorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun observeFavorites(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE lastPlayedEpochSeconds IS NOT NULL ORDER BY lastPlayedEpochSeconds DESC LIMIT :limit")
    fun observeRecentlyPlayed(limit: Int): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE playCount > 0 ORDER BY playCount DESC LIMIT :limit")
    fun observeMostPlayed(limit: Int): Flow<List<MediaItemEntity>>

    @Query(
        """SELECT * FROM media_items
           WHERE (:type IS NULL OR mediaType = :type)
             AND (title LIKE '%' || :query || '%'
                  OR artistName LIKE '%' || :query || '%'
                  OR albumTitle LIKE '%' || :query || '%')
           ORDER BY title COLLATE NOCASE ASC""",
    )
    fun search(query: String, type: String?): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: Long): MediaItemEntity?

    @Query("SELECT DISTINCT folderPath FROM media_items WHERE folderPath IS NOT NULL")
    fun observeDistinctFolderPaths(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM media_items WHERE folderPath = :path")
    suspend fun countInFolder(path: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MediaItemEntity>)

    /** Removes library rows for files the scanner no longer finds (deleted/moved). */
    @Query("DELETE FROM media_items WHERE id NOT IN (:currentIds)")
    suspend fun deleteMissing(currentIds: List<Long>)

    // --- rescan user-data preservation -------------------------------------------------
    // upsertAll uses OnConflictStrategy.REPLACE, which rewrites the whole row from the
    // freshly scanned entity and would silently wipe everything the user owns —
    // favourites, play statistics, AND tag-editor edits — on every rescan (one runs at
    // every app launch). Snapshot all of it before the upsert, re-apply row-by-row
    // afterwards; UPDATEs against rows deleted since the snapshot affect zero rows.

    @Query(
        """SELECT id, title, artistName, albumTitle, isFavorite, playCount, lastPlayedEpochSeconds
           FROM media_items
           WHERE isFavorite = 1 OR playCount > 0 OR lastPlayedEpochSeconds IS NOT NULL"""
    )
    suspend fun getUserMetadataSnapshot(): List<UserMetadataProjection>

    @Query(
        """UPDATE media_items SET title = :title, artistName = :artistName, albumTitle = :albumTitle,
           isFavorite = :isFavorite, playCount = :playCount, lastPlayedEpochSeconds = :lastPlayedEpochSeconds
           WHERE id = :id"""
    )
    suspend fun restoreUserMetadata(
        id: Long,
        title: String,
        artistName: String?,
        albumTitle: String?,
        isFavorite: Boolean,
        playCount: Int,
        lastPlayedEpochSeconds: Long?,
    )

    /** Tag editor (Phase 1.1): in-library metadata override. File tags themselves are
     * not rewritten; edits survive rescans via the snapshot/restore pair above. */
    @Query("UPDATE media_items SET title = :title, artistName = :artistName, albumTitle = :albumTitle WHERE id = :id")
    suspend fun updateTrackMetadata(id: Long, title: String, artistName: String?, albumTitle: String?)

    @Query("UPDATE media_items SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE media_items SET playCount = playCount + 1, lastPlayedEpochSeconds = :playedAt WHERE id = :id")
    suspend fun recordPlayed(id: Long, playedAt: Long)

    /** Pairs with PlayHistoryDao.clearAll() for Settings → Storage → clear history. */
    @Query("UPDATE media_items SET playCount = 0, lastPlayedEpochSeconds = NULL")
    suspend fun resetPlayStatistics()

    @Query("SELECT COUNT(*) FROM media_items")
    suspend fun count(): Int
}

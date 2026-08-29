package com.exapps.velox.core.data.repository

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.exapps.velox.core.common.di.IoDispatcher
import com.exapps.velox.core.data.local.dao.MediaItemDao
import com.exapps.velox.core.data.local.dao.PlaylistDao
import com.exapps.velox.core.data.local.entity.PlaylistEntity
import com.exapps.velox.core.data.local.mapper.toDomain
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.model.Playlist
import com.exapps.velox.core.domain.model.PlaylistDetail
import com.exapps.velox.core.domain.model.PlaylistType
import com.exapps.velox.core.domain.repository.PlaylistRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao,
    private val mediaItemDao: MediaItemDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PlaylistRepository {

    /**
     * M13 (data-layer review): the read-then-write in
     * [PlaylistDao.addTracksAtEnd] was safe inside a single transaction but
     * two concurrent calls (e.g. AddTracksSheet submitted twice while a
     * backup-restore was also writing) would both observe the same
     * MAX(position) and the second INSERT would violate the
     * (playlistId, position) primary key. Per-playlist Mutexes serialise all
     * write operations on the same playlist, so the read+write pair is atomic
     * from the caller's point of view.
     *
     * Concurrent writes to *different* playlists are unaffected, and a
     * read-while-write just sees a slightly stale state for the duration of
     * the lock.
     */
    private val writeLocks = mutableMapOf<Long, Mutex>()
    private val locksLock = Mutex()

    private suspend fun playlistWriteLock(playlistId: Long): Mutex = locksLock.withLock {
        writeLocks.getOrPut(playlistId) { Mutex() }
    }

    /** SCREEN_PLAYLISTS.md §3: user playlists plus the always-present system playlists.
     * System playlists use small negative ids (never collide with Room's
     * autoGenerate ids, which start at 1) so the UI can route to them without a
     * separate "is this a system playlist" model. */
    override fun observePlaylists(): Flow<List<Playlist>> {
        val userPlaylistsFlow = playlistDao.observeAll().flatMapLatest { entities ->
            if (entities.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(entities.map { entity -> playlistFlow(entity) }) { it.toList() }
            }
        }
        val systemPlaylistsFlow = combine(
            mediaItemDao.observeFavorites(),
            mediaItemDao.observeRecentlyPlayed(SYSTEM_PLAYLIST_PREVIEW_LIMIT),
            mediaItemDao.observeMostPlayed(SYSTEM_PLAYLIST_PREVIEW_LIMIT),
        ) { favorites, recent, mostPlayed ->
            listOf(
                systemPlaylist(SYSTEM_ID_FAVORITES, PlaylistType.FAVORITES, favorites.size),
                systemPlaylist(SYSTEM_ID_RECENTLY_PLAYED, PlaylistType.RECENTLY_PLAYED, recent.size),
                systemPlaylist(SYSTEM_ID_MOST_PLAYED, PlaylistType.MOST_PLAYED, mostPlayed.size),
            )
        }
        return combine(systemPlaylistsFlow, userPlaylistsFlow) { system, user -> system + user }
    }

    private fun systemPlaylist(id: Long, type: PlaylistType, trackCount: Int) = Playlist(
        id = id,
        name = "", // display name is a localized string resource — resolved in the UI layer from `type`
        type = type,
        trackCount = trackCount,
        totalDurationMs = 0L,
    )

    private fun playlistFlow(entity: PlaylistEntity): Flow<Playlist> =
        playlistDao.observeItems(entity.id).map { items ->
            Playlist(
                id = entity.id,
                name = entity.name,
                type = PlaylistType.USER,
                trackCount = items.size,
                totalDurationMs = 0L,
                artworkUris = emptyList(),
                createdAtEpochSeconds = entity.createdAtEpochSeconds,
            )
        }

    override fun observePlaylistDetail(playlistId: Long): Flow<PlaylistDetail> = when (playlistId) {
        SYSTEM_ID_FAVORITES -> mediaItemDao.observeFavorites().map { it.toSystemDetail(PlaylistType.FAVORITES) }
        SYSTEM_ID_RECENTLY_PLAYED -> mediaItemDao.observeRecentlyPlayed(SYSTEM_PLAYLIST_PREVIEW_LIMIT)
            .map { it.toSystemDetail(PlaylistType.RECENTLY_PLAYED) }
        SYSTEM_ID_MOST_PLAYED -> mediaItemDao.observeMostPlayed(SYSTEM_PLAYLIST_PREVIEW_LIMIT)
            .map { it.toSystemDetail(PlaylistType.MOST_PLAYED) }
        else -> observeUserPlaylistDetail(playlistId)
    }

    /** System playlists have no stored track list — their contents are the live
     * query the playlist is named after, mapped into the same detail shape. */
    private fun List<com.exapps.velox.core.data.local.entity.MediaItemEntity>.toSystemDetail(
        type: PlaylistType,
    ) = PlaylistDetail(
        playlist = Playlist(
            id = 0L,
            name = "",
            type = type,
            trackCount = size,
            totalDurationMs = sumOf { it.durationMs },
        ),
        tracks = map { it.toDomain() },
    )

    private fun observeUserPlaylistDetail(playlistId: Long): Flow<PlaylistDetail> =
        combine(playlistDao.observeById(playlistId), playlistDao.observeItems(playlistId)) { entity, items ->
            entity to items
        }.flatMapLatest { (entity, items) ->
            if (items.isEmpty()) {
                flowOf(
                    PlaylistDetail(
                        playlist = Playlist(
                            playlistId,
                            entity?.name ?: "",
                            PlaylistType.USER,
                            0,
                            0L,
                        ),
                        tracks = emptyList(),
                    ),
                )
            } else {
                combine(items.map { item -> mediaItemFlow(item.mediaItemId) }) { tracks ->
                    val resolvedTracks = tracks.filterNotNull()
                    PlaylistDetail(
                        playlist = Playlist(
                            id = playlistId,
                            name = entity?.name ?: "",
                            type = PlaylistType.USER,
                            trackCount = resolvedTracks.size,
                            totalDurationMs = resolvedTracks.sumOf { it.durationMs },
                        ),
                        tracks = resolvedTracks,
                    )
                }
            }
        }

    /** M12 (data-layer review): emit from a live Room query so favourite toggles /
     * tag edits / rescans refresh already-open playlist details instead of going
     * stale until the item list itself mutates. */
    private fun mediaItemFlow(mediaItemId: Long): Flow<MediaItem?> =
        mediaItemDao.observeById(mediaItemId).map { it?.toDomain() }

    override suspend fun createPlaylist(name: String): Long = withContext(ioDispatcher) {
        playlistDao.insert(PlaylistEntity(name = name, createdAtEpochSeconds = System.currentTimeMillis() / 1000))
    }

    override suspend fun renamePlaylist(playlistId: Long, name: String) =
        withContext(ioDispatcher) { playlistDao.rename(playlistId, name) }

    override suspend fun deletePlaylist(playlistId: Long) =
        withContext(ioDispatcher) { playlistDao.delete(playlistId) }

    override suspend fun addTracks(playlistId: Long, mediaItemIds: List<Long>) =
        withContext(ioDispatcher) {
            playlistWriteLock(playlistId).withLock {
                playlistDao.addTracksAtEnd(playlistId, mediaItemIds)
            }
        }

    override suspend fun removeTrack(playlistId: Long, mediaItemId: Long) =
        withContext(ioDispatcher) {
            playlistWriteLock(playlistId).withLock {
                playlistDao.removeItem(playlistId, mediaItemId)
            }
        }

    override suspend fun reorderTrack(playlistId: Long, fromPosition: Int, toPosition: Int) =
        withContext(ioDispatcher) {
            playlistWriteLock(playlistId).withLock {
                playlistDao.reorder(playlistId, fromPosition, toPosition)
            }
        }

    /**
     * FEATURES.md §3 "Import / Export M3U / M3U8". Exports an extended M3U with
     * EXTINF title/artist metadata and content URIs, written straight to the SAF
     * destination the user picked (so no storage permission is needed).
     */
    override suspend fun exportM3u(playlistId: Long, destinationPath: String): String =
        withContext(ioDispatcher) {
            val items = playlistDao.observeItemsSnapshot(playlistId)
                .mapNotNull { mediaItemDao.getById(it.mediaItemId)?.toDomain() }
            val builder = StringBuilder("#EXTM3U\n")
            items.forEach { track ->
                // Low nit (data-layer review): strip newlines from metadata so a
                // crafted tag can't inject fake EXTINF/URL lines into the export.
                val safeArtist = (track.artistName ?: "").replace(NEWLINES, " ")
                val safeTitle = track.title.replace(NEWLINES, " ")
                builder.append("#EXTINF:${track.durationMs / 1000},$safeArtist - $safeTitle\n")
                builder.append(track.uri).append('\n')
            }
            context.contentResolver.openOutputStream(Uri.parse(destinationPath))?.use { stream ->
                stream.write(builder.toString().toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("Could not open $destinationPath for writing")
            destinationPath
        }

    /**
     * Reads an M3U/M3U8 picked via SAF. Each entry is a content:// URI (as exported
     * above) or a plain filesystem path; paths are resolved against the MediaStore
     * index via the library database, and anything unmatched is skipped rather
     * than aborting the whole import.
     *
     * data-layer (review): the previous version always created the playlist,
     * even if the stream could not be opened (`.orEmpty()` masked the
     * failure) or every line failed to resolve, leaving the user with an
     * empty playlist. We now create the playlist last, only when at
     * least one track resolved, and surface a typed error otherwise.
     */
    override suspend fun importM3u(sourcePath: String, playlistName: String): Long =
        withContext(ioDispatcher) {
            val entries = readM3uEntries(sourcePath)
            if (entries.isEmpty()) {
                throw java.io.IOException(
                    "M3U import produced no resolvable entries (file unreadable or all paths unknown to MediaStore)",
                )
            }
            val playlistId = createPlaylist(playlistName)
            playlistDao.addTracksAtEnd(playlistId, entries)
            playlistId
        }

    /**
     * Parse the M3U at [sourcePath] into a list of resolved media-item ids.
     * Returns an empty list (without throwing) if the file is empty or every
     * entry fails to resolve — `importM3u` then surfaces a friendly error
     * and refuses to create the placeholder playlist.
     */
    private suspend fun readM3uEntries(sourcePath: String): List<Long> {
        val stream = context.contentResolver.openInputStream(Uri.parse(sourcePath))
            ?: return emptyList()
        val lines = stream.use { it.bufferedReader(Charsets.UTF_8).readLines() }
        return lines.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { entry -> resolveToMediaItemId(entry) }
            .toList()
    }

    private fun resolveToMediaItemId(entry: String): Long? {
        if (entry.startsWith("content://")) {
            // Entries we exported are content URIs whose last segment is the item id.
            return Uri.parse(entry).lastPathSegment?.toLongOrNull()
        }
        // Plain filesystem paths (from other apps' playlists) resolve through
        // MediaStore's DATA index rather than a stored column — the library schema
        // deliberately doesn't persist raw paths.
        //
        // data-layer (review): on API 29+ MediaColumns.DATA is deprecated
        // and on some OEM ROMs returns null. We first try the exact DATA
        // match (works on API ≤28 and on most devices for backwards-compat
        // reasons), then fall back to a basename + parent-path match
        // using the `MediaStore.Files` collection with a RELATIVE_PATH
        // filter. The fallback picks the first match — collisions are
        // possible if two files in the library have identical basenames
        // and parent paths, but that's a rare M3U-import edge case.
        val collections = listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        for (collection in collections) {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DATA} = ?",
                arrayOf(entry),
                null,
            )?.use { cursor -> if (cursor.moveToFirst()) return cursor.getLong(0) }
        }
        // Fallback for API 29+ where DATA is unreliable: build a
        // (RELATIVE_PATH, DISPLAY_NAME) pair from the path and query the
        // MediaStore Files table which still exposes those columns.
        val fileName = entry.substringAfterLast('/')
        if (fileName.isNotBlank()) {
            val parentRelative = entry.substringBeforeLast('/').trimStart('/')
            // RELATIVE_PATH always ends with a trailing slash.
            val relPath = if (parentRelative.isEmpty()) "" else "$parentRelative/"
            for (collection in collections) {
                context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                    arrayOf(fileName, relPath),
                    null,
                )?.use { cursor -> if (cursor.moveToFirst()) return cursor.getLong(0) }
            }
        }
        return null
    }

    private companion object {
        const val SYSTEM_ID_FAVORITES = -1L
        const val SYSTEM_ID_RECENTLY_PLAYED = -2L
        const val SYSTEM_ID_MOST_PLAYED = -3L
        const val SYSTEM_PLAYLIST_PREVIEW_LIMIT = 500
    }
}

private val NEWLINES = Regex("[\r\n]+")

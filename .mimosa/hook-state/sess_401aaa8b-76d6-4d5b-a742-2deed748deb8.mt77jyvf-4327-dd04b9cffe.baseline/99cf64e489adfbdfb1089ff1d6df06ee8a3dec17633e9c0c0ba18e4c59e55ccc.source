package com.exapps.velox.core.domain.repository

import com.exapps.velox.core.domain.model.Playlist
import com.exapps.velox.core.domain.model.PlaylistDetail
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {

    /** User playlists + the always-present system playlists (Favorites, Recently
     * Played, Most Played, Recently Added — SCREEN_PLAYLISTS.md §3). */
    fun observePlaylists(): Flow<List<Playlist>>
    fun observePlaylistDetail(playlistId: Long): Flow<PlaylistDetail>

    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(playlistId: Long, name: String)
    suspend fun deletePlaylist(playlistId: Long)

    suspend fun addTracks(playlistId: Long, mediaItemIds: List<Long>)
    suspend fun removeTrack(playlistId: Long, mediaItemId: Long)
    suspend fun reorderTrack(playlistId: Long, fromPosition: Int, toPosition: Int)

    /** FEATURES.md §3: "Import / Export M3U / M3U8". Returns the created file's path. */
    suspend fun exportM3u(playlistId: Long, destinationPath: String): String
    suspend fun importM3u(sourcePath: String, playlistName: String): Long
}

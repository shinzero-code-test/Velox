package com.exapps.velox.core.domain.model

/** SCREEN_PLAYLISTS.md §3 — user playlists are editable; the rest are system-derived
 * and read-only (their contents come from query logic, not a stored track list). */
enum class PlaylistType { USER, FAVORITES, RECENTLY_PLAYED, MOST_PLAYED, RECENTLY_ADDED }

data class Playlist(
    val id: Long,
    val name: String,
    val type: PlaylistType,
    val trackCount: Int,
    val totalDurationMs: Long,
    /** First few tracks' artwork, for the collage cover (SCREEN_PLAYLISTS.md §4). */
    val artworkUris: List<String> = emptyList(),
    val createdAtEpochSeconds: Long = 0L,
)

data class PlaylistItem(
    val playlistId: Long,
    val mediaItemId: Long,
    val position: Int,
)

/** A playlist plus its resolved tracks, for the detail screen (SCREEN_PLAYLISTS.md §6). */
data class PlaylistDetail(
    val playlist: Playlist,
    val tracks: List<MediaItem>,
)

package com.exapps.velox.core.domain.model

/** FEATURES.md §1: Velox plays both local audio and video; almost everything in the
 * library and player layers branches on this. */
enum class MediaType { AUDIO, VIDEO }

/**
 * A single playable file — a track or a video. This is the domain-layer shape;
 * `:core:data` maps its Room `MediaItemEntity` to/from this, and `:player:engine`
 * builds a Media3 `MediaItem` from it when handed off to ExoPlayer.
 */
data class MediaItem(
    val id: Long,
    val uri: String,
    val title: String,
    val mediaType: MediaType,
    val durationMs: Long,
    val artistName: String? = null,
    val albumId: Long? = null,
    val albumTitle: String? = null,
    val artworkUri: String? = null,
    val folderPath: String? = null,
    val dateAddedEpochSeconds: Long = 0L,
    val sizeBytes: Long = 0L,
    val isFavorite: Boolean = false,
    /** FEATURES.md §2: "Recently played & Most played". */
    val playCount: Int = 0,
    val lastPlayedEpochSeconds: Long? = null,
)

data class Album(
    val id: Long,
    val title: String,
    val artistName: String?,
    val artworkUri: String?,
    val trackCount: Int,
    val year: Int? = null,
    val totalDurationMs: Long = 0L,
)

data class Artist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val artworkUri: String? = null,
)

/** SCREEN_HOME_LIBRARY.md §5.3: folders are browsed hierarchically, with an item
 * count and (later) a collage of the first few tracks' artwork. */
data class Folder(
    val path: String,
    val displayName: String,
    val itemCount: Int,
    val parentPath: String?,
)

/** SCREEN_HOME_LIBRARY.md §4: the top-level library groupings / tabs. */
enum class LibraryGroup { FOLDERS, ARTISTS, ALBUMS, TRACKS, VIDEOS, GENRES, RECENT }

enum class SortOrder { TITLE, DATE_ADDED, DURATION, SIZE, PATH }

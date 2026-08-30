package com.exapps.velox.feature.library

import com.exapps.velox.core.domain.model.Album
import com.exapps.velox.core.domain.model.Artist
import com.exapps.velox.core.domain.model.Folder
import com.exapps.velox.core.domain.model.Genre
import com.exapps.velox.core.domain.repository.MediaLibraryRepository

/**
 * Phase 3 / Milestone 3 completion — Better tablet layouts. Identifies
 * one Library collection (album, artist, folder, or genre) for the
 * in-place list-detail pane. Each variant carries the minimum
 * information needed to look up the tracks via
 * [MediaLibraryRepository] and to display a header.
 */
sealed class CollectionKey {
    abstract val title: String

    data class AlbumKey(val albumId: Long, val title: String) : CollectionKey() {
        companion object {
            fun from(album: Album): AlbumKey = AlbumKey(album.id, album.title)
        }
    }

    data class ArtistKey(val artistId: Long, val title: String) : CollectionKey() {
        companion object {
            fun from(artist: Artist): ArtistKey = ArtistKey(artist.id, artist.name)
        }
    }

    data class FolderKey(val folderPath: String, val title: String) : CollectionKey() {
        companion object {
            fun from(folder: Folder): FolderKey =
                FolderKey(folder.path, folder.displayName)
        }
    }

    data class GenreKey(val genre: String, val title: String) : CollectionKey() {
        companion object {
            fun from(genre: Genre): GenreKey = GenreKey(genre.name, genre.name)
        }
    }
}

/**
 * Resolve a [CollectionKey] to the underlying tracks flow on the
 * repository. Centralises the four-way switch in one place so the
 * Library and Playlists list-detail panes don't duplicate it.
 */
fun MediaLibraryRepository.observeCollection(
    key: CollectionKey,
): kotlinx.coroutines.flow.Flow<List<com.exapps.velox.core.domain.model.MediaItem>> =
    when (key) {
        is CollectionKey.AlbumKey -> observeAlbumTracks(key.albumId)
        is CollectionKey.ArtistKey -> observeArtistTracks(key.artistId)
        is CollectionKey.FolderKey -> observeFolderContents(key.folderPath)
        is CollectionKey.GenreKey -> observeGenreTracks(key.genre)
    }

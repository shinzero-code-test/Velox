package com.exapps.velox.feature.library

import com.exapps.velox.core.domain.model.Album
import com.exapps.velox.core.domain.model.Artist
import com.exapps.velox.core.domain.model.Folder
import com.exapps.velox.core.domain.model.Genre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 3 / Milestone 3 completion — Better tablet layouts. Pure
 * data tests for [CollectionKey] and its Compose Saver
 * ([CollectionKeySaver]). The Saver is a small string-encoded
 * format; round-tripping it through `save` / `restore` is the
 * critical contract that keeps the two-pane selection alive across
 * a configuration change.
 */
class CollectionKeyTest {

    @Test
    fun `AlbumKey from domain Album carries id and title`() {
        val album = Album(
            id = 42L,
            title = "The Wall",
            artistName = "Pink Floyd",
            artworkUri = null,
            trackCount = 26,
        )
        val key = CollectionKey.AlbumKey.from(album)
        assertEquals(42L, key.albumId)
        assertEquals("The Wall", key.title)
    }

    @Test
    fun `ArtistKey from domain Artist carries id and name`() {
        val artist = Artist(
            id = 7L,
            name = "Pink Floyd",
            trackCount = 13,
            albumCount = 15,
        )
        val key = CollectionKey.ArtistKey.from(artist)
        assertEquals(7L, key.artistId)
        assertEquals("Pink Floyd", key.title)
    }

    @Test
    fun `FolderKey from domain Folder carries path and display name`() {
        val folder = Folder(
            path = "/storage/emulated/0/Music/Pink Floyd",
            displayName = "Pink Floyd",
            itemCount = 13,
            parentPath = "/storage/emulated/0/Music",
        )
        val key = CollectionKey.FolderKey.from(folder)
        assertEquals("/storage/emulated/0/Music/Pink Floyd", key.folderPath)
        assertEquals("Pink Floyd", key.title)
    }

    @Test
    fun `GenreKey from domain Genre uses name as both fields`() {
        val genre = Genre(name = "Rock", trackCount = 99)
        val key = CollectionKey.GenreKey.from(genre)
        assertEquals("Rock", key.genre)
        assertEquals("Rock", key.title)
    }

    @Test
    fun `Saver round-trips an AlbumKey`() {
        val key: CollectionKey = CollectionKey.AlbumKey(albumId = 99L, title = "Wish You Were Here")
        val saved = CollectionKeySaver.save(key)
        assertNotNull(saved)
        val restored = CollectionKeySaver.restore(saved!!)
        assertEquals(key, restored)
    }

    @Test
    fun `Saver round-trips an ArtistKey`() {
        val key: CollectionKey = CollectionKey.ArtistKey(artistId = 12L, title = "Daft Punk")
        val restored = CollectionKeySaver.restore(CollectionKeySaver.save(key)!!)
        assertEquals(key, restored)
    }

    @Test
    fun `Saver round-trips a FolderKey with slash in path`() {
        val key: CollectionKey = CollectionKey.FolderKey(
            folderPath = "/storage/emulated/0/Music/Some/Deep/Path",
            title = "Deep",
        )
        val restored = CollectionKeySaver.restore(CollectionKeySaver.save(key)!!)
        assertEquals(key, restored)
    }

    @Test
    fun `Saver round-trips a GenreKey`() {
        val key: CollectionKey = CollectionKey.GenreKey(genre = "Hip-Hop", title = "Hip-Hop")
        val restored = CollectionKeySaver.restore(CollectionKeySaver.save(key)!!)
        assertEquals(key, restored)
    }

    @Test
    fun `Saver encodes null as empty string and restores to null`() {
        assertEquals("", CollectionKeySaver.save(null))
        assertNull(CollectionKeySaver.restore(""))
    }
}

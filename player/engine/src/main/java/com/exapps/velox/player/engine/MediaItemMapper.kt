package com.exapps.velox.player.engine

import android.net.Uri
import androidx.media3.common.MediaItem as Media3MediaItem
import androidx.media3.common.MediaMetadata
import com.exapps.velox.core.domain.model.MediaItem as VeloxMediaItem

/**
 * The only place `androidx.media3.common.MediaItem` and Velox's own domain
 * `MediaItem` are allowed to touch — everywhere else in the app should be dealing
 * with one or the other, never both (see the seam note on `PlayerController`).
 */
fun VeloxMediaItem.toMedia3MediaItem(): Media3MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artistName)
        .setAlbumTitle(albumTitle)
        // L12 (player-stack review): `artworkUri` is stored as a String
        // (null/blank allowed). Uri.parse("") produces a relative-uri
        // with a misleading empty authority; skip empty values entirely
        // so Media3 falls back to its default art placeholder.
        .apply {
            artworkUri
                ?.takeIf { it.isNotBlank() }
                ?.let { setArtworkUri(Uri.parse(it)) }
        }
        .setDurationMs(durationMs)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .build()

    return Media3MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id.toString())
        .setMediaMetadata(metadata)
        .build()
}

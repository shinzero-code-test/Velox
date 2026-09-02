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

    // Video crash hardening (v1.10): content:// URIs from MediaStore have no
    // file extension, so DefaultMediaSourceFactory cannot sniff the container
    // (e.g. video/mp4 vs audio/mpeg) and may throw UnrecognizedInputFormatException
    // which surfaces as STATE_IDLE / stale UI. Guess MIME from fileName or uri
    // extension and set it explicitly so the correct extractor is chosen even for
    // extension-less content URIs. Mirrors core:network's guessMimeType but lives
    // in the engine to keep the :core:network → :player:engine edge clean.
    val guessedMime = guessMimeType(fileName ?: uri)

    return Media3MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id.toString())
        .setMediaMetadata(metadata)
        .apply { guessedMime?.let { setMimeType(it) } }
        .build()
}

private fun guessMimeType(name: String?): String? {
    if (name.isNullOrBlank()) return null
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp4", "m4v", "mov" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "ts", "m2ts" -> "video/mp2t"
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4a-latm"
        "aac" -> "audio/aac"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        else -> null
    }
}

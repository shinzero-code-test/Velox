package com.exapps.velox.core.data.local.mapper

import com.exapps.velox.core.data.local.entity.AlbumEntity
import com.exapps.velox.core.data.local.entity.ArtistEntity
import com.exapps.velox.core.data.local.entity.MediaItemEntity
import com.exapps.velox.core.domain.model.Album
import com.exapps.velox.core.domain.model.Artist
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.model.MediaType

fun MediaItemEntity.toDomain(): MediaItem = MediaItem(
    id = id,
    uri = uri,
    title = title,
    mediaType = MediaType.valueOf(mediaType),
    durationMs = durationMs,
    artistName = artistName,
    albumId = albumId,
    albumTitle = albumTitle,
    artworkUri = artworkUri,
    folderPath = folderPath,
    fileName = fileName,
    genre = genre,
    dateAddedEpochSeconds = dateAddedEpochSeconds,
    sizeBytes = sizeBytes,
    isFavorite = isFavorite,
    playCount = playCount,
    lastPlayedEpochSeconds = lastPlayedEpochSeconds,
)

fun MediaItem.toEntity(): MediaItemEntity = MediaItemEntity(
    id = id,
    uri = uri,
    title = title,
    mediaType = mediaType.name,
    durationMs = durationMs,
    artistName = artistName,
    albumId = albumId,
    albumTitle = albumTitle,
    artworkUri = artworkUri,
    folderPath = folderPath,
    fileName = fileName,
    genre = genre,
    dateAddedEpochSeconds = dateAddedEpochSeconds,
    sizeBytes = sizeBytes,
    isFavorite = isFavorite,
    playCount = playCount,
    lastPlayedEpochSeconds = lastPlayedEpochSeconds,
)

fun AlbumEntity.toDomain(): Album = Album(
    id = id,
    title = title,
    artistName = artistName,
    artworkUri = artworkUri,
    trackCount = trackCount,
    year = year,
    totalDurationMs = totalDurationMs,
)

fun ArtistEntity.toDomain(): Artist = Artist(
    id = id,
    name = name,
    trackCount = trackCount,
    albumCount = albumCount,
    artworkUri = artworkUri,
)

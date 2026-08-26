package com.exapps.velox.core.data.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.exapps.velox.core.common.di.IoDispatcher
import com.exapps.velox.core.data.local.entity.AlbumEntity
import com.exapps.velox.core.data.local.entity.ArtistEntity
import com.exapps.velox.core.data.local.entity.MediaItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ScanResult(
    val mediaItems: List<MediaItemEntity>,
    val albums: List<AlbumEntity>,
    val artists: List<ArtistEntity>,
)

/**
 * TECHNICAL_PLAN.md §6.1 / master prompt Phase 0 item 7: "Basic media scanner
 * (MediaStore + optional folder)". This is the MediaStore half — it reads the
 * platform's own audio/video index rather than walking the filesystem by hand,
 * which is both faster and matches what the Photos/Files apps already see. A
 * dedicated folder-walk pass for paths MediaStore hasn't indexed yet is deferred
 * past Phase 0 — see PROGRESS.md's known-limitations list.
 *
 * This class only *reads* MediaStore and returns plain entities; writing them
 * into Room is the repository's job, which keeps this class trivially testable
 * with a fake ContentResolver and no database involved.
 */
@Singleton
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun scan(): ScanResult = withContext(ioDispatcher) {
        val audioItems = scanAudio()
        val videoItems = scanVideo()
        val allItems = audioItems + videoItems

        val albums = audioItems
            .filter { it.albumId != null }
            .groupBy { it.albumId }
            .map { (albumId, tracks) ->
                val first = tracks.first()
                AlbumEntity(
                    id = albumId!!,
                    title = first.albumTitle ?: "Unknown Album",
                    artistName = first.artistName,
                    artworkUri = first.artworkUri,
                    trackCount = tracks.size,
                    year = null,
                    totalDurationMs = tracks.sumOf { it.durationMs },
                )
            }

        val artists = audioItems
            .filter { !it.artistName.isNullOrBlank() }
            .groupBy { it.artistName }
            .map { (name, tracks) ->
                ArtistEntity(
                    id = name.hashCode().toLong(),
                    name = name!!,
                    trackCount = tracks.size,
                    albumCount = tracks.mapNotNull { it.albumId }.distinct().size,
                    artworkUri = tracks.firstOrNull { it.artworkUri != null }?.artworkUri,
                )
            }

        ScanResult(mediaItems = allItems, albums = albums, artists = artists)
    }

    private fun scanAudio(): List<MediaItemEntity> {
        val items = mutableListOf<MediaItemEntity>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.GENRE,
        )
        // IS_MUSIC filters out notification tones / ringtones MediaStore also indexes.
        val baseSelection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        // Low nit (data-layer review): exclude pending/trashed on API 29+.
        val selection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            "$baseSelection AND (${MediaStore.MediaColumns.IS_PENDING} = 0) AND (${MediaStore.MediaColumns.IS_TRASHED} = 0)"
        } else {
            baseSelection
        }

        context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            // GENRE became a first-class column on the audio table in API 30; on
            // older devices getColumnIndex returns -1 and the helper yields null —
            // the Genres tab is simply empty there rather than crashing.
            val genreCol = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val path = cursor.getStringOrNull(dataCol)
                items += MediaItemEntity(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    title = cursor.getStringOrNull(titleCol) ?: path?.substringAfterLast('/') ?: "Unknown",
                    mediaType = "AUDIO",
                    durationMs = cursor.getLong(durationCol),
                    artistName = cursor.getStringOrNull(artistCol),
                    albumId = albumId.takeIf { it != 0L },
                    albumTitle = cursor.getStringOrNull(albumCol),
                    // Historical but still broadly functional album-art URI pattern; a
                    // per-item contentResolver.loadThumbnail() pass (API 29+) is the
                    // more "correct" replacement and is noted as a Phase 1 follow-up.
                    artworkUri = albumId.takeIf { it != 0L }
                        ?.let { ContentUris.withAppendedId(ALBUM_ART_URI, it).toString() },
                    folderPath = path?.let { File(it).parent },
                    fileName = path?.substringAfterLast('/'),
                    genre = if (genreCol >= 0) cursor.getStringOrNull(genreCol) else null,
                    dateAddedEpochSeconds = cursor.getLong(dateAddedCol),
                    sizeBytes = cursor.getLong(sizeCol),
                )
            }
        }
        return items
    }

    private fun scanVideo(): List<MediaItemEntity> {
        val items = mutableListOf<MediaItemEntity>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
        )

        context.contentResolver.query(collection, projection, pendingTrashSelection(), null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val path = cursor.getStringOrNull(dataCol)
                items += MediaItemEntity(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    title = cursor.getStringOrNull(titleCol) ?: path?.substringAfterLast('/') ?: "Unknown",
                    mediaType = "VIDEO",
                    durationMs = cursor.getLong(durationCol),
                    artistName = null,
                    albumId = null,
                    albumTitle = null,
                    artworkUri = null, // Phase 1: MediaStore.Video.Thumbnails / loadThumbnail()
                    folderPath = path?.let { File(it).parent },
                    fileName = path?.substringAfterLast('/'),
                    dateAddedEpochSeconds = cursor.getLong(dateAddedCol),
                    sizeBytes = cursor.getLong(sizeCol),
                )
            }
        }
        return items
    }

    private fun android.database.Cursor.getStringOrNull(columnIndex: Int): String? =
        if (isNull(columnIndex)) null else getString(columnIndex)

    companion object {
        private val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}

/**
 * Low nit (data-layer review): exclude not-yet-committed camera captures and
 * trashed items (API 29+ columns). On older devices the extra predicate is
 * simply absent — those OS versions never set the flags in the first place.
 */
private fun pendingTrashSelection(): String? =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        "(" + MediaStore.MediaColumns.IS_PENDING + " = 0)" +
            " AND (" + MediaStore.MediaColumns.IS_TRASHED + " = 0)"
    } else {
        null
    }

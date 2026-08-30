package com.exapps.velox.core.data.backup

import android.content.Context
import com.exapps.velox.core.data.local.dao.BookmarkDao
import com.exapps.velox.core.data.local.dao.MediaItemDao
import com.exapps.velox.core.data.local.dao.PlayHistoryDao
import androidx.room.withTransaction
import com.exapps.velox.core.data.local.VeloxDatabase
import com.exapps.velox.core.data.local.dao.PlaylistDao
import com.exapps.velox.core.common.di.IoDispatcher
import com.exapps.velox.core.data.preferences.AppLanguage
import com.exapps.velox.core.data.preferences.UserSettingsPreferences
import com.exapps.velox.core.network.model.NetworkProtocol
import com.exapps.velox.core.network.model.NetworkServer
import com.exapps.velox.core.network.repo.NetworkLibraryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2 "Backup / restore": one JSON document covering everything user-owned —
 * settings, playlists (by media id), favourites, play history, bookmarks, network
 * servers and recent streams. Transport is SAF: Settings writes to / reads from a
 * user-chosen file, so no storage permissions are involved.
 *
 * Restore semantics are merge-style for library-linked data (playlists recreated by
 * name; favourites/history/bookmarks re-applied only when the referenced track still
 * exists in the library) and overwrite-style for pure preferences.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    private val database: VeloxDatabase,
    private val userSettingsPreferences: UserSettingsPreferences,
    private val playlistDao: PlaylistDao,
    private val mediaItemDao: MediaItemDao,
    private val playHistoryDao: PlayHistoryDao,
    private val bookmarkDao: BookmarkDao,
    private val networkRepository: NetworkLibraryRepository,
) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    // ---- Payload model ------------------------------------------------------------

    @Serializable
    data class PlaylistPayload(val name: String, val mediaItemIds: List<Long>)

    @Serializable
    data class BookmarkPayload(
        val mediaItemId: Long,
        val positionMs: Long,
        val label: String,
        val createdAtEpochSeconds: Long,
    )

    @Serializable
    data class ServerPayload(
        val name: String,
        val protocol: String,
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val basePath: String,
        val secure: Boolean = false,
    )

    @Serializable
    data class SettingsPayload(
        val language: String,
        val amoled: Boolean,
        val accentIndex: Int,
        val seekIncrementSeconds: Int,
        val autoPipOnLeave: Boolean,
        val resumePlayback: Boolean,
        val subtitleScalePercent: Int,
        val subtitlePositionBottom: Boolean,
        val autoLoadExternalSubtitles: Boolean,
        val intelligentSilenceEnabled: Boolean = true,
        val autoChapterGenerationEnabled: Boolean = false,
        val decoderPreference: String,
        val gestureLongPressSpeedBoost: Boolean,
        val gestureHorizontalSeekDrag: Boolean,
        val gestureVerticalDragMapping: String,
    )

    @Serializable
    data class BackupPayload(
        val formatVersion: Int = FORMAT_VERSION,
        val exportedAtEpochMs: Long,
        val settings: SettingsPayload,
        val playlists: List<PlaylistPayload>,
        val favoriteIds: List<Long>,
        val playHistory: List<Pair<Long, Long>>, // (mediaItemId, playedAtEpochSeconds)
        val bookmarks: List<BookmarkPayload>,
        val servers: List<ServerPayload>,
        val recentStreams: List<String>,
    ) {
        companion object {
            const val FORMAT_VERSION = 1
        }
    }

    // ---- Export -------------------------------------------------------------------

    suspend fun buildPayload(): BackupPayload {
        val settings = userSettingsPreferences.settings.first()
        val playlists = mutableListOf<PlaylistPayload>()

        playlistDao.observeAll().first().forEach { playlist ->
            val ids = playlistDao.observeItemsSnapshot(playlist.id).map { it.mediaItemId }
            playlists += PlaylistPayload(name = playlist.name, mediaItemIds = ids)
        }

        val favorites = mediaItemDao.observeFavorites().first().map { it.id }
        val history = playHistoryDao.observeAll().first().map { Pair(it.mediaItemId, it.playedAtEpochSeconds) }
        val bookmarks = bookmarkDao.observeAll().first().map {
            BookmarkPayload(it.mediaItemId, it.positionMs, it.label, it.createdAtEpochSeconds)
        }
        val (servers, recents) = networkRepository.exportState()

        return BackupPayload(
            exportedAtEpochMs = System.currentTimeMillis(),
            settings = SettingsPayload(
                language = settings.language.name,
                amoled = settings.amoled,
                accentIndex = settings.accentIndex,
                seekIncrementSeconds = settings.seekIncrementSeconds,
                autoPipOnLeave = settings.autoPipOnLeave,
                resumePlayback = settings.resumePlayback,
                subtitleScalePercent = settings.subtitleScalePercent,
                subtitlePositionBottom = settings.subtitlePositionBottom,
                autoLoadExternalSubtitles = settings.autoLoadExternalSubtitles,
                intelligentSilenceEnabled = settings.intelligentSilenceEnabled,
                autoChapterGenerationEnabled = settings.autoChapterGenerationEnabled,
                decoderPreference = settings.decoderPreference.name,
                gestureLongPressSpeedBoost = settings.gestureLongPressSpeedBoost,
                gestureHorizontalSeekDrag = settings.gestureHorizontalSeekDrag,
                gestureVerticalDragMapping = settings.gestureVerticalDragMapping.name,
            ),
            playlists = playlists,
            favoriteIds = favorites,
            playHistory = history,
            bookmarks = bookmarks,
            servers = servers.map {
                ServerPayload(it.name, it.protocol.name, it.host, it.port, it.username, it.password, it.basePath, it.secure)
            },
            recentStreams = recents,
        )
    }

    /** Writes the backup to a SAF uri; returns the byte count written.
     * H2 (data-layer review): SAF IO + JSON encoding hop to IO — callers launch on
     * viewModelScope (Main). */
    suspend fun exportTo(uri: android.net.Uri): Int = withContext(ioDispatcher) {
        val payload = buildPayload()
        val text = json.encodeToString(payload)
        val bytes = text.toByteArray(Charsets.UTF_8)
        (context.contentResolver.openOutputStream(uri) ?: throw java.io.IOException("Cannot open $uri")).use { out ->
            out.write(bytes)
        }
        bytes.size
    }

    // ---- Import -------------------------------------------------------------------

    /** Reads + applies a backup; returns a short human summary of what was applied
     * (H2: IO + parse off-main; M4-partial: library mutations are transactional and
     * settings apply last so a throw can't leave half-restored state behind). */
    suspend fun restoreFrom(uri: android.net.Uri): String = withContext(ioDispatcher) {
        val text = (context.contentResolver.openInputStream(uri)
            ?: throw java.io.IOException("Cannot read $uri")).use { it.readBytes().decodeToString() }
        val payload = json.decodeFromString<BackupPayload>(text)
        if (payload.formatVersion > BackupPayload.FORMAT_VERSION) {
            throw IllegalStateException("Backup format v${payload.formatVersion} is newer than this app supports")
        }

        val knownTrackIds = payload.favoriteIds
            .plus(payload.playHistory.map { it.first })
            .plus(payload.bookmarks.map { it.mediaItemId })
            .distinct()
            // C2 (data-layer review): chunk to stay under SQLite's bind-variable cap
            // and skip the query entirely for empty payloads (`IN ()` is invalid SQL).
            .chunked(900)
            .flatMap { mediaItemDao.getByIds(it) }
            .map { it.id }
            .toSet()

        var favouritesApplied = 0
        var historyApplied = 0
        var bookmarksApplied = 0
        var playlistsApplied = 0

        val favIdsToApply = payload.favoriteIds.filter { it in knownTrackIds }
        val historyToApply = payload.playHistory.filter { it.first in knownTrackIds }
        val bookmarksToApply = payload.bookmarks.filter { it.mediaItemId in knownTrackIds }

        database.withTransaction {
            favIdsToApply.forEach { mediaItemDao.setFavorite(it, true) }
            favouritesApplied = favIdsToApply.size

            historyToApply.forEach { (id, at) ->
                playHistoryDao.insert(com.exapps.velox.core.data.local.entity.PlayHistoryEntity(mediaItemId = id, playedAtEpochSeconds = at))
                historyApplied++
            }

            bookmarksToApply.forEach { b ->
                bookmarkDao.insert(
                    com.exapps.velox.core.data.local.entity.BookmarkEntity(
                        mediaItemId = b.mediaItemId,
                        positionMs = b.positionMs,
                        label = b.label,
                        createdAtEpochSeconds = b.createdAtEpochSeconds,
                    ),
                )
                bookmarksApplied++
            }
        }

        // Playlists need cross-table work; done after the bookmark/media transaction.
        payload.playlists.forEach { backup ->
            val playlistId = playlistDao.findByName(backup.name)?.id
                ?: playlistDao.insert(
                    com.exapps.velox.core.data.local.entity.PlaylistEntity(
                        name = backup.name,
                        createdAtEpochSeconds = System.currentTimeMillis() / 1000,
                    ),
                )
            val currentIds = playlistDao.observeItemsSnapshot(playlistId).map { it.mediaItemId }.toSet()
            val toAdd = backup.mediaItemIds.filter { it in knownTrackIds && it !in currentIds }
            if (toAdd.isNotEmpty()) playlistDao.addTracksAtEnd(playlistId, toAdd)
            playlistsApplied++
        }

        // Settings last (M4): preferences overwrite only after all library work
        // succeeded — a throw above leaves settings untouched.
        applySettings(payload.settings)

        networkRepository.importState(
            payload.servers.map {
                NetworkServer(
                    id = System.nanoTime(),
                    name = it.name,
                    protocol = runCatching { NetworkProtocol.valueOf(it.protocol) }.getOrDefault(NetworkProtocol.SMB),
                    host = it.host,
                    port = it.port,
                    username = it.username,
                    password = it.password,
                    basePath = it.basePath,
                    secure = it.secure,
                )
            },
            payload.recentStreams,
        )

        "settings · $playlistsApplied playlists · $favouritesApplied favourites · " +
            "$historyApplied history rows · $bookmarksApplied bookmarks · ${payload.servers.size} servers"
    }

    private suspend fun applySettings(s: SettingsPayload) {
        // M4-partial (data-layer review): coalesce 13 separate DataStore edits into
        // one atomic write so readers can't observe a half-restored settings state.
        val language = runCatching { AppLanguage.valueOf(s.language) }.getOrDefault(AppLanguage.SYSTEM)
        val decoder = runCatching { com.exapps.velox.core.data.preferences.DecoderPreference.valueOf(s.decoderPreference) }
            .getOrDefault(com.exapps.velox.core.data.preferences.DecoderPreference.AUTO)
        val dragMapping = runCatching { com.exapps.velox.core.data.preferences.VerticalDragMapping.valueOf(s.gestureVerticalDragMapping) }
            .getOrDefault(com.exapps.velox.core.data.preferences.VerticalDragMapping.BRIGHTNESS_LEFT_VOLUME_RIGHT)
        userSettingsPreferences.applyAll(
            language = language,
            amoled = s.amoled,
            accentIndex = s.accentIndex,
            seekIncrementSeconds = s.seekIncrementSeconds,
            autoPipOnLeave = s.autoPipOnLeave,
            resumePlayback = s.resumePlayback,
            subtitleScalePercent = s.subtitleScalePercent,
            subtitlePositionBottom = s.subtitlePositionBottom,
            autoLoadExternalSubtitles = s.autoLoadExternalSubtitles,
            intelligentSilenceEnabled = s.intelligentSilenceEnabled,
            autoChapterGenerationEnabled = s.autoChapterGenerationEnabled,
            decoderPreference = decoder,
            gestureLongPressSpeedBoost = s.gestureLongPressSpeedBoost,
            gestureHorizontalSeekDrag = s.gestureHorizontalSeekDrag,
            gestureVerticalDragMapping = dragMapping,
        )
    }

    private companion object {
        // Kept for future format bumps; BackupPayload carries its own version field.
        @Suppress("unused")
        const val FORMAT_VERSION = 1
    }
}

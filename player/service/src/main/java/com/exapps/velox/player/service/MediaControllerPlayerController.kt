package com.exapps.velox.player.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.player.PlaybackState
import com.exapps.velox.core.domain.player.PlaybackStatus
import com.exapps.velox.core.domain.player.PlayerController
import com.exapps.velox.core.domain.player.PlayerTrack
import com.exapps.velox.core.domain.player.RepeatMode
import com.exapps.velox.core.domain.player.TrackType
import com.exapps.velox.player.engine.Media3PlayerAccessor
import com.exapps.velox.player.engine.toMedia3MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import androidx.media3.common.MediaItem as Media3MediaItem
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks as Media3Tracks

/**
 * The one place outside `:player:service` itself that is allowed to know Media3
 * exists (see the `PlayerController` doc comment in `:core:domain`). Everything an
 * app-level `ViewModel` calls here is translated into `MediaController` calls
 * against the session `VeloxPlaybackService` hosts.
 *
 * Known Phase 0 simplification: the queue is tracked locally (via [queueById]) and
 * matched back up to the Media3 controller's reported item index, rather than
 * round-tripping full `MediaItem` payloads through Media3's own metadata — that
 * keeps this class simple as long as playback is always started from within the
 * app. A voice-assistant / Android Auto entry point (Phase 2+) would need the
 * reverse mapping done properly. See PROGRESS.md.
 */
@Singleton
class MediaControllerPlayerController @Inject constructor(
    @ApplicationContext private val context: Context,

) : PlayerController, Media3PlayerAccessor {

    /**
     * Media3 throws IllegalStateException when a MediaController (or Player) method
     * is touched off the looper it was built on — and the injected application scope runs
     * on Dispatchers.Default (position polling, callers from ViewModels, etc.), which
     * is exactly how the crash happened. Every controller interaction therefore runs
     * on this main-confined scope; commands are fire-and-forget because the
     * observable result surfaces through [state]/[tracks] StateFlows either way.
     */
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _tracks = MutableStateFlow<List<PlayerTrack>>(emptyList())
    override val tracks: StateFlow<List<PlayerTrack>> = _tracks.asStateFlow()

    override val media3Player: Player?
        get() = controller

    private var controller: MediaController? = null
    private var queueById: Map<String, MediaItem> = emptyMap()
    private var positionPollingJob: Job? = null

    /** Our stable track ids → the (TrackGroup, index) needed for selection overrides. */
    private var trackSelectionRefs: Map<String, Pair<TrackGroup, Int>> = emptyMap()

    init {
        val sessionToken = SessionToken(context, ComponentName(context, VeloxPlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener(
            {
                controller = future.get().also { it.addListener(playerListener) }
                syncStateFromController()
                publishTracks(controller?.currentTracks)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    override fun play(queue: List<MediaItem>, startIndex: Int) {
        mainScope.launch {
            queueById = queue.associateBy { it.id.toString() }
            val media3Items = queue.map(MediaItem::toMedia3MediaItem)
            controller?.apply {
                setMediaItems(media3Items, startIndex.coerceIn(media3Items.indices), 0L)
                prepare()
                play()
            }
        }
    }

    override fun playPause() {
        mainScope.launch {
            val c = controller ?: return@launch
            if (c.isPlaying) c.pause() else c.play()
        }
    }

    override fun pause() { mainScope.launch { controller?.pause() } }
    override fun resume() { mainScope.launch { controller?.play() } }
    override fun seekTo(positionMs: Long) { mainScope.launch { controller?.seekTo(positionMs) } }
    override fun skipNext() { mainScope.launch { controller?.seekToNextMediaItem() } }
    override fun skipPrevious() { mainScope.launch { controller?.seekToPreviousMediaItem() } }

    override fun setShuffleEnabled(enabled: Boolean) {
        _state.update { it.copy(shuffleEnabled = enabled) }
        mainScope.launch { controller?.shuffleModeEnabled = enabled }
    }

    override fun setRepeatMode(mode: RepeatMode) {
        _state.update { it.copy(repeatMode = mode) }
        mainScope.launch {
            controller?.repeatMode = when (mode) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            }
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        _state.update { it.copy(playbackSpeed = speed) }
        mainScope.launch { controller?.setPlaybackSpeed(speed) }
    }

    override fun setFavorite(mediaItemId: Long, favorite: Boolean) {
        // Favorite status is persisted via MediaLibraryRepository (see
        // ToggleFavoriteUseCase); mirrored here only so an already-open Now Playing
        // screen reflects the change immediately without waiting on a Flow re-query.
        // StateFlow.update is thread-safe, so no main hop needed.
        _state.update {
            if (it.currentItem?.id == mediaItemId) it.copy(isFavorite = favorite) else it
        }
    }

    override fun playQueueItem(index: Int) {
        mainScope.launch {
            val c = controller ?: return@launch
            if (index !in 0 until c.mediaItemCount) return@launch
            c.seekTo(index, 0L)
            c.play()
        }
    }

    override fun addToQueue(item: MediaItem) {
        queueById = queueById + (item.id.toString() to item)
        mainScope.launch { controller?.addMediaItem(item.toMedia3MediaItem()) }
    }

    override fun playNext(item: MediaItem) {
        queueById = queueById + (item.id.toString() to item)
        mainScope.launch {
            val c = controller ?: return@launch
            val insertAt = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
            c.addMediaItem(insertAt, item.toMedia3MediaItem())
        }
    }

    override fun removeFromQueue(index: Int) { mainScope.launch { controller?.removeMediaItem(index) } }
    override fun moveQueueItem(fromIndex: Int, toIndex: Int) { mainScope.launch { controller?.moveMediaItem(fromIndex, toIndex) } }
    override fun clearQueue() { mainScope.launch { controller?.clearMediaItems() } }

    override fun selectTrack(type: TrackType, trackId: String?) {
        mainScope.launch {
            val c = controller ?: return@launch
            val media3Type = when (type) {
                TrackType.AUDIO -> C.TRACK_TYPE_AUDIO
                TrackType.TEXT -> C.TRACK_TYPE_TEXT
                TrackType.VIDEO -> C.TRACK_TYPE_VIDEO
            }
            c.trackSelectionParameters = c.trackSelectionParameters.buildUpon().apply {
                clearOverridesOfType(media3Type)
                if (trackId == null) {
                    setTrackTypeDisabled(media3Type, true)
                } else {
                    setTrackTypeDisabled(media3Type, false)
                    trackSelectionRefs[trackId]?.let { (group, index) ->
                        setOverrideForType(androidx.media3.common.TrackSelectionOverride(group, index))
                    }
                }
            }.build()
        }
    }

    override fun addExternalSubtitle(uri: String, mimeType: String, label: String) {
        mainScope.launch {
            val c = controller ?: return@launch
            val index = c.currentMediaItemIndex
            val current = c.getMediaItemAt(index)
            val configuration = Media3MediaItem.SubtitleConfiguration.Builder(Uri.parse(uri))
                .setMimeType(mimeType)
                .setLabel(label)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            val existing = current.localConfiguration?.subtitleConfigurations.orEmpty()
            val withSubtitle = current.buildUpon()
                .setSubtitleConfigurations(existing + configuration)
                .build()
            c.replaceMediaItem(index, withSubtitle)
            // An earlier "subtitles off" selection would hide the side-loaded track.
            c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        }
    }

    override fun stop() { mainScope.launch { controller?.stop() } }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            managePositionPolling(isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(status = playbackState.toDomainStatus()) }
        }

        override fun onMediaItemTransition(mediaItem: Media3MediaItem?, reason: Int) {
            syncStateFromController()
        }

        override fun onTracksChanged(tracks: Media3Tracks) {
            publishTracks(tracks)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _state.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _state.update { it.copy(repeatMode = repeatMode.toDomainRepeatMode()) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            _state.update { it.copy(playbackSpeed = playbackParameters.speed) }
        }
    }

    private fun syncStateFromController() {
        val c = controller ?: return
        val currentItem = c.currentMediaItem?.mediaId?.let { queueById[it] }
        val queue = (0 until c.mediaItemCount).mapNotNull { i -> c.getMediaItemAt(i).mediaId.let { queueById[it] } }
        _state.update {
            it.copy(
                status = c.playbackState.toDomainStatus(),
                isPlaying = c.isPlaying,
                currentItem = currentItem,
                queue = queue,
                currentIndex = c.currentMediaItemIndex,
                positionMs = c.currentPosition.coerceAtLeast(0),
                bufferedPositionMs = c.bufferedPosition.coerceAtLeast(0),
                durationMs = c.duration.coerceAtLeast(0),
                shuffleEnabled = c.shuffleModeEnabled,
                repeatMode = c.repeatMode.toDomainRepeatMode(),
                playbackSpeed = c.playbackParameters.speed,
                isFavorite = currentItem?.isFavorite ?: false,
            )
        }
        managePositionPolling(c.isPlaying)
    }

    /** Media3 only pushes discrete events, not a continuous position stream — poll
     * while actively playing so the Now Playing progress bar advances smoothly, and
     * stop polling the instant playback pauses to avoid burning battery in the
     * background (SCREEN_NOW_PLAYING.md's scrubber needs live position; nothing
     * else does while paused). */
    private fun managePositionPolling(isPlaying: Boolean) {
        positionPollingJob?.cancel()
        if (!isPlaying) return
        // Main-confined: reading currentPosition/bufferedPosition off the
        // controller's application looper throws (see [mainScope] doc).
        positionPollingJob = mainScope.launch {
            while (isActive) {
                val c = controller
                if (c != null) {
                    _state.update { it.copy(positionMs = c.currentPosition.coerceAtLeast(0)) }
                }
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
    }

    private fun publishTracks(tracks: Media3Tracks?) {
        if (tracks == null) {
            _tracks.value = emptyList()
            trackSelectionRefs = emptyMap()
            return
        }
        val result = mutableListOf<PlayerTrack>()
        val refs = mutableMapOf<String, Pair<TrackGroup, Int>>()
        tracks.groups.forEach { group ->
            val domainType = when (group.type) {
                C.TRACK_TYPE_AUDIO -> TrackType.AUDIO
                C.TRACK_TYPE_TEXT -> TrackType.TEXT
                else -> return@forEach // video track switching is out of scope for v1
            }
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val format = group.getTrackFormat(i)
                val id = format.id ?: "${group.type}-${format.label}-${format.language}-$i"
                result += PlayerTrack(
                    id = id,
                    type = domainType,
                    label = format.label ?: format.language ?: "#${i + 1}",
                    language = format.language,
                    isSelected = group.isTrackSelected(i),
                )
                refs[id] = group.mediaTrackGroup to i
            }
        }
        _tracks.value = result
        trackSelectionRefs = refs
    }

    private fun Int.toDomainStatus(): PlaybackStatus = when (this) {
        Player.STATE_IDLE -> PlaybackStatus.IDLE
        Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
        Player.STATE_READY -> PlaybackStatus.READY
        Player.STATE_ENDED -> PlaybackStatus.ENDED
        else -> PlaybackStatus.IDLE
    }

    private fun Int.toDomainRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }

    private companion object {
        const val POSITION_POLL_INTERVAL_MS = 500L
    }
}

/** Maps a picked subtitle file's extension to a Media3 text MIME type. */
fun subtitleMimeTypeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "srt" -> MimeTypes.APPLICATION_SUBRIP
    "vtt" -> MimeTypes.TEXT_VTT
    "ttml", "xml", "dfxp" -> MimeTypes.APPLICATION_TTML
    "ssa", "ass" -> MimeTypes.TEXT_SSA
    else -> MimeTypes.APPLICATION_SUBRIP
}

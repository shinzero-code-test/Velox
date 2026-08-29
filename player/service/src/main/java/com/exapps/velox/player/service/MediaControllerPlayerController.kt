package com.exapps.velox.player.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.exapps.velox.core.data.preferences.UserSettingsPreferences
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.model.MediaType
import com.exapps.velox.core.domain.player.PlaybackState
import com.exapps.velox.core.domain.player.PlaybackStatus
import com.exapps.velox.core.domain.player.PlaybackPositionStore
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
import kotlinx.coroutines.flow.first
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
    private val userSettings: UserSettingsPreferences,
    private val positionStore: PlaybackPositionStore,
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

    /**
     * M6 (player-stack review): [MediaSessionService] can be killed and restarted by
     * the platform (low memory, app update, etc.) but [MediaController] lives for the
     * whole process. When the service restarts the existing controller is bound to a
     * dead session — every method call would silently no-op or throw. We register
     * a disconnect listener that nulls the controller and kicks off a fresh connect
     * attempt; the next command (or [play] call) will see `controller == null` and
     * go through [awaitController] to wait for the new connect.
     *
     * Declared before [init] so the eager `setListener(mediaControllerListener)` call
     * below runs against a non-null field.
     */
    private val mediaControllerListener = androidx.media3.session.MediaController.Listener {
        // M6: the controller died while the service restarts. Drop the reference
        // immediately and rebuild asynchronously; mainScope.launch is safe because
        // this listener is invoked on the main looper.
        mainScope.launch {
            controller = null
            trackSelectionRefs = emptyMap()
            val sessionToken = SessionToken(context, ComponentName(context, VeloxPlaybackService::class.java))
            val future = MediaController.Builder(context, sessionToken)
                .setListener(this@MediaControllerPlayerController.mediaControllerListener)
                .buildAsync()
            future.addListener(
                {
                    try {
                        controller = future.get().also { it.addListener(playerListener) }
                        syncStateFromController()
                        publishTracks(controller?.currentTracks)
                    } catch (e: Exception) {
                        android.util.Log.w("VeloxPlayer", "MediaController reconnect failed", e)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }
    }

    init {
        val sessionToken = SessionToken(context, ComponentName(context, VeloxPlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken)
            .setListener(mediaControllerListener)
            .buildAsync()
        future.addListener(
            {
                try {
                    controller = future.get().also { it.addListener(playerListener) }
                    syncStateFromController()
                    publishTracks(controller?.currentTracks)
                } catch (e: Exception) {
                    // H1 (player-stack review): unhandled connect failure crashed the
                    // main thread; log and leave controller null so awaitController()
                    // can time out cleanly instead of crashing the process.
                    android.util.Log.w("VeloxPlayer", "MediaController connect failed", e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    override fun play(queue: List<MediaItem>, startIndex: Int) {
        if (queue.isEmpty()) return
        mainScope.launch {
            queueById = queue.associateBy { it.id.toString() }
            val media3Items = queue.map(MediaItem::toMedia3MediaItem)

            // The single session player is shared by songs and videos. A speed set
            // for a video used to bleed into music playback; songs always start at
            // 1x (the Now Playing chip re-applies a song speed explicitly).
            val startType = queue.getOrNull(startIndex.coerceIn(queue.indices))?.mediaType
                ?: queue.firstOrNull()?.mediaType
            if (startType != MediaType.VIDEO && _state.value.playbackSpeed != 1f) {
                controller?.setPlaybackSpeed(1f)
                _state.update { it.copy(playbackSpeed = 1f) }
            }

            // A-B loops are per-item; a fresh queue always starts clean.
            _state.update { it.copy(loopStartMs = null, loopEndMs = null) }

            awaitController()?.apply {
                setMediaItems(media3Items, startIndex.coerceIn(media3Items.indices), 0L)
                prepare()
                play()
                applyResumePosition(startIndex, queue)
            }
        }
    }

    override fun playPause() {
        // M8 (player-stack review): all controller-touching commands go through
        // [awaitController] so a connect that's still in flight doesn't silently
        // drop the user's tap. The bounded wait (CONTROLLER_WAIT_TIMEOUT_MS)
        // prevents the coroutine from leaking if the service is dead.
        mainScope.launch {
            val c = awaitController() ?: return@launch
            if (c.isPlaying) c.pause() else c.play()
        }
    }

    override fun pause() { mainScope.launch { awaitController()?.pause() } }
    override fun resume() { mainScope.launch { awaitController()?.play() } }
    override fun seekTo(positionMs: Long) { mainScope.launch { awaitController()?.seekTo(positionMs) } }
    override fun skipNext() { mainScope.launch { awaitController()?.seekToNextMediaItem() } }
    override fun skipPrevious() { mainScope.launch { awaitController()?.seekToPreviousMediaItem() } }

    override fun setShuffleEnabled(enabled: Boolean) {
        _state.update { it.copy(shuffleEnabled = enabled) }
        mainScope.launch { awaitController()?.shuffleModeEnabled = enabled }
    }

    override fun setRepeatMode(mode: RepeatMode) {
        _state.update { it.copy(repeatMode = mode) }
        mainScope.launch {
            val c = awaitController() ?: return@launch
            c.repeatMode = when (mode) {
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

    /** Phase 2 A-B repeat — state-only bookkeeping; the poll enforces the wrap. */
    override fun setLoopRegion(startMs: Long?, endMs: Long?) {
        _state.update {
            it.copy(
                loopStartMs = startMs,
                loopEndMs = endMs?.takeIf { end -> startMs != null && end > startMs },
            )
        }
    }

    /** Phase 2 sleep-timer fade-out surface. */
    override fun setVolume(scale: Float) {
        mainScope.launch { awaitController()?.volume = scale.coerceIn(0f, 1f) }
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
            val c = awaitController() ?: return@launch
            if (index !in 0 until c.mediaItemCount) return@launch
            c.seekTo(index, 0L)
            c.play()
        }
    }

    override fun addToQueue(item: MediaItem) {
        mainScope.launch {
            queueById = queueById + (item.id.toString() to item)
            awaitController()?.addMediaItem(item.toMedia3MediaItem())
        }
    }

    override fun playNext(item: MediaItem) {
        mainScope.launch {
            queueById = queueById + (item.id.toString() to item)
            val c = awaitController() ?: return@launch
            val insertAt = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
            c.addMediaItem(insertAt, item.toMedia3MediaItem())
        }
    }

    override fun removeFromQueue(index: Int) { mainScope.launch { awaitController()?.removeMediaItem(index) } }
    override fun moveQueueItem(fromIndex: Int, toIndex: Int) { mainScope.launch { awaitController()?.moveMediaItem(fromIndex, toIndex) } }
    override fun clearQueue() {
        mainScope.launch {
            queueById = emptyMap()
            awaitController()?.clearMediaItems()
        }
    }

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

    override fun stop() {
        // M8 (player-stack review): route the stop through awaitController so
        // the resume-position write and the controller.stop() see the same
        // (possibly just-reconnected) controller instance.
        mainScope.launch {
            val c = awaitController() ?: return@launch
            saveResumePosition(c)
            c.stop()
        }
    }

    /**
     * Commands can legally arrive before the async MediaController connect
     * completes (e.g. track selection right after opening the app). Dropping them
     * made features like subtitle/track selection silently do nothing; wait a
     * short bounded window instead.
     */
    private suspend fun awaitController(): MediaController? {
        var waitedMs = 0L
        while (controller == null && waitedMs < CONTROLLER_WAIT_TIMEOUT_MS) {
            delay(CONTROLLER_POLL_MS)
            waitedMs += CONTROLLER_POLL_MS
        }
        return controller
    }

    /** Remember-position write (Settings → "Remember position" gates reads only —
     * writes are cheap and always-on so the setting applies to past plays too). */
    private suspend fun saveResumePosition(c: MediaController) {
        val duration = c.duration
        if (duration <= 0) return // unknown/unset duration → position not trustworthy
        val mediaItemId = c.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        runCatching { positionStore.put(mediaItemId, c.currentPosition.coerceAtLeast(0)) }
    }

    private suspend fun applyResumePosition(startIndex: Int, queue: List<MediaItem>) {
        val item = queue.getOrNull(startIndex) ?: return
        val resumeEnabled = runCatching { userSettings.settings.first().resumePlayback }.getOrDefault(true)
        if (!resumeEnabled) return
        val saved = runCatching { positionStore.get(item.id) }.getOrNull() ?: return
        // M3 (data-layer review): fall back to the live controller duration when the
        // domain item's durationMs is 0 (common for network streams where metadata
        // isn't known until after prepare). Skip trivial/near-end positions.
        val liveDuration = controller?.duration?.takeIf { it > 0 } ?: 0L
        val effectiveDuration = maxOf(item.durationMs, liveDuration)
        val maxResume = effectiveDuration - END_RESUME_GUARD_MS
        if (saved > START_RESUME_GUARD_MS && saved < maxResume) {
            controller?.seekTo(startIndex, saved)
        }
    }


    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (!isPlaying) {
                // Poll stops with pause — flush one last position save so "remember
                // position" never trails more than the moment of pausing.
                mainScope.launch { controller?.let { saveResumePosition(it) } }
            }
            managePositionPolling(isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(status = playbackState.toDomainStatus()) }
            if (playbackState == Player.STATE_READY) {
                // Duration/queue metadata become valid here — without this sync the
                // progress bar pinned at max while both time labels read zero until
                // the first track change happened to fire a transition event.
                syncStateFromController()
            } else if (playbackState == Player.STATE_IDLE) {
                // M2: stop()/clearMediaItems leaves stale currentItem/positionMs/
                // durationMs in state; reset so the UI doesn't render a dead session
                // as paused-ish. Shuffle/repeat/speed prefs survive.
                _state.update {
                    it.copy(
                        currentItem = null, queue = emptyList(), currentIndex = -1,
                        positionMs = 0, bufferedPositionMs = 0, durationMs = 0,
                    )
                }
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            syncStateFromController()
        }

        override fun onMediaItemTransition(mediaItem: Media3MediaItem?, reason: Int) {
            // C2 (player-stack review): clear the A-B loop on every track change so
            // it doesn't hijack subsequent queue items.
            _state.update {
                if (it.loopStartMs != null || it.loopEndMs != null)
                    it.copy(loopStartMs = null, loopEndMs = null)
                else it
            }
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

        /** H2 (player-stack review): a paused seekTo fires this but not the poll —
         * without the override the scrubber snaps back to its pre-seek position.
         *
         * M1 (player-stack review): if the new position lands outside an
         * active A-B loop on a user seek, the previously documented policy
         * was "behave however — undefined". We now snap the playback back
         * to A so the loop never plays outside its window. A `SEEK_PLACEHOLDER`
         * is also issued in `setLoopRegion` when the user picks B before A;
         * the position the scrubber lands on is clamped to the new loop
         * region so feedback is consistent. */
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            _state.update {
                it.copy(
                    positionMs = newPosition.positionMs.coerceAtLeast(0),
                    currentIndex = newPosition.mediaItemIndex,
                )
            }
            // A-B: if the discontinuity was a user seek that landed
            // outside the loop region, wrap to A. The seek-back itself
            // happens on the next poll tick (≤500 ms later) so we just
            // check the precondition here.
            val s = _state.value
            val a = s.loopStartMs
            val b = s.loopEndMs
            if (a != null && b != null && reason == Player.DISCONTINUITY_REASON_SEEK) {
                val pos = newPosition.positionMs.coerceAtLeast(0)
                if (pos < a || pos > b) {
                    mainScope.launch {
                        awaitController()?.let { c ->
                            c.seekTo(a)
                            _state.update { it.copy(positionMs = a) }
                        }
                    }
                }
            }
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
        var ticks = 0
        positionPollingJob = mainScope.launch {
            while (isActive) {
                val c = controller
                if (c != null) {
                    val position = c.currentPosition.coerceAtLeast(0)
                    _state.update { it.copy(positionMs = position) }

                    // Phase 2 A-B repeat: wrap to point A once playback crosses B.
                    val loop = _state.value
                    val loopEnd = loop.loopEndMs
                    val loopStart = loop.loopStartMs
                    if (loopEnd != null && position >= loopEnd) {
                        val restartAt = (loopStart ?: 0L)
                        c.seekTo(restartAt)
                        _state.update { it.copy(positionMs = restartAt) }
                    }
                    // L10 (player-stack review): always run the
                    // position-save cadence, even on a tick where the
                    // A-B wrap fired. The previous `else if` meant the
                    // save was skipped on wrap ticks, which silently
                    // drifted the saved position (the post-wrap
                    // `restartAt` was never persisted until the loop
                    // region ended).
                    if (++ticks % POSITION_SAVE_EVERY_TICKS == 0) {
                        saveResumePosition(c)
                    }
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
                // L8 (player-stack review): the previous fallback
                // (label-language-i) could collide when two formats in
                // the same group shared label + language + null id
                // (e.g. identical-language undifferentiated streams).
                // Hash the format's full codec/container metadata so the
                // id is unique per format within a group.
                val id = format.id ?: "${group.type}-${format.label}-${format.language}-" +
                    "${format.codecs ?: "?"}-${format.sampleMimeType ?: "?"}-$i"
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

        /** ~5s between remember-position writes (every 10th 500ms poll tick). */
        const val POSITION_SAVE_EVERY_TICKS = 10
        const val START_RESUME_GUARD_MS = 5_000L
        const val END_RESUME_GUARD_MS = 10_000L

        /** Bounded wait for the async MediaController connect (see [awaitController]). */
        const val CONTROLLER_WAIT_TIMEOUT_MS = 2_500L
        const val CONTROLLER_POLL_MS = 50L
    }
}

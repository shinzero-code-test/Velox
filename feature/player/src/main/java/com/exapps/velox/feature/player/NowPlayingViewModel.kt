package com.exapps.velox.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.common.util.LrcParser
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.player.PlaybackState
import com.exapps.velox.core.domain.player.PlayerController
import com.exapps.velox.core.domain.player.RepeatMode
import com.exapps.velox.core.domain.repository.MediaLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** §7 sleep timers — Phase 2 adds custom minutes, end-of-queue, and fade-out. */
enum class SleepTimerOption(val minutes: Int?) {
    OFF(null),
    END_OF_TRACK(null),
    END_OF_QUEUE(null),
    CUSTOM(null),
    MINUTES_15(15),
    MINUTES_30(30),
    MINUTES_60(60),
}

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val libraryRepository: MediaLibraryRepository,
    private val lyricsLoader: LyricsLoader,
    private val chaptersLoader: ChaptersLoader,
) : ViewModel() {

    val state: StateFlow<PlaybackState> = playerController.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlaybackState(),
    )

    /** Phase 1.1 "Lyrics display (basic)": sidecar .lrc (synced) or .txt (plain)
     * loaded per current track; reloaded on every item change. */
    val lyrics: StateFlow<LyricsLoader.Lyrics?> = state
        .map { it.currentItem }
        .distinctUntilChangedBy { it?.id }
        .map { item -> item?.let { lyricsLoader.load(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Index of the line that should be highlighted right now (-1 before first line). */
    val activeLyricIndex: StateFlow<Int> = combine(state, lyrics) { playback, lyrics ->
        val synced = lyrics?.syncedLines.orEmpty()
        if (synced.isEmpty()) return@combine -1
        var index = -1
        synced.forEachIndexed { i, line ->
            if ((line.timeMs ?: Long.MAX_VALUE) <= playback.positionMs) index = i
        }
        index
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1)

    private val _sleepTimer = MutableStateFlow(SleepTimerOption.OFF)
    val sleepTimer: StateFlow<SleepTimerOption> = _sleepTimer.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var watchedTrackId: Long? = null

    init {
        // Sleep-timer watch: end-of-track (current item changed) and end-of-queue
        // (last item reached) both pause before anything new starts.
        viewModelScope.launch {
            state.collect { playback ->
                when (_sleepTimer.value) {
                    SleepTimerOption.END_OF_TRACK -> {
                        val currentId = playback.currentItem?.id
                        if (watchedTrackId == null) {
                            watchedTrackId = currentId
                        } else if (currentId != null && currentId != watchedTrackId) {
                            playerController.pause()
                            setSleepTimer(SleepTimerOption.OFF)
                        }
                    }

                    SleepTimerOption.END_OF_QUEUE -> {
                        val last = playback.queue.lastOrNull()
                        val atLast = playback.currentItem?.id == last?.id &&
                            playback.currentIndex == playback.queue.lastIndex
                        if (atLast && playback.status == com.exapps.velox.core.domain.player.PlaybackStatus.ENDED) {
                            playerController.pause()
                            setSleepTimer(SleepTimerOption.OFF)
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    fun onPlayPause() = playerController.playPause()
    fun onSkipNext() = playerController.skipNext()
    fun onSkipPrevious() = playerController.skipPrevious()
    fun onSeek(positionMs: Long) = playerController.seekTo(positionMs)

    fun onQueueItemClick(index: Int) = playerController.playQueueItem(index)
    fun onQueueItemRemove(index: Int) = playerController.removeFromQueue(index)
    fun onQueueClear() = playerController.clearQueue()

    fun onToggleShuffle() = playerController.setShuffleEnabled(!state.value.shuffleEnabled)

    fun onFavoriteToggle() {
        val mediaItemId = state.value.currentItem?.id ?: return
        val favorite = !state.value.isFavorite
        // setFavorite mirrors in-memory state for instant UI feedback; the
        // repository call is what actually persists it (same pairing as
        // ToggleFavoriteUseCase does for library rows).
        playerController.setFavorite(mediaItemId, favorite)
        viewModelScope.launch { libraryRepository.setFavorite(mediaItemId, favorite) }
    }

    /** Songs share the session player with videos; play() resets audio to 1x, and
     * this chip (Now Playing §7) is where a song speed gets re-applied. */
    fun onCycleSpeed() {
        val speeds = listOf(1f, 1.25f, 1.5f, 2f)
        val next = speeds[(speeds.indexOf(state.value.playbackSpeed) + 1).mod(speeds.size)]
        playerController.setPlaybackSpeed(next)
    }

    /** Phase 1.1 "Tag editor (basic)": library-level metadata override. File tags
     * themselves aren't rewritten (MediaStore ownership walls); edits survive
     * rescans via the repository's user-metadata snapshot/restore. */
    fun onSaveTrackMetadata(id: Long, title: String, artistName: String?, albumTitle: String?) {
        if (title.isBlank()) return
        viewModelScope.launch { libraryRepository.updateTrackMetadata(id, title, artistName, albumTitle) }
    }

    fun onCycleRepeat() {
        val next = when (state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        playerController.setRepeatMode(next)
    }

    fun setSleepTimer(option: SleepTimerOption, customMinutes: Int? = null) {
        _sleepTimer.value = option
        sleepTimerJob?.cancel()
        watchedTrackId = state.value.currentItem?.id
        playerController.setVolume(1f) // reset any in-progress fade

        val minutes = when (option) {
            SleepTimerOption.CUSTOM -> customMinutes?.takeIf { it > 0 } ?: return
            else -> option.minutes ?: return
        }

        sleepTimerJob = viewModelScope.launch {
            val totalMs = minutes * 60_000L
            val fadeMs = SLEEP_FADE_MS
            if (totalMs > fadeMs) {
                delay(totalMs - fadeMs)
                // Fade-out: ramp session volume down across the final seconds so the
                // stop isn't jarring (Phase 2 "Advanced sleep timer").
                val steps = 10
                repeat(steps) { step ->
                    playerController.setVolume(1f * (steps - step - 1) / steps)
                    delay(fadeMs / steps)
                }
            }
            playerController.pause()
            playerController.setVolume(1f)
            _sleepTimer.value = SleepTimerOption.OFF
        }
    }

    // --- Phase 2: A-B repeat -------------------------------------------------------

    /** Cycles OFF → A armed → A-B loop → OFF using the current position. */
    fun onCycleLoopRegion() {
        val current = state.value
        when {
            current.loopStartMs == null ->
                playerController.setLoopRegion(current.positionMs)

            current.loopEndMs == null ->
                playerController.setLoopRegion(current.loopStartMs!!, current.positionMs.coerceAtLeast(current.loopStartMs!! + 500))

            else -> playerController.setLoopRegion(null, null)
        }
    }

    // --- Phase 2: Bookmarks & chapters ---------------------------------------------

    private var bookmarksJob: Job? = null

    private val _bookmarks = MutableStateFlow<List<com.exapps.velox.core.domain.model.Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<com.exapps.velox.core.domain.model.Bookmark>> = _bookmarks.asStateFlow()

    private val _chapters = MutableStateFlow<List<ChaptersLoader.Chapter>>(emptyList())
    val chapters: StateFlow<List<ChaptersLoader.Chapter>> = _chapters.asStateFlow()

    /** Call from the sheet when it opens — loads chapters + starts bookmark watching. */
    fun loadMarkersFor(mediaItemId: Long, item: MediaItem?) {
        bookmarksJob?.cancel()
        bookmarksJob = launchBookmarks(mediaItemId)
        _chapters.value = item?.let { chaptersLoader.load(it) }.orEmpty()
    }

    private fun launchBookmarks(mediaItemId: Long): Job = viewModelScope.launch {
        libraryRepository.observeBookmarks(mediaItemId).collect { _bookmarks.value = it }
    }

    fun onAddBookmark(positionMs: Long) {
        val id = state.value.currentItem?.id ?: return
        viewModelScope.launch {
            libraryRepository.addBookmark(id, positionMs, "Marker ${_bookmarks.value.size + 1}")
        }
    }

    fun onDeleteBookmark(bookmarkId: Long) {
        viewModelScope.launch { libraryRepository.deleteBookmark(bookmarkId) }
    }

    fun onSeekToBookmark(positionMs: Long) {
        playerController.seekTo(positionMs)
    }
}


private const val SLEEP_FADE_MS = 10_000L

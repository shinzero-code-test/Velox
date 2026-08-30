package com.exapps.velox.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.common.util.LrcParser
import com.exapps.velox.core.domain.audio.TrackAnalysisService
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.player.PlaybackState
import com.exapps.velox.core.domain.player.PlayerController
import com.exapps.velox.core.domain.player.RepeatMode
import com.exapps.velox.core.domain.recommendation.Recommendation
import com.exapps.velox.core.domain.recommendation.RecommendationEngine
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
    // Phase 3 / Wave 3 / Round 2 — auto-generated chapter
    // boundaries. Surface in the Markers sheet, merged with the
    // sidecar `.chapters.txt` source.
    private val analysisService: TrackAnalysisService,
    // Phase 3 / Wave 3 / Round 3.5 — "Up next for you" in the
    // queue sheet. The engine's `upNext()` flow is hot; we
    // dedup against the current queue so the user never sees a
    // recommendation for a track they already have queued.
    private val recommendationEngine: RecommendationEngine,
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
        // Sleep-timer watch: end-of-track fires only on a natural ENDED transition
        // (M11), and end-of-queue fires when the last item is at ENDED. Manual
        // skipNext/skipPrevious changes `currentItem.id` without changing
        // status, so a transition-by-id approach was cancelling the timer on
        // every user-initiated skip.
        //
        // L17 (features review): the player state is collected for the
        // lifetime of the VM. We accept the (negligible) battery tradeoff
        // because the same `state` flow is already kept hot by the
        // `stateIn` call below; an extra subscriber doesn't add IO, just
        // a callback. Cancelling this collector on screen background
        // would mean a sleep timer "fire while screen is off" silently
        // no-ops, which is a worse UX.
        viewModelScope.launch {
            state.collect { playback ->
                when (_sleepTimer.value) {
                    SleepTimerOption.END_OF_TRACK -> {
                        val currentId = playback.currentItem?.id
                        if (watchedTrackId == null) {
                            watchedTrackId = currentId
                        } else if (
                            currentId != null && currentId != watchedTrackId &&
                            playback.status == com.exapps.velox.core.domain.player.PlaybackStatus.ENDED
                        ) {
                            // Natural end of the watched track: pause and disarm.
                            playerController.pause()
                            setSleepTimer(SleepTimerOption.OFF)
                        }
                        // L20 (features review): REPEAT_ONE keeps the same id
                        // looping, so update the watch whenever the same track
                        // continues playing rather than only on first observation.
                        if (playback.status != com.exapps.velox.core.domain.player.PlaybackStatus.ENDED) {
                            watchedTrackId = currentId
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
        // L2 (features review): the previous version mutated `_sleepTimer`
        // and reset the volume before validating the input. A caller
        // passing `option = CUSTOM, customMinutes = 0` would leave the
        // VM with `_sleepTimer = CUSTOM` and no scheduled job, which
        // would make subsequent UI look like the timer is on while
        // nothing is happening. Validate first; only commit state when
        // we actually have a duration to schedule.
        val minutes = when (option) {
            SleepTimerOption.CUSTOM -> customMinutes?.takeIf { it > 0 } ?: return
            else -> option.minutes ?: return
        }

        _sleepTimer.value = option
        sleepTimerJob?.cancel()
        watchedTrackId = state.value.currentItem?.id
        playerController.setVolume(1f) // reset any in-progress fade

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

    private var chaptersJob: Job? = null

    /** Call from the sheet when it opens — loads chapters + starts bookmark watching. */
    /** Called from the sheet when it opens — bookmarks arrive via the existing flow
     * collector; chapters read once from the sidecar file (now suspend → IO). */
    fun loadMarkersFor(mediaItemId: Long, item: MediaItem?) {
        bookmarksJob?.cancel()
        bookmarksJob = launchBookmarks(mediaItemId)
        chaptersJob?.cancel()
        chaptersJob = launchChapters(mediaItemId, item)
    }

    private fun launchBookmarks(mediaItemId: Long): Job = viewModelScope.launch {
        libraryRepository.observeBookmarks(mediaItemId).collect { _bookmarks.value = it }
    }

    private fun launchChapters(mediaItemId: Long, item: MediaItem?): Job = viewModelScope.launch {
        // Sidecar .chapters.txt first; auto-generated chapters from
        // the audio-analysis service merge in below. Both flows are
        // combined into a single sorted list so the markers sheet
        // shows one continuous timeline.
        val sidecarFlow = kotlinx.coroutines.flow.flow {
            emit(item?.let { chaptersLoader.load(it) }.orEmpty())
        }
        val autoFlow = analysisService.observeChapters(mediaItemId)
            .map { auto ->
                auto.map {
                    ChaptersLoader.Chapter(
                        timeMs = it.positionMs,
                        title = "Chapter ${it.index + 1}",
                        // Phase 3 / Wave 3 / Round 3.5c — the
                        // autoGenerated flag propagates through
                        // the merged Chapter list so the Markers
                        // sheet can render an "auto" badge.
                        autoGenerated = it.autoGenerated,
                    )
                }
            }
        combine(sidecarFlow, autoFlow) { sidecar, auto ->
            (sidecar + auto).sortedBy { it.timeMs }
        }.collect { _chapters.value = it }
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

    /**
     * Phase 3 / Wave 3 / Round 3.5e — bulk-delete every
     * auto-detected chapter. Sidecar / embedded chapters are
     * unaffected.
     */
    fun onClearAllAutoChapters() {
        viewModelScope.launch {
            analysisService.clearAllAutoChapters()
        }
    }

    fun onSeekToBookmark(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    // --- Phase 3 / Wave 3 / Round 2: skip-intro affordance ----

    /**
     * True when the current track has a saved intro row. The UI
     * uses this to show / hide the "↪ skip intro" button on the
     * transport row. The flow re-collects every time the current
     * item id changes (state.map(...).distinctUntilChangedBy(...)).
     *
     * The smart-silence Settings toggle gates the *automatic*
     * skip on the first listen (see `MediaControllerPlayerController`).
     * The manual button here is a deliberate override: if the user
     * disabled auto-skip but the analysis row is still on disk,
     * they can still seek past the intro manually.
     */
    val hasIntro: StateFlow<Boolean> = state
        .map { it.currentItem?.id }
        .distinctUntilChangedBy { it }
        .map { id ->
            if (id == null) {
                false
            } else {
                analysisService.getIntroOutro(
                    id,
                    com.exapps.velox.core.domain.audio.IntroOutroKind.INTRO,
                ) != null
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onSkipIntro() {
        val id = state.value.currentItem?.id ?: return
        viewModelScope.launch {
            val intro = analysisService.getIntroOutro(id, com.exapps.velox.core.domain.audio.IntroOutroKind.INTRO)
                ?: return@launch
            playerController.seekTo(intro.endMs)
        }
    }

    // --- Phase 3 / Wave 3 / Round 3.5: "Up next for you" ---

    /**
     * Up-next recommendations, deduped against the current
     * queue. The engine's `upNext()` is hot and emits on every
     * play-history change; the dedup is a cheap `filterNot` over
     * the queue's id set. Empty on cold start.
     */
    val upNext: StateFlow<Recommendation.UpNext> = combine(
        state.map { it.queue.map(MediaItem::id).toSet() },
        recommendationEngine.upNext(),
    ) { queueIds, upNext ->
        Recommendation.UpNext(items = upNext.items.filterNot { it.id in queueIds })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = Recommendation.UpNext(emptyList()),
    )

    /**
     * Append a recommended track to the end of the queue. The
     * call goes through the existing `addToQueue` so the play
     * semantics (next-up auto-advance) are preserved.
     */
    fun onUpNextAppend(track: MediaItem) {
        playerController.addToQueue(track)
    }

    /**
     * Play a recommended track *next* — inserts at position
     * currentIndex + 1 so it becomes the immediate successor.
     */
    fun onUpNextPlayNext(track: MediaItem) {
        playerController.playNext(track)
    }
}


private const val SLEEP_FADE_MS = 10_000L

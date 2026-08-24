package com.exapps.velox.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** SCREEN_NOW_PLAYING.md §7's sleep timer options; null = off. */
enum class SleepTimerOption(val minutes: Int?) {
    OFF(null),
    END_OF_TRACK(null),
    MINUTES_15(15),
    MINUTES_30(30),
    MINUTES_60(60),
}

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val libraryRepository: MediaLibraryRepository,
) : ViewModel() {

    val state: StateFlow<PlaybackState> = playerController.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlaybackState(),
    )

    private val _sleepTimer = MutableStateFlow(SleepTimerOption.OFF)
    val sleepTimer: StateFlow<SleepTimerOption> = _sleepTimer.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var watchedTrackId: Long? = null

    init {
        // "End of track": watch for the current item changing and pause before the
        // next one starts (an exact end-of-media pause hook isn't observable through
        // the domain seam — this is the standard approximation).
        viewModelScope.launch {
            state.collect { playback ->
                val option = _sleepTimer.value
                if (option == SleepTimerOption.END_OF_TRACK) {
                    val currentId = playback.currentItem?.id
                    if (watchedTrackId == null) {
                        watchedTrackId = currentId
                    } else if (currentId != null && currentId != watchedTrackId) {
                        playerController.pause()
                        setSleepTimer(SleepTimerOption.OFF)
                    }
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

    fun onCycleRepeat() {
        val next = when (state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        playerController.setRepeatMode(next)
    }

    fun setSleepTimer(option: SleepTimerOption) {
        _sleepTimer.value = option
        sleepTimerJob?.cancel()
        watchedTrackId = state.value.currentItem?.id
        val minutes = option.minutes ?: return
        sleepTimerJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            playerController.pause()
            _sleepTimer.value = SleepTimerOption.OFF
        }
    }
}

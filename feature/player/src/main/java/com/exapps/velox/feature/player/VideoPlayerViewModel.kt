package com.exapps.velox.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.data.preferences.UserSettingsPreferences
import com.exapps.velox.core.domain.player.PlaybackState
import com.exapps.velox.core.domain.player.PlayerController
import com.exapps.velox.core.domain.player.PlayerTrack
import com.exapps.velox.core.domain.player.TrackType
import com.exapps.velox.core.domain.repository.MediaLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SCREEN_VIDEO_PLAYER.md. Resolves the route's media item, makes it the playing
 * queue (with the rest of the device's videos as context), and exposes everything
 * the gesture surface needs: live playback state, selectable tracks, the
 * double-tap seek increment from Settings, and transport actions.
 */
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MediaLibraryRepository,
    private val playerController: PlayerController,
    userSettings: UserSettingsPreferences,
) : ViewModel() {

    private val mediaItemId: Long = checkNotNull(savedStateHandle["mediaItemId"])

    /** The surface renderer needs the controller itself (via :player:engine's seam). */
    val controller: PlayerController get() = playerController

    val state: StateFlow<PlaybackState> = playerController.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlaybackState(),
    )

    val tracks: StateFlow<List<PlayerTrack>> = playerController.tracks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /** SCREEN_VIDEO_PLAYER.md §5: double-tap seek is configurable (5/10/15/30s). */
    val seekIncrementSeconds: StateFlow<Int> = userSettings.settings
        .map { it.seekIncrementSeconds }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 10)

    init {
        viewModelScope.launch {
            val item = repository.getById(mediaItemId) ?: return@launch
            if (playerController.state.value.currentItem?.id != item.id) {
                // Videos play within the Videos list as their queue, so Next/Previous
                // behave like the Library tab does for tracks.
                val queue = repository.observeVideos().first().ifEmpty { listOf(item) }
                val startIndex = queue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                playerController.play(queue, startIndex)
                repository.recordPlayed(item.id)
            }
        }
    }

    fun onPlayPause() = playerController.playPause()
    fun onSeekTo(positionMs: Long) = playerController.seekTo(positionMs)
    fun onSeekBy(deltaMs: Long) {
        val current = state.value
        val target = (current.positionMs + deltaMs).coerceIn(0L, current.durationMs.coerceAtLeast(0L))
        playerController.seekTo(target)
    }

    fun onSkipNext() = playerController.skipNext()
    fun onSkipPrevious() = playerController.skipPrevious()
    fun onSetSpeed(speed: Float) = playerController.setPlaybackSpeed(speed)

    fun onToggleShuffle() = playerController.setShuffleEnabled(!state.value.shuffleEnabled)

    fun onCycleRepeat() {
        val next = when (state.value.repeatMode) {
            com.exapps.velox.core.domain.player.RepeatMode.OFF -> com.exapps.velox.core.domain.player.RepeatMode.ALL
            com.exapps.velox.core.domain.player.RepeatMode.ALL -> com.exapps.velox.core.domain.player.RepeatMode.ONE
            com.exapps.velox.core.domain.player.RepeatMode.ONE -> com.exapps.velox.core.domain.player.RepeatMode.OFF
        }
        playerController.setRepeatMode(next)
    }

    fun onSelectTrack(type: TrackType, trackId: String?) = playerController.selectTrack(type, trackId)

    /** [mimeType] comes from the file extension — see player:service's subtitleMimeTypeFor. */
    fun onSubtitleFilePicked(uri: String, mimeType: String, label: String) =
        playerController.addExternalSubtitle(uri, mimeType, label)
}

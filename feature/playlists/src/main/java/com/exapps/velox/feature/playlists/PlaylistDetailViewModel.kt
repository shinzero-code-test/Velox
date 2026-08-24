package com.exapps.velox.feature.playlists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.model.PlaylistDetail
import com.exapps.velox.core.domain.repository.MediaLibraryRepository
import com.exapps.velox.core.domain.repository.PlaylistRepository
import com.exapps.velox.core.domain.usecase.PlayMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    libraryRepository: MediaLibraryRepository,
    private val playMedia: PlayMediaUseCase,
) : ViewModel() {

    // Reads the same `playlistId` nav argument the screen receives directly — Nav
    // Compose's type-safe args land in SavedStateHandle automatically, so this is
    // a plain field lookup rather than a manual Bundle parse.
    private val playlistId: Long = checkNotNull(savedStateHandle["playlistId"])

    /** System playlists (negative ids) are read-only — SCREEN_PLAYLISTS.md §3. */
    val isSystemPlaylist: Boolean = playlistId < 0

    val detail: StateFlow<PlaylistDetail?> = playlistRepository.observePlaylistDetail(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The full track list, for the "add tracks" picker (§6). */
    val libraryTracks: StateFlow<List<MediaItem>> = libraryRepository.observeTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onPlayAll(shuffle: Boolean) {
        viewModelScope.launch {
            val tracks = detail.value?.tracks.orEmpty()
            if (tracks.isEmpty()) return@launch
            val queue = if (shuffle) tracks.shuffled() else tracks
            playMedia(queue.first(), queue)
        }
    }

    fun onTrackClick(track: MediaItem) {
        viewModelScope.launch {
            playMedia(track, detail.value?.tracks?.ifEmpty { listOf(track) } ?: listOf(track))
        }
    }

    fun onAddTracks(mediaItemIds: List<Long>) {
        if (isSystemPlaylist) return
        viewModelScope.launch { playlistRepository.addTracks(playlistId, mediaItemIds) }
    }

    fun onRemoveTrack(mediaItemId: Long) {
        if (isSystemPlaylist) return
        viewModelScope.launch { playlistRepository.removeTrack(playlistId, mediaItemId) }
    }

    fun onExportM3u(destinationUri: String) {
        viewModelScope.launch {
            runCatching { playlistRepository.exportM3u(playlistId, destinationUri) }
        }
    }
}

package com.exapps.velox.feature.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.common.util.ScreenState
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.model.Playlist
import com.exapps.velox.core.domain.model.PlaylistDetail
import com.exapps.velox.core.domain.repository.PlaylistRepository
import com.exapps.velox.core.domain.usecase.PlayMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val playMedia: PlayMediaUseCase,
) : ViewModel() {

    val state: StateFlow<ScreenState<List<Playlist>>> = playlistRepository.observePlaylists()
        .map<List<Playlist>, ScreenState<List<Playlist>>> { playlists ->
            if (playlists.isEmpty()) ScreenState.Empty else ScreenState.Content(playlists)
        }
        .catch { emit(ScreenState.Error(it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    /**
     * M4 (features review): the previous `runCatching { ... }` discarded
     * every error from `importM3u`, so a corrupt or unreadable M3U file
     * silently produced nothing. The screen now subscribes to this flow
     * and surfaces a snackbar with the localized failure reason.
     */
    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

    fun clearImportMessage() {
        _importMessage.value = null
    }

    /** SCREEN_PLAYLISTS.md §5 — create flow lands here from the name dialog. */
    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { playlistRepository.createPlaylist(name.trim()) }
    }

    /** FEATURES.md §3 — import from a picked M3U/M3U8 file. */
    fun importM3u(sourceUri: String, playlistName: String) {
        viewModelScope.launch {
            val result = runCatching { playlistRepository.importM3u(sourceUri, playlistName) }
            result.onSuccess { _importMessage.value = IMPORT_SUCCESS_MARKER }
            result.onFailure {
                android.util.Log.w("VeloxPlaylists", "M3U import failed", it)
                _importMessage.value = IMPORT_FAILED_MARKER
            }
        }
    }

    // ---- Phase 3 / Round 1: Playlists two-pane helpers ----
    //
    // The Playlists screen (in two-pane mode) re-uses these instead of
    // constructing a PlaylistDetailViewModel per selection. The
    // repository's `observePlaylistDetail` is a cold flow keyed by id,
    // so each pane subscription gets its own live updates without
    // spinning up a Hilt VM.

    /** A live detail stream for any playlist id. The caller collects it. */
    fun playlistDetailFor(playlistId: Long): kotlinx.coroutines.flow.Flow<PlaylistDetail?> =
        playlistRepository.observePlaylistDetail(playlistId)

    fun isSystemPlaylist(playlistId: Long): Boolean = playlistId < 0

    fun onPlaylistPlayAll(playlistId: Long, shuffle: Boolean) {
        viewModelScope.launch {
            val detail = playlistRepository.observePlaylistDetail(playlistId).first()
            val tracks = detail.tracks
            if (tracks.isEmpty()) return@launch
            val queue = if (shuffle) tracks.shuffled() else tracks
            playMedia(queue.first(), queue)
        }
    }

    fun onPlaylistTrackClick(playlistId: Long, track: MediaItem) {
        viewModelScope.launch {
            val detail = playlistRepository.observePlaylistDetail(playlistId).first()
            val queue = detail.tracks.ifEmpty { listOf(track) }
            playMedia(track, queue)
        }
    }

    fun onPlaylistRemoveTrack(playlistId: Long, mediaItemId: Long) {
        if (playlistId < 0) return
        viewModelScope.launch { playlistRepository.removeTrack(playlistId, mediaItemId) }
    }

    fun onPlaylistAddTracks(playlistId: Long, mediaItemIds: List<Long>) {
        if (playlistId < 0) return
        viewModelScope.launch { playlistRepository.addTracks(playlistId, mediaItemIds) }
    }

    companion object {
        // M4: opaque markers carried through `importMessage` so the
        // Composable layer can map them to localized strings.xml entries
        // without a Context dependency on the ViewModel.
        const val IMPORT_SUCCESS_MARKER = "import_ok"
        const val IMPORT_FAILED_MARKER = "import_failed"
    }
}

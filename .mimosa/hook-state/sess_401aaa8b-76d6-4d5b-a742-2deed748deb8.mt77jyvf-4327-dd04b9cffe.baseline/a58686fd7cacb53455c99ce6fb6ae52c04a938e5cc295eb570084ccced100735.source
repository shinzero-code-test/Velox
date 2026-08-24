package com.exapps.velox.feature.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.common.util.ScreenState
import com.exapps.velox.core.domain.model.Playlist
import com.exapps.velox.core.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    val state: StateFlow<ScreenState<List<Playlist>>> = playlistRepository.observePlaylists()
        .map<List<Playlist>, ScreenState<List<Playlist>>> { playlists ->
            if (playlists.isEmpty()) ScreenState.Empty else ScreenState.Content(playlists)
        }
        .catch { emit(ScreenState.Error(it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    /** SCREEN_PLAYLISTS.md §5 — create flow lands here from the name dialog. */
    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { playlistRepository.createPlaylist(name.trim()) }
    }

    /** FEATURES.md §3 — import from a picked M3U/M3U8 file. */
    fun importM3u(sourceUri: String, playlistName: String) {
        viewModelScope.launch {
            runCatching { playlistRepository.importM3u(sourceUri, playlistName) }
        }
    }
}

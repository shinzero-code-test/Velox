package com.exapps.velox.feature.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.common.util.ScreenState
import com.exapps.velox.core.domain.model.Playlist
import com.exapps.velox.core.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    companion object {
        // M4: opaque markers carried through `importMessage` so the
        // Composable layer can map them to localized strings.xml entries
        // without a Context dependency on the ViewModel.
        const val IMPORT_SUCCESS_MARKER = "import_ok"
        const val IMPORT_FAILED_MARKER = "import_failed"
    }
}

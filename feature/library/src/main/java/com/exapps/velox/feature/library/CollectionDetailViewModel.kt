package com.exapps.velox.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.repository.MediaLibraryRepository
import com.exapps.velox.core.domain.usecase.PlayMediaUseCase
import com.exapps.velox.core.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * One view-model behind the three Library grouping tabs' detail screens (album /
 * artist / folder). Type-safe nav lands each route's properties in the
 * [SavedStateHandle] under its own name, so exactly one of albumId / artistId /
 * folderPath is present and `title` always is — see VeloxRoutes.
 */
@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MediaLibraryRepository,
    private val playMedia: PlayMediaUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    private val albumId: Long? = savedStateHandle["albumId"]
    private val artistId: Long? = savedStateHandle["artistId"]
    private val folderPath: String? = savedStateHandle["folderPath"]

    /** Header title — carried through navigation to avoid a repository round-trip. */
    val title: String = checkNotNull(
        savedStateHandle["title"]
            ?: folderPath?.let { File(it).name },
    ) { "CollectionDetail route must carry a title" }

    val tracks: StateFlow<List<MediaItem>> = when {
        albumId != null -> repository.observeAlbumTracks(albumId)
        artistId != null -> repository.observeArtistTracks(artistId)
        else -> repository.observeFolderContents(checkNotNull(folderPath))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onTrackClick(track: MediaItem) {
        viewModelScope.launch { playMedia(track, tracks.value) }
    }

    fun onToggleFavorite(track: MediaItem) {
        viewModelScope.launch { toggleFavorite(track) }
    }
}

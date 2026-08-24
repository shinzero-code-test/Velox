package com.exapps.velox.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.common.util.ScreenState
import com.exapps.velox.core.domain.model.LibraryGroup
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.model.SortOrder
import com.exapps.velox.core.domain.repository.MediaLibraryRepository
import com.exapps.velox.core.domain.usecase.PlayMediaUseCase
import com.exapps.velox.core.domain.usecase.ScanLibraryUseCase
import com.exapps.velox.core.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MediaLibraryRepository,
    private val playMedia: PlayMediaUseCase,
    private val scanLibrary: ScanLibraryUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(LibraryGroup.TRACKS)
    val selectedTab: StateFlow<LibraryGroup> = _selectedTab.asStateFlow()

    private val _hasMediaPermission = MutableStateFlow(false)
    val hasMediaPermission: StateFlow<Boolean> = _hasMediaPermission.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** ROADMAP M2 "Search & sort" — applies to the Tracks and Videos tabs. */
    private val _sortOrder = MutableStateFlow(SortOrder.TITLE)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    val content: StateFlow<ScreenState<LibraryContent>> = combine(
        selectedTab,
        hasMediaPermission,
        sortOrder,
    ) { tab, hasPermission, sort -> Triple(tab, hasPermission, sort) }
        .flatMapLatest { (tab, hasPermission, sort) ->
            if (!hasPermission) {
                flowOf(ScreenState.PermissionRequired())
            } else {
                observeContentFor(tab, sort)
            }
        }
        .catch { emit(ScreenState.Error(it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    private fun observeContentFor(tab: LibraryGroup, sortOrder: SortOrder) = when (tab) {
        LibraryGroup.TRACKS -> repository.observeTracks(sortOrder).map { LibraryContent.Tracks(it).asScreenState() }
        LibraryGroup.VIDEOS -> repository.observeVideos(sortOrder).map { LibraryContent.Videos(it).asScreenState() }
        LibraryGroup.ALBUMS -> repository.observeAlbums().map {
            // Groupings have no date-added/size of their own; those sort options
            // degrade to title order rather than doing nothing (bug: "sort only
            // works on Songs").
            LibraryContent.Albums(it.sortedFor(sortOrder) { album -> album.title }).asScreenState()
        }
        LibraryGroup.ARTISTS -> repository.observeArtists().map {
            LibraryContent.Artists(it.sortedFor(sortOrder) { artist -> artist.name }).asScreenState()
        }
        LibraryGroup.FOLDERS -> repository.observeFolders().map {
            LibraryContent.Folders(it.sortedFor(sortOrder) { folder -> folder.displayName }).asScreenState()
        }
        LibraryGroup.RECENT -> repository.observeRecentlyPlayed().map { LibraryContent.Tracks(it).asScreenState() }
        LibraryGroup.GENRES -> repository.observeTracks().map { LibraryContent.Tracks(it).asScreenState() } // TODO(Phase 2)
    }

    /** Case-insensitive title sort for the grouping tabs; PATH maps to the same
     * natural key, and the options without a grouping analogue fall back to it. */
    private fun <T> List<T>.sortedFor(sortOrder: SortOrder, key: (T) -> String): List<T> =
        sortedWith(compareBy({ item -> key(item).lowercase() }))

    private fun LibraryContent.asScreenState(): ScreenState<LibraryContent> =
        if (isEmpty) ScreenState.Empty else ScreenState.Content(this)

    fun onTabSelected(tab: LibraryGroup) {
        _selectedTab.value = tab
    }

    fun onSortSelected(sortOrder: SortOrder) {
        _sortOrder.value = sortOrder
    }

    /** Called once from the screen after the runtime permission result comes back
     * (and on first composition, in case it was already granted from a prior
     * launch) — see LibraryScreen's rememberLauncherForActivityResult wiring. */
    fun onMediaPermissionResult(granted: Boolean) {
        _hasMediaPermission.value = granted
        if (granted) rescan()
    }

    fun rescan() {
        viewModelScope.launch {
            _isScanning.value = true
            runCatching { scanLibrary() }
            _isScanning.value = false
        }
    }

    fun onTrackClick(track: MediaItem, queue: List<MediaItem>) {
        viewModelScope.launch { playMedia(track, queue) }
    }

    fun onToggleFavorite(track: MediaItem) {
        viewModelScope.launch { toggleFavorite(track) }
    }
}

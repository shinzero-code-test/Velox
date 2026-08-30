package com.exapps.velox.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.common.util.ScreenState
import com.exapps.velox.core.domain.model.LibraryGroup
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.model.SortOrder
import com.exapps.velox.core.domain.recommendation.Recommendation
import com.exapps.velox.core.domain.recommendation.RecommendationEngine
import com.exapps.velox.core.domain.repository.MediaLibraryRepository
import com.exapps.velox.core.domain.usecase.PlayMediaUseCase
import com.exapps.velox.core.domain.usecase.ScanLibraryUseCase
import com.exapps.velox.core.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
    // Phase 3 / Wave 3 / Round 3 — Milestone 7. The "Recommended"
    // row is the engine's `forYou()` flow projected to a
    // LibraryContent.Recommended. We collect it as a
    // StateFlow and `combine` it with the active tab's
    // content below.
    private val recommendationEngine: RecommendationEngine,
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

    /**
     * Phase 3 / Wave 3 / Round 3 — Milestone 7. The "Recommended"
     * row above the active tab. The flow is hot via
     * [SharingStarted.Eagerly] so the Library's first frame
     * already has the latest recommendations (or an empty list
     * while the engine is warming up).
     */
    val recommended: StateFlow<Recommendation.ForYou> = recommendationEngine.forYou()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = Recommendation.ForYou(emptyList()),
        )

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
        LibraryGroup.GENRES -> repository.observeGenres().map {
            LibraryContent.Genres(it.sortedFor(sortOrder) { genre -> genre.name }).asScreenState()
        }
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

    /** Called from the screen after the runtime permission result comes back.
     *
     * M5 (features review): the previous behaviour rescaned on every
     * tab-return, because the screen's `LaunchedEffect(Unit)` reports the
     * current permission state to this VM on every fresh composition, and
     * a permission grant was always followed by `rescan()`. We now only
     * rescan when the permission transitions from denied → granted; if
     * the user has had the permission all along, the data has been
     * observed live via the Flow the whole time and a rescan would be
     * pure duplicate work. Manual rescan (the toolbar button / retry)
     * still calls [rescan] directly. */
    fun onMediaPermissionResult(granted: Boolean) {
        val wasGranted = _hasMediaPermission.value
        _hasMediaPermission.value = granted
        if (granted && !wasGranted) rescan()
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

    /**
     * Phase 3 / Milestone 3 completion — Better tablet layouts. The
     * in-place list-detail pane in [LibraryScreen] needs a
     * `Flow<List<MediaItem>>` for the user's currently-selected
     * collection, without spinning up a new Hilt ViewModel for each
     * selection. We delegate to the repository directly; the flow is
     * cold and the parent screen collects it via `collectAsState`.
     *
     * The track click + favourite-toggle callbacks are routed through
     * the existing [onTrackClick] / [onToggleFavorite] methods so the
     * play semantics (queue = the whole collection) and the favourite
     * write path are unchanged.
     */
    fun tracksFor(key: CollectionKey): Flow<List<MediaItem>> =
        repository.observeCollection(key)

    /**
     * Same as the single-pane [onTrackClick], but resolves the
     * collection from the supplied [CollectionKey] instead of
     * requiring the caller to pass the queue. The current
     * implementation just falls through to [onTrackClick] with the
     * snapshot of the flow that the VM is already collecting; if the
     * flow hasn't emitted yet, it falls back to a single-track queue
     * (a track can play itself; the next emit will update the queue
     * for the *next* play, not the one in progress).
     */
    fun onCollectionTrackClick(key: CollectionKey, track: MediaItem) {
        viewModelScope.launch {
            // Take the first emission of the collection's tracks; the
            // play semantics (queue = whole collection) are the same
            // as CollectionDetailViewModel.onTrackClick in the
            // single-pane path.
            val queue = tracksFor(key).map { ScreenState.Content(it) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, ScreenState.Loading)
                .value
                .let { (it as? ScreenState.Content)?.data.orEmpty() }
            playMedia(track, queue)
        }
    }
}

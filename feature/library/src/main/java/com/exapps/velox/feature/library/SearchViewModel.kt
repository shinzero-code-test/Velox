package com.exapps.velox.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.recommendation.Recommendation
import com.exapps.velox.core.domain.recommendation.RecommendationEngine
import com.exapps.velox.core.domain.repository.MediaLibraryRepository
import com.exapps.velox.core.domain.usecase.PlayMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MediaLibraryRepository,
    private val playMedia: PlayMediaUseCase,
    // Phase 3 / Wave 3 / Round 3.5 — "Because you listened to X"
    // surface in the search screen. The engine exposes
    // `becauseYouListened(seedTrackId)`; the screen looks it up
    // whenever the user has exactly one search result.
    private val recommendationEngine: RecommendationEngine,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * Becomes true the moment a non-blank query has been debounced and
     * looked up. The screen uses this to keep showing the "type to search"
     * prompt during the 250ms debounce window instead of flashing the
     * "no results" empty state (library dead code / features review).
     */
    private val _hasQueried = MutableStateFlow(false)
    val hasQueried: StateFlow<Boolean> = _hasQueried.asStateFlow()

    val results: StateFlow<List<MediaItem>> = _query
        .debounce(SEARCH_DEBOUNCE_MS)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.isBlank()) {
                _hasQueried.value = false
                flowOf(emptyList())
            } else {
                _hasQueried.value = true
                repository.search(q)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Phase 3 / Wave 3 / Round 3.5 — "Because you listened to X".
     * Emits a non-null [Recommendation.BecauseYouListened] only
     * when the search has exactly one result (the user is
     * searching for a known track) AND the engine has at least
     * one neighbour for that track. Otherwise the section is
     * hidden.
     */
    val becauseYouListened: StateFlow<Recommendation.BecauseYouListened?> = results
        .map { rs -> rs.singleOrNull()?.id }
        .distinctUntilChanged()
        .flatMapLatest { seedId ->
            if (seedId == null) flowOf<Recommendation.BecauseYouListened?>(null)
            else recommendationEngine.becauseYouListened(seedId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onClearQuery() {
        _query.value = ""
    }

    fun onResultClick(item: MediaItem) {
        viewModelScope.launch { playMedia(item, listOf(item)) }
    }

    /**
     * Phase 3 / Wave 3 / Round 3.5 — play a "Because you listened
     * to X" recommendation. The seed track isn't in the
     * recommendation list, so we just play the recommended track
     * with itself as the queue.
     */
    fun onRecommendationClick(item: MediaItem) {
        viewModelScope.launch { playMedia(item, listOf(item)) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}

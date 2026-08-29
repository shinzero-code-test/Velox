package com.exapps.velox.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.domain.model.MediaItem
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MediaLibraryRepository,
    private val playMedia: PlayMediaUseCase,
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

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onClearQuery() {
        _query.value = ""
    }

    fun onResultClick(item: MediaItem) {
        viewModelScope.launch { playMedia(item, listOf(item)) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}

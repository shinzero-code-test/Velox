package com.exapps.velox.core.domain.usecase

import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.repository.MediaLibraryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// NOTE on scope: library *reads* (observeTracks/observeAlbums/observeArtists/...) are
// called directly from LibraryViewModel against the MediaLibraryRepository interface —
// a single pass-through repository call doesn't earn a dedicated UseCase, and a
// unified "ObserveLibraryUseCase" would have to erase Tracks/Albums/Artists/Folders
// to a common supertype to compile, which trades away real type safety for not much.
// UseCases here are reserved for the ones with actual behavior beyond one repository
// call: scanning, favoriting, searching, and (in PlaybackUseCases.kt) playing.

class ScanLibraryUseCase @Inject constructor(
    private val repository: MediaLibraryRepository,
) {
    suspend operator fun invoke() = repository.rescanLibrary()
}

class ToggleFavoriteUseCase @Inject constructor(
    private val repository: MediaLibraryRepository,
) {
    suspend operator fun invoke(mediaItem: MediaItem) {
        repository.setFavorite(mediaItem.id, !mediaItem.isFavorite)
    }
}

class SearchLibraryUseCase @Inject constructor(
    private val repository: MediaLibraryRepository,
) {
    operator fun invoke(query: String): Flow<List<MediaItem>> = repository.search(query)
}

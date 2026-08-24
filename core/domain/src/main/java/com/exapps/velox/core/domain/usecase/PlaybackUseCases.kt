package com.exapps.velox.core.domain.usecase

import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.player.PlayerController
import com.exapps.velox.core.domain.repository.MediaLibraryRepository
import javax.inject.Inject

/**
 * Starts playback of [item] within [queue] (SCREEN_HOME_LIBRARY.md §9: "Tap item →
 * Play") and records it into play history (FEATURES.md §2: "Recently played &
 * Most played") — two side effects behind one call, which is exactly the kind of
 * thing a UseCase should own instead of the ViewModel juggling both itself.
 */
class PlayMediaUseCase @Inject constructor(
    private val playerController: PlayerController,
    private val libraryRepository: MediaLibraryRepository,
) {
    suspend operator fun invoke(item: MediaItem, queue: List<MediaItem> = listOf(item)) {
        val startIndex = queue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        playerController.play(queue, startIndex)
        libraryRepository.recordPlayed(item.id)
    }
}

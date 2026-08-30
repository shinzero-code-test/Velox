package com.exapps.velox.feature.library

import com.exapps.velox.core.domain.model.Album
import com.exapps.velox.core.domain.model.Artist
import com.exapps.velox.core.domain.model.Folder
import com.exapps.velox.core.domain.model.Genre
import com.exapps.velox.core.domain.model.LibraryGroup
import com.exapps.velox.core.domain.model.MediaItem

/** One case per [LibraryGroup] tab, each keeping its real domain type — see the
 * note in core:domain's LibraryUseCases.kt on why this isn't erased to `List<Any>`. */
sealed interface LibraryContent {
    data class Tracks(val items: List<MediaItem>) : LibraryContent
    data class Videos(val items: List<MediaItem>) : LibraryContent
    data class Albums(val items: List<Album>) : LibraryContent
    data class Artists(val items: List<Artist>) : LibraryContent
    data class Folders(val items: List<Folder>) : LibraryContent
    data class Genres(val items: List<Genre>) : LibraryContent

    /**
     * Phase 3 / Wave 3 / Round 3 — Milestone 7. The
     * "Recommended" row at the top of the Library tab. Loaded
     * alongside the chosen tab; the renderer decides how to lay
     * it out (a horizontal scroll above the active tab's
     * LazyColumn).
     */
    data class Recommended(val items: List<MediaItem>) : LibraryContent

    val isEmpty: Boolean get() = when (this) {
        is Tracks -> items.isEmpty()
        is Videos -> items.isEmpty()
        is Albums -> items.isEmpty()
        is Artists -> items.isEmpty()
        is Folders -> items.isEmpty()
        is Genres -> items.isEmpty()
        is Recommended -> items.isEmpty()
    }
}

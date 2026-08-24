package com.exapps.velox.feature.library

import com.exapps.velox.core.domain.model.Album
import com.exapps.velox.core.domain.model.Artist
import com.exapps.velox.core.domain.model.Folder
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

    val isEmpty: Boolean get() = when (this) {
        is Tracks -> items.isEmpty()
        is Videos -> items.isEmpty()
        is Albums -> items.isEmpty()
        is Artists -> items.isEmpty()
        is Folders -> items.isEmpty()
    }
}

package com.exapps.velox.navigation

import kotlinx.serialization.Serializable

/**
 * TECHNICAL_PLAN.md: "Navigation Compose (type-safe preferred)". One object/data
 * class per destination in SCREENS_OVERVIEW.md's screen map. Screens not yet built
 * beyond a placeholder (see PROGRESS.md) still get a real route now, so the nav
 * graph itself — Phase 0 item 4 — is complete even where the content behind a
 * route isn't yet.
 */
sealed interface VeloxRoute {

    @Serializable
    data object Onboarding : VeloxRoute

    // --- bottom-nav destinations ---
    @Serializable
    data object Library : VeloxRoute

    @Serializable
    data object Playlists : VeloxRoute

    @Serializable
    data object Search : VeloxRoute

    @Serializable
    data object Settings : VeloxRoute

    // --- pushed on top of the bottom-nav destinations ---
    @Serializable
    data object NowPlaying : VeloxRoute

    @Serializable
    data class VideoPlayer(val mediaItemId: Long) : VeloxRoute

    @Serializable
    data class PlaylistDetail(val playlistId: Long) : VeloxRoute

    /** Detail screens for the Library's grouping tabs — reachable by tapping an
     * album/artist/folder card (previously these cards had no-op click handlers).
     * `title` rides along so the header doesn't need a repository lookup. */
    @Serializable
    data class AlbumDetail(val albumId: Long, val title: String) : VeloxRoute

    @Serializable
    data class ArtistDetail(val artistId: Long, val title: String) : VeloxRoute

    @Serializable
    data class FolderDetail(val folderPath: String, val title: String) : VeloxRoute

    @Serializable
    data object Equalizer : VeloxRoute
}

/** The four tabs that share the bottom navigation bar + persistent Mini Player chrome
 * (SCREEN_PATTERNS.md: "Mini Player: Global — persists across all screens except
 * Now Playing, Video Player (fullscreen), and Onboarding"). */
val BOTTOM_NAV_ROUTES: List<VeloxRoute> = listOf(
    VeloxRoute.Library,
    VeloxRoute.Playlists,
    VeloxRoute.Search,
    VeloxRoute.Settings,
)

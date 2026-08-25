package com.exapps.velox.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.exapps.velox.AppViewModel
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.model.MediaType
import com.exapps.velox.feature.equalizer.EqualizerScreen
import com.exapps.velox.feature.library.CollectionDetailScreen
import com.exapps.velox.feature.library.LibraryScreen
import com.exapps.velox.feature.library.SearchScreen
import com.exapps.velox.feature.player.NowPlayingScreen
import com.exapps.velox.feature.player.VideoPlayerScreen
import com.exapps.velox.feature.playlists.PlaylistDetailScreen
import com.exapps.velox.feature.playlists.PlaylistsScreen
import com.exapps.velox.feature.settings.SettingsScreen
import com.exapps.velox.ui.OnboardingScreen
import kotlinx.coroutines.launch

@Composable
fun VeloxNavHost(startDestination: VeloxRoute, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val appViewModel: AppViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current.findActivity()

    fun openMediaItem(item: MediaItem) {
        // SCREEN_VIDEO_PLAYER.md §11: videos open the fullscreen video chrome;
        // audio expands Now Playing. The item's own type decides, not the tab.
        if (item.mediaType == MediaType.VIDEO) {
            navController.navigate(VeloxRoute.VideoPlayer(item.id))
        } else {
            navController.navigate(VeloxRoute.NowPlaying)
        }
    }

    // Phase 1 M4 "File association": an externally-opened file already started
    // playing in MainActivity — surface the matching player chrome once the graph
    // exists, then clear the one-shot request.
    LaunchedEffect(Unit) {
        appViewModel.externalPlayback.collect { request ->
            when (request) {
                null -> Unit
                is com.exapps.velox.ExternalPlayback -> {
                    // v0.4.0: always the Now Playing chrome — the fullscreen video
                    // route resolves its item through the library database, which
                    // externally-opened files aren't in. Documented in PROGRESS.md.
                    navController.navigate(VeloxRoute.NowPlaying)
                    appViewModel.consumeExternalPlayback()
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {

        composable<VeloxRoute.Onboarding> {
            OnboardingScreen(
                onFinish = { _ ->
                    scope.launch {
                        appViewModel.onOnboardingComplete()
                        navController.navigate(VeloxRoute.Library) {
                            popUpTo(VeloxRoute.Onboarding) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable<VeloxRoute.Library> {
            MainScaffold(
                currentRoute = VeloxRoute.Library,
                onNavigate = { navController.navigateToTab(it) },
                onExpandPlayer = { navController.navigate(VeloxRoute.NowPlaying) },
            ) {
                LibraryScreen(
                    onMediaItemClick = ::openMediaItem,
                    onAlbumClick = { navController.navigate(VeloxRoute.AlbumDetail(it.id, it.title)) },
                    onArtistClick = { navController.navigate(VeloxRoute.ArtistDetail(it.id, it.name)) },
                    onFolderClick = { navController.navigate(VeloxRoute.FolderDetail(it.path, it.displayName)) },
                    onGenreClick = { navController.navigate(VeloxRoute.GenreDetail(it.name, it.name)) },
                )
            }
        }

        composable<VeloxRoute.Playlists> {
            MainScaffold(
                currentRoute = VeloxRoute.Playlists,
                onNavigate = { navController.navigateToTab(it) },
                onExpandPlayer = { navController.navigate(VeloxRoute.NowPlaying) },
            ) {
                PlaylistsScreen(onPlaylistClick = { navController.navigate(VeloxRoute.PlaylistDetail(it)) })
            }
        }

        composable<VeloxRoute.Search> {
            MainScaffold(
                currentRoute = VeloxRoute.Search,
                onNavigate = { navController.navigateToTab(it) },
                onExpandPlayer = { navController.navigate(VeloxRoute.NowPlaying) },
            ) {
                SearchScreen(onResultClick = ::openMediaItem)
            }
        }

        composable<VeloxRoute.Settings> {
            MainScaffold(
                currentRoute = VeloxRoute.Settings,
                onNavigate = { navController.navigateToTab(it) },
                onExpandPlayer = { navController.navigate(VeloxRoute.NowPlaying) },
            ) {
                val shareContext = LocalContext.current
                SettingsScreen(
                    // Locale changes only take effect through attachBaseContext —
                    // recreate() re-runs it with the freshly persisted language.
                    onLanguageChanged = { activity?.recreate() },
                    onShareCrashLog = { text ->
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, text)
                        }
                        shareContext.startActivity(
                            android.content.Intent.createChooser(send, null)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    onReplayIntro = { navController.navigate(VeloxRoute.Onboarding) },
                )
            }
        }

        composable<VeloxRoute.NowPlaying> {
            NowPlayingScreen(
                onCollapse = { navController.popBackStack() },
                onOpenEqualizer = { navController.navigate(VeloxRoute.Equalizer) },
            )
        }

        composable<VeloxRoute.VideoPlayer> { entry ->
            val route: VeloxRoute.VideoPlayer = entry.toRoute()
            VideoPlayerScreen(
                mediaItemId = route.mediaItemId,
                onBack = { navController.popBackStack() },
            )
        }

        composable<VeloxRoute.PlaylistDetail> {
            PlaylistDetailScreen(
                playlistId = it.toRoute<VeloxRoute.PlaylistDetail>().playlistId,
                onBack = { navController.popBackStack() },
                onMediaItemClick = ::openMediaItem,
            )
        }

        // Library grouping tabs' detail screens (album / artist / folder).
        composable<VeloxRoute.AlbumDetail> {
            CollectionDetailScreen(
                onBack = { navController.popBackStack() },
                onMediaItemClick = ::openMediaItem,
            )
        }

        composable<VeloxRoute.ArtistDetail> {
            CollectionDetailScreen(
                onBack = { navController.popBackStack() },
                onMediaItemClick = ::openMediaItem,
            )
        }

        composable<VeloxRoute.FolderDetail> {
            CollectionDetailScreen(
                onBack = { navController.popBackStack() },
                onMediaItemClick = ::openMediaItem,
            )
        }

        composable<VeloxRoute.GenreDetail> {
            CollectionDetailScreen(
                onBack = { navController.popBackStack() },
                onMediaItemClick = ::openMediaItem,
            )
        }

        composable<VeloxRoute.Equalizer> {
            EqualizerScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** Bottom-tab navigation should behave like tab switching, not stack-pushing:
 * single top instance per tab, and popping back to the graph's start tab instead
 * of piling up a deep back stack across Library → Playlists → Search → Settings. */
private fun NavController.navigateToTab(route: VeloxRoute) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** LocalContext is frequently a ContextThemeWrapper (or deeper) rather than the
 * Activity itself — unwrap the base-context chain to reach it. Used for the
 * language-change recreate() hook, which must not silently no-op on a cast. */
private tailrec fun android.content.Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

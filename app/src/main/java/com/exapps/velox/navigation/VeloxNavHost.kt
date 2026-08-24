package com.exapps.velox.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.findStartDestination
import androidx.navigation.toRoute
import com.exapps.velox.AppViewModel
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.model.MediaType
import com.exapps.velox.feature.equalizer.EqualizerScreen
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
    val activity = LocalContext.current as? ComponentActivity

    fun openMediaItem(item: MediaItem) {
        // SCREEN_VIDEO_PLAYER.md §11: videos open the fullscreen video chrome;
        // audio expands Now Playing. The item's own type decides, not the tab.
        if (item.mediaType == MediaType.VIDEO) {
            navController.navigate(VeloxRoute.VideoPlayer(item.id))
        } else {
            navController.navigate(VeloxRoute.NowPlaying)
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
                LibraryScreen(onMediaItemClick = ::openMediaItem)
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
                SettingsScreen(
                    // Locale changes only take effect through attachBaseContext —
                    // recreate() re-runs it with the freshly persisted language.
                    onLanguageChanged = { activity?.recreate() },
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
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

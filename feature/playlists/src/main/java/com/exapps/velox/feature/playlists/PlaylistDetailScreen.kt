package com.exapps.velox.feature.playlists

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.ui.components.VeloxFullScreenLoading
import com.exapps.velox.core.ui.theme.VeloxSpacing

/**
 * SCREEN_PLAYLISTS.md §6 — header with totals, Play All / Shuffle,
 * track rows with remove, and the add-tracks picker for (empty or
 * not) user playlists.
 *
 * Phase 3 / Round 1: this is now a thin route shell. The body is
 * delegated to [PlaylistDetailContent] (the same composable the
 * Playlists two-pane uses for the in-place detail pane).
 */
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onMediaItemClick: (MediaItem) -> Unit = {},
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val libraryTracks by viewModel.libraryTracks.collectAsStateWithLifecycle()
    val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()
    var showAddTracksSheet by remember { mutableStateOf(false) }

    val exportSnackbar = remember { SnackbarHostState() }
    val exportSuccess = stringResource(R.string.playlists_export_done)
    val exportFailure = stringResource(R.string.playlists_export_failed)
    LaunchedEffect(exportMessage) {
        exportMessage?.let { marker ->
            val localized = when (marker) {
                PlaylistDetailViewModel.EXPORT_SUCCESS_MARKER -> exportSuccess
                PlaylistDetailViewModel.EXPORT_FAILED_MARKER -> exportFailure
                else -> marker
            }
            exportSnackbar.showSnackbar(localized)
            viewModel.clearExportMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl"),
    ) { uri ->
        uri?.let { viewModel.onExportM3u(it.toString()) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PlaylistDetailContent(
            detail = detail,
            isSystemPlaylist = viewModel.isSystemPlaylist,
            onBack = onBack,
            onPlayAll = { viewModel.onPlayAll(shuffle = false) },
            onShuffle = { viewModel.onPlayAll(shuffle = true) },
            onTrackClick = { track ->
                viewModel.onTrackClick(track)
                onMediaItemClick(track)
            },
            onRemoveTrack = { track -> viewModel.onRemoveTrack(track.id) },
            onAddTracksRequested = { showAddTracksSheet = true },
            onExportRequested = {
                val name = detail?.playlist?.name?.ifEmpty { "playlist" } ?: "playlist"
                exportLauncher.launch(name)
            },
        )

        // Loading shim: while `detail` is null, fall back to the full-screen
        // loading indicator the route had before. The content composable
        // also shows a spinner inside its column, but the route's larger
        // surface reads better with this overlay.
        if (detail == null) {
            Box(modifier = Modifier.fillMaxSize()) {
                VeloxFullScreenLoading()
            }
        }

        if (showAddTracksSheet) {
            AddTracksSheet(
                allTracks = libraryTracks,
                onAdd = { ids ->
                    viewModel.onAddTracks(ids)
                    showAddTracksSheet = false
                },
                onDismiss = { showAddTracksSheet = false },
            )
        }

        SnackbarHost(
            hostState = exportSnackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(VeloxSpacing.lg),
        )
    }
}

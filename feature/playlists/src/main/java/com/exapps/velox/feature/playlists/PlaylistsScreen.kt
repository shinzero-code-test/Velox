package com.exapps.velox.feature.playlists

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exapps.velox.core.common.util.ScreenState
import com.exapps.velox.core.domain.model.Playlist
import com.exapps.velox.core.domain.model.PlaylistType
import com.exapps.velox.core.ui.components.ClickableGlassCard
import com.exapps.velox.core.ui.components.VeloxEmptyState
import com.exapps.velox.core.ui.components.VeloxFullScreenLoading
import com.exapps.velox.core.ui.components.VeloxGlassIconButton
import com.exapps.velox.core.ui.layout.DefaultWindowSizeClass
import com.exapps.velox.core.ui.layout.isCompact
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxSpacing
import com.exapps.velox.core.ui.theme.VeloxTheme
import com.exapps.velox.core.ui.theme.accentColor
import com.exapps.velox.core.ui.theme.glassOutlineColor
import com.exapps.velox.core.ui.theme.glassSurfaceColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** SCREEN_PLAYLISTS.md §2/§5 — system + user playlists, "+" to create, M3U import. */
@Composable
fun PlaylistsScreen(
    onPlaylistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass = DefaultWindowSizeClass,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    if (windowSizeClass.isCompact) {
        PlaylistsSinglePane(
            onPlaylistClick = onPlaylistClick,
            modifier = modifier,
            viewModel = viewModel,
        )
    } else {
        PlaylistsTwoPane(
            onMediaItemClick = onPlaylistClick, // the route's onPlaylistClick navigates
            modifier = modifier,
            viewModel = viewModel,
        )
    }
}

@Composable
private fun PlaylistsSinglePane(
    onPlaylistClick: (Long) -> Unit,
    modifier: Modifier,
    viewModel: PlaylistsViewModel,
) {
    PlaylistsBase(
        onRowClick = { playlist -> onPlaylistClick(playlist.id) },
        showDetailPane = false,
        selectedPlaylistId = null,
        onClearSelection = {},
        onMediaItemClick = {},
        modifier = modifier,
        viewModel = viewModel,
    )
}

@Composable
private fun PlaylistsTwoPane(
    onMediaItemClick: (Long) -> Unit,
    modifier: Modifier,
    viewModel: PlaylistsViewModel,
) {
    var selectedPlaylistId by rememberSaveable { mutableStateOf<Long?>(null) }
    PlaylistsBase(
        onRowClick = { playlist -> selectedPlaylistId = playlist.id },
        showDetailPane = true,
        selectedPlaylistId = selectedPlaylistId,
        onClearSelection = { selectedPlaylistId = null },
        // The pane's "tap to play" closes the visual gap: the route
        // doesn't navigate to a player, so the in-place handler just
        // delegates to the parent's route callback (which DOES
        // navigate to Now Playing in the existing flow).
        onMediaItemClick = { /* no-op; pane plays through VM */ },
        modifier = modifier,
        viewModel = viewModel,
    )
}

/**
 * The shared body of the Playlists screen, used by both the
 * single-pane and the two-pane variants. The two-pane case renders
 * [PlaylistDetailContent] in a second column when a row is
 * selected; the single-pane case omits the column and the row
 * click goes straight to the navigation callback.
 */
@Composable
private fun PlaylistsBase(
    onRowClick: (Playlist) -> Unit,
    showDetailPane: Boolean,
    selectedPlaylistId: Long?,
    onClearSelection: () -> Unit,
    onMediaItemClick: (Long) -> Unit,
    modifier: Modifier,
    viewModel: PlaylistsViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importMessage by viewModel.importMessage.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    val importSnackbar = remember { SnackbarHostState() }
    val importSuccess = stringResource(R.string.playlists_import_done)
    val importFailure = stringResource(R.string.playlists_import_failed)
    LaunchedEffect(importMessage) {
        importMessage?.let { marker ->
            val localized = when (marker) {
                PlaylistsViewModel.IMPORT_SUCCESS_MARKER -> importSuccess
                PlaylistsViewModel.IMPORT_FAILED_MARKER -> importFailure
                else -> marker
            }
            importSnackbar.showSnackbar(localized)
            viewModel.clearImportMessage()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let {
            val name = it.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Imported"
            viewModel.importM3u(it.toString(), name)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.playlists_title),
                    style = VeloxTheme.typography.headlineLarge,
                    color = VeloxColors.OnBackground,
                )
                VeloxGlassIconButton(
                    icon = Icons.Filled.FileDownload,
                    contentDescription = stringResource(R.string.playlists_import_m3u),
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                )
            }

            when (val s = state) {
                is ScreenState.Loading -> VeloxFullScreenLoading()
                is ScreenState.Error -> VeloxEmptyState(
                    icon = Icons.Filled.QueueMusic,
                    title = stringResource(R.string.playlists_error_title),
                    body = s.message ?: stringResource(R.string.playlists_empty_body),
                )
                is ScreenState.Empty, is ScreenState.PermissionRequired -> VeloxEmptyState(
                    icon = Icons.Filled.QueueMusic,
                    title = stringResource(R.string.playlists_empty_title),
                    body = stringResource(R.string.playlists_empty_body),
                    primaryActionLabel = stringResource(R.string.playlists_create_first),
                    onPrimaryAction = { showCreateDialog = true },
                )
                is ScreenState.Content -> {
                    if (showDetailPane) {
                        PlaylistsTwoPaneContent(
                            playlists = s.data,
                            selectedPlaylistId = selectedPlaylistId,
                            onRowClick = onRowClick,
                            onClearSelection = onClearSelection,
                            viewModel = viewModel,
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(VeloxSpacing.sm),
                        ) {
                            itemsIndexed(s.data, key = { index, item -> "${item.id}-$index" }) { _, playlist ->
                                PlaylistRow(playlist = playlist, onClick = { onRowClick(playlist) })
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = accentColor(),
            contentColor = VeloxColors.currentBackground,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(VeloxSpacing.lg),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.playlists_create))
        }

        SnackbarHost(
            hostState = importSnackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(VeloxSpacing.lg),
        )
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        val isValid = name.trim().isNotEmpty()
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.playlists_create_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.playlists_name_hint)) },
                    singleLine = true,
                    isError = !isValid && name.isNotEmpty(),
                    supportingText = if (!isValid && name.isNotEmpty()) {
                        { Text(stringResource(R.string.playlists_name_required)) }
                    } else null,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = isValid,
                    onClick = {
                        viewModel.createPlaylist(name)
                        showCreateDialog = false
                    },
                ) { Text(stringResource(R.string.playlists_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = VeloxColors.currentSurface,
            titleContentColor = VeloxColors.OnBackground,
        )
    }
}

@Composable
private fun PlaylistsTwoPaneContent(
    playlists: List<Playlist>,
    selectedPlaylistId: Long?,
    onRowClick: (Playlist) -> Unit,
    onClearSelection: () -> Unit,
    viewModel: PlaylistsViewModel,
) {
    val libraryTracks by viewModel.playlistDetailFor(
        // safe — the pane is only built when selectedPlaylistId != null,
        // but we have to pass a Long to satisfy the type; we use 0L
        // as a no-op sentinel and check first.
        playlistId = selectedPlaylistId ?: 0L,
    )
        .map { detail ->
            if (detail == null) {
                com.exapps.velox.core.common.util.ScreenState.Loading
            } else {
                com.exapps.velox.core.common.util.ScreenState.Content(detail)
            }
        }
        .stateIn(
            scope = rememberCoroutineScope(),
            started = SharingStarted.Eagerly,
            initialValue = com.exapps.velox.core.common.util.ScreenState.Loading,
        )
        .collectAsStateWithLifecycle()

    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight().weight(1f),
            contentPadding = PaddingValues(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(VeloxSpacing.sm),
        ) {
            itemsIndexed(playlists, key = { index, item -> "${item.id}-$index" }) { _, playlist ->
                PlaylistRow(
                    playlist = playlist,
                    selected = playlist.id == selectedPlaylistId,
                    onClick = { onRowClick(playlist) },
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(glassOutlineColor()),
        )
        Box(modifier = Modifier.fillMaxHeight().weight(1.4f)) {
            if (selectedPlaylistId == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.playlists_two_pane_hint),
                        style = VeloxTheme.typography.bodyLarge,
                        color = VeloxColors.OnSurfaceVariant,
                    )
                }
            } else {
                val state = libraryTracks
                val detail = (state as? com.exapps.velox.core.common.util.ScreenState.Content)?.data
                PlaylistDetailContent(
                    detail = detail,
                    isSystemPlaylist = viewModel.isSystemPlaylist(selectedPlaylistId),
                    onBack = null, // pane doesn't show a back button
                    onPlayAll = { viewModel.onPlaylistPlayAll(selectedPlaylistId, shuffle = false) },
                    onShuffle = { viewModel.onPlaylistPlayAll(selectedPlaylistId, shuffle = true) },
                    onTrackClick = { track -> viewModel.onPlaylistTrackClick(selectedPlaylistId, track) },
                    onRemoveTrack = { track -> viewModel.onPlaylistRemoveTrack(selectedPlaylistId, track.id) },
                    onAddTracksRequested = { /* pane has no add-tracks flow in v1.4.0; same as route flow but deferred */ },
                    onExportRequested = null, // export is a route-only flow for now
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val (icon, label) = playlistDisplay(playlist)
    val background = if (selected) glassSurfaceColor(elevated = true) else glassSurfaceColor(elevated = false)
    ClickableGlassCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md),
            modifier = Modifier
                .fillMaxWidth()
                .background(background)
                .padding(VeloxSpacing.sm),
        ) {
            Icon(icon, contentDescription = null, tint = accentColor())
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = VeloxTheme.typography.titleMedium,
                    color = VeloxColors.OnSurface,
                    maxLines = 1,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.playlist_track_count,
                        playlist.trackCount,
                        playlist.trackCount,
                    ),
                    style = VeloxTheme.typography.bodyMedium,
                    color = VeloxColors.OnSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun playlistDisplay(playlist: Playlist): Pair<androidx.compose.ui.graphics.vector.ImageVector, String> {
    return when (playlist.type) {
        PlaylistType.FAVOURITES -> Icons.Filled.Favorite to stringResource(R.string.playlist_favorites)
        PlaylistType.RECENTLY_PLAYED -> Icons.Filled.History to stringResource(R.string.playlist_recently_played)
        PlaylistType.MOST_PLAYED -> Icons.Filled.TrendingUp to stringResource(R.string.playlist_most_played)
        PlaylistType.USER -> Icons.Filled.QueueMusic to playlist.name.ifEmpty {
            stringResource(R.string.playlist_fallback_name)
        }
    }
}

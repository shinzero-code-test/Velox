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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxShapes
import com.exapps.velox.core.ui.theme.VeloxSpacing
import com.exapps.velox.core.ui.theme.VeloxTheme
import com.exapps.velox.core.ui.theme.accentColor
import com.exapps.velox.core.ui.theme.glassSurfaceColor

/** SCREEN_PLAYLISTS.md §2/§5 — system + user playlists, "+" to create, M3U import. */
@Composable
fun PlaylistsScreen(
    onPlaylistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importMessage by viewModel.importMessage.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    // M4 (features review): surface the import outcome via a snackbar driven
    // by the opaque markers in [PlaylistsViewModel.importMessage].
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
                // M3 (features review): the error state used the same title
                // as the empty-playlists state ("No playlists yet") which
                // was misleading — a DB error isn't the same as a fresh
                // install. Show a dedicated error title.
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
                is ScreenState.Content -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(VeloxSpacing.sm),
                ) {
                    itemsIndexed(s.data, key = { index, item -> "${item.id}-$index" }) { _, playlist ->
                        PlaylistRow(playlist = playlist, onClick = { onPlaylistClick(playlist.id) })
                    }
                }
            }
        }

        // Create FAB (§5.1: tap "+" → name dialog). Position follows layout
        // direction automatically (start side mirrors in RTL).
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

        // M4: snackbar host layered over the screen so M3U import outcomes
        // surface without hijacking the create-name dialog.
        SnackbarHost(
            hostState = importSnackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(VeloxSpacing.lg),
        )
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        // M3 (features review): the Create button used to be enabled
        // unconditionally, so tapping with an empty field silently
        // created a playlist named "" (or, with the post-v1.0.8 trim,
        // a name with only whitespace which the repository rejected
        // with no UI feedback). Disable the button until the trimmed
        // name is non-blank.
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
                TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
            containerColor = VeloxColors.currentSurface,
            titleContentColor = VeloxColors.OnBackground,
        )
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (icon, label) = playlistDisplay(playlist)
    ClickableGlassCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(VeloxShapes.sm)
                    .background(glassSurfaceColor(elevated = true)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accentColor())
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = VeloxTheme.typography.titleLarge, color = VeloxColors.OnSurface)
                // String parity: playlist_track_count is a plural in both
                // locales (was a plain "%1$d tracks" string in en; switched
                // to pluralStringResource for the 0/1/n cases).
                Text(
                    text = pluralStringResource(R.plurals.playlist_track_count, playlist.trackCount, playlist.trackCount),
                    style = VeloxTheme.typography.bodyMedium,
                    color = VeloxColors.OnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun playlistDisplay(playlist: Playlist): Pair<ImageVector, String> = when (playlist.type) {
    PlaylistType.FAVORITES -> Icons.Filled.Favorite to stringResource(R.string.playlist_favorites)
    PlaylistType.RECENTLY_PLAYED -> Icons.Filled.History to stringResource(R.string.playlist_recently_played)
    PlaylistType.MOST_PLAYED -> Icons.Filled.TrendingUp to stringResource(R.string.playlist_most_played)
    PlaylistType.RECENTLY_ADDED -> Icons.Filled.LibraryMusic to playlist.name
    PlaylistType.USER -> Icons.Filled.QueueMusic to playlist.name
}

package com.exapps.velox.feature.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exapps.velox.core.common.util.ScreenState
import com.exapps.velox.core.domain.model.Album
import com.exapps.velox.core.domain.model.Artist
import com.exapps.velox.core.domain.model.Folder
import com.exapps.velox.core.domain.model.Genre
import com.exapps.velox.core.domain.model.LibraryGroup
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.model.SortOrder
import com.exapps.velox.core.ui.components.VeloxEmptyState
import com.exapps.velox.core.ui.components.VeloxErrorRow
import com.exapps.velox.core.ui.components.VeloxFullScreenLoading
import com.exapps.velox.core.ui.components.VeloxGlassIconButton
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxSpacing
import com.exapps.velox.core.ui.theme.VeloxTheme
import com.exapps.velox.core.ui.theme.accentColor

/**
 * SCREEN_HOME_LIBRARY.md. This is the screen the master prompt's Phase 0 exit
 * criteria points at: "shows a glass-themed shell, and can play a local file."
 * Every state on this screen (permission / empty / loading / error / content)
 * is real — nothing here is mocked.
 */
@Composable
fun LibraryScreen(
    onMediaItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onFolderClick: (Folder) -> Unit = {},
    onGenreClick: (Genre) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionsToRequest = rememberMediaPermissions()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> viewModel.onMediaPermissionResult(results.values.any { it }) }

    // Check on first composition too — covers the case where permission was
    // already granted in a previous session (no dialog should flash on launch).
    LaunchedEffect(Unit) {
        val alreadyGranted = permissionsToRequest.any { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        viewModel.onMediaPermissionResult(alreadyGranted)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.library_title),
                style = VeloxTheme.typography.headlineLarge,
                color = VeloxColors.OnBackground,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.xs)) {
                SortMenuButton(sortOrder = sortOrder, onSortSelected = viewModel::onSortSelected)
                if (!isScanning) {
                    VeloxGlassIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.library_rescan),
                        onClick = viewModel::rescan,
                    )
                }
            }
        }

        LibraryTabRow(selected = selectedTab, onSelect = viewModel::onTabSelected)

        when (val state = content) {
            is ScreenState.Loading -> VeloxFullScreenLoading()

            is ScreenState.PermissionRequired -> VeloxEmptyState(
                icon = Icons.Filled.LockOpen,
                title = stringResource(R.string.library_permission_title),
                body = stringResource(R.string.library_permission_body),
                primaryActionLabel = stringResource(R.string.library_permission_action),
                onPrimaryAction = { permissionLauncher.launch(permissionsToRequest) },
            )

            is ScreenState.Empty -> VeloxEmptyState(
                icon = Icons.Filled.LibraryMusic,
                title = stringResource(R.string.library_empty_title),
                body = stringResource(R.string.library_empty_body),
                primaryActionLabel = stringResource(R.string.library_empty_action),
                onPrimaryAction = viewModel::rescan,
            )

            is ScreenState.Error -> VeloxErrorRow(
                message = state.message ?: stringResource(R.string.library_error_body),
                retryLabel = stringResource(R.string.library_error_retry),
                onRetry = viewModel::rescan,
            )

            is ScreenState.Content -> LibraryContentView(
                content = state.data,
                onTrackClick = { track, queue ->
                    viewModel.onTrackClick(track, queue)
                    // SCREEN_VIDEO_PLAYER.md §11: videos get the fullscreen video
                    // chrome, audio expands the Now Playing sheet — the nav layer
                    // decides from the item's type, not the tab it came from.
                    onMediaItemClick(track)
                },
                onToggleFavorite = viewModel::onToggleFavorite,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onFolderClick = onFolderClick,
                onGenreClick = onGenreClick,
            )
        }
    }
}

/** ROADMAP M2 "Search & sort" — title / date added / duration / size / path. */
@Composable
private fun SortMenuButton(sortOrder: SortOrder, onSortSelected: (SortOrder) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        VeloxGlassIconButton(
            icon = Icons.Filled.Sort,
            contentDescription = stringResource(R.string.library_sort),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = sortLabel(order),
                            color = if (order == sortOrder) accentColor() else VeloxColors.OnSurface,
                        )
                    },
                    onClick = {
                        onSortSelected(order)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun sortLabel(order: SortOrder): String = stringResource(
    when (order) {
        SortOrder.TITLE -> R.string.library_sort_title
        SortOrder.DATE_ADDED -> R.string.library_sort_date_added
        SortOrder.DURATION -> R.string.library_sort_duration
        SortOrder.SIZE -> R.string.library_sort_size
        SortOrder.PATH -> R.string.library_sort_path
    },
)

@Composable
private fun LibraryTabRow(selected: LibraryGroup, onSelect: (LibraryGroup) -> Unit, modifier: Modifier = Modifier) {
    val tabs = listOf(
        LibraryGroup.TRACKS to R.string.library_tab_tracks,
        LibraryGroup.ALBUMS to R.string.library_tab_albums,
        LibraryGroup.ARTISTS to R.string.library_tab_artists,
        LibraryGroup.FOLDERS to R.string.library_tab_folders,
        LibraryGroup.GENRES to R.string.library_tab_genres,
        LibraryGroup.VIDEOS to R.string.library_tab_videos,
    )
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.xs),
    ) {
        items(tabs.size) { index ->
            val (group, labelRes) = tabs[index]
            LibraryTabChip(
                label = stringResource(labelRes),
                selected = group == selected,
                onClick = { onSelect(group) },
            )
        }
    }
}

@Composable
private fun rememberMediaPermissions(): Array<String> = remember {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

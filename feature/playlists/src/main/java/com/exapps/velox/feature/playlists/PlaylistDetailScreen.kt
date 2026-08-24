package com.exapps.velox.feature.playlists

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.exapps.velox.core.common.util.formatDuration
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.ui.components.ClickableGlassCard
import com.exapps.velox.core.ui.components.VeloxFullScreenLoading
import com.exapps.velox.core.ui.components.VeloxGlassIconButton
import com.exapps.velox.core.ui.components.VeloxPrimaryButton
import com.exapps.velox.core.ui.components.VeloxSecondaryButton
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxShapes
import com.exapps.velox.core.ui.theme.VeloxSpacing
import com.exapps.velox.core.ui.theme.VeloxTheme
import com.exapps.velox.core.ui.theme.accentColor

/** SCREEN_PLAYLISTS.md §6 — header with totals, Play All / Shuffle, track rows with
 * remove, and the add-tracks picker for (empty or not) user playlists. */
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onMediaItemClick: (com.exapps.velox.core.domain.model.MediaItem) -> Unit = {},
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val libraryTracks by viewModel.libraryTracks.collectAsStateWithLifecycle()
    var showAddTracksSheet by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl"),
    ) { uri ->
        uri?.let { viewModel.onExportM3u(it.toString()) }
    }

    val current = detail
    if (current == null) {
        VeloxFullScreenLoading(modifier.fillMaxSize())
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header (§6): back, name, export
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VeloxSpacing.md, vertical = VeloxSpacing.sm),
        ) {
            VeloxGlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onBack,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = current.playlist.name.ifEmpty { stringResource(R.string.playlist_fallback_name) },
                    style = VeloxTheme.typography.headlineMedium,
                    color = VeloxColors.OnBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.playlist_header_subtitle,
                        current.playlist.trackCount,
                        formatDuration(current.playlist.totalDurationMs),
                    ),
                    style = VeloxTheme.typography.bodyMedium,
                    color = VeloxColors.OnSurfaceVariant,
                )
            }
            if (!viewModel.isSystemPlaylist) {
                VeloxGlassIconButton(
                    icon = Icons.Filled.FileUpload,
                    contentDescription = stringResource(R.string.playlists_export_m3u),
                    onClick = { exportLauncher.launch(current.playlist.name.ifEmpty { "playlist" }) },
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.sm),
        ) {
            VeloxPrimaryButton(
                text = stringResource(R.string.playlist_play_all),
                onClick = {
                    viewModel.onPlayAll(shuffle = false)
                    current.tracks.firstOrNull()?.let(onMediaItemClick)
                },
                modifier = Modifier.weight(1f),
            )
            VeloxSecondaryButton(
                text = stringResource(R.string.playlist_shuffle),
                onClick = {
                    viewModel.onPlayAll(shuffle = true)
                    current.tracks.firstOrNull()?.let(onMediaItemClick)
                },
                modifier = Modifier.weight(1f),
            )
        }

        if (current.tracks.isEmpty()) {
            // §7 empty states: user playlists get "add songs", system ones explain themselves.
            Box(modifier = Modifier.fillMaxSize().padding(VeloxSpacing.xxl), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = VeloxColors.OnSurfaceVariant, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(VeloxSpacing.md))
                    Text(
                        text = stringResource(
                            if (viewModel.isSystemPlaylist) R.string.playlist_system_empty_body
                            else R.string.playlist_empty_body,
                        ),
                        style = VeloxTheme.typography.bodyMedium,
                        color = VeloxColors.OnSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(VeloxSpacing.xxs),
            ) {
                items(current.tracks, key = { it.id }) { track ->
                    PlaylistTrackRow(
                        track = track,
                        onClick = {
                            viewModel.onTrackClick(track)
                            onMediaItemClick(track)
                        },
                        onRemove = if (viewModel.isSystemPlaylist) null else ({ viewModel.onRemoveTrack(track.id) }),
                    )
                }
                if (!viewModel.isSystemPlaylist) {
                    item {
                        Text(
                            text = stringResource(R.string.playlist_add_tracks),
                            style = VeloxTheme.typography.labelLarge,
                            color = accentColor(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(VeloxShapes.md)
                                .clickable { showAddTracksSheet = true }
                                .padding(VeloxSpacing.md),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
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
}

@Composable
private fun PlaylistTrackRow(
    track: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
) {
    ClickableGlassCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md)) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(VeloxShapes.sm),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = VeloxTheme.typography.titleMedium,
                    color = VeloxColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artistName ?: "",
                    style = VeloxTheme.typography.bodyMedium,
                    color = VeloxColors.OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatDuration(track.durationMs),
                style = VeloxTheme.typography.labelMedium,
                color = VeloxColors.OnSurfaceVariant,
            )
            if (onRemove != null) {
                VeloxGlassIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.playlist_remove_track),
                    onClick = onRemove,
                    size = 36.dp,
                    iconSize = 18.dp,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTracksSheet(
    allTracks: List<MediaItem>,
    onAdd: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember { mutableStateOf(setOf<Long>()) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = VeloxColors.currentSurface) {
        Column(Modifier.padding(horizontal = VeloxSpacing.lg)) {
            Text(
                text = stringResource(R.string.playlist_add_tracks),
                style = VeloxTheme.typography.headlineMedium,
                color = VeloxColors.OnBackground,
                modifier = Modifier.padding(bottom = VeloxSpacing.sm),
            )
            LazyColumn(modifier = Modifier.height(420.dp)) {
                items(allTracks, key = { it.id }) { track ->
                    val isSelected = track.id in selected.value
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(VeloxShapes.md)
                            .clickable {
                                selected.value = if (isSelected) {
                                    selected.value - track.id
                                } else {
                                    selected.value + track.id
                                }
                            }
                            .padding(VeloxSpacing.sm),
                    ) {
                        Text(
                            text = track.title,
                            style = VeloxTheme.typography.titleMedium,
                            color = if (isSelected) accentColor() else VeloxColors.OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.Circle,
                            contentDescription = null,
                            tint = if (isSelected) accentColor() else VeloxColors.OnSurfaceVariant.copy(alpha = 0.3f),
                        )
                    }
                }
            }
            if (selected.value.isNotEmpty()) {
                VeloxPrimaryButton(
                    text = stringResource(R.string.playlist_add_selected, selected.value.size),
                    onClick = { onAdd(selected.value.toList()) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = VeloxSpacing.md),
                )
            }
            Spacer(Modifier.height(VeloxSpacing.lg))
        }
    }
}

package com.exapps.velox.feature.playlists

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.exapps.velox.core.common.util.formatDuration
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.domain.model.PlaylistDetail
import com.exapps.velox.core.ui.components.ClickableGlassCard
import com.exapps.velox.core.ui.components.VeloxGlassIconButton
import com.exapps.velox.core.ui.components.VeloxPrimaryButton
import com.exapps.velox.core.ui.components.VeloxSecondaryButton
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxShapes
import com.exapps.velox.core.ui.theme.VeloxSpacing
import com.exapps.velox.core.ui.theme.VeloxTheme
import com.exapps.velox.core.ui.theme.accentColor

/**
 * Phase 3 / Round 1 — Playlists two-pane. The body of the existing
 * [PlaylistDetailScreen] extracted as a stateless composable that
 * takes the resolved [PlaylistDetail] (or null while loading) and
 * a handful of callbacks. Both the route (`PlaylistDetailScreen`)
 * and the in-place pane in [PlaylistsScreen] re-use this; the
 * route keeps its own Hilt VM, the pane reads from the parent's
 * `PlaylistsViewModel.playlistDetailFor(id)` flow.
 *
 * The `onAddTracksRequested` callback is fired by the trailing
 * "add tracks" button. The actual `AddTracksSheet` is provided by
 * the caller — the route uses the same one the parent already had,
 * and the pane in [PlaylistsScreen] can show it inline in the
 * detail column.
 */
@Composable
fun PlaylistDetailContent(
    detail: PlaylistDetail?,
    isSystemPlaylist: Boolean,
    onBack: (() -> Unit)?,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onTrackClick: (MediaItem) -> Unit,
    onRemoveTrack: ((MediaItem) -> Unit)?,
    onAddTracksRequested: () -> Unit,
    onExportRequested: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val current = detail
    Column(modifier = modifier.fillMaxSize()) {
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            return
        }

        // Header (§6): back (optional — pane doesn't show a back button), name, export.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VeloxSpacing.md, vertical = VeloxSpacing.sm),
        ) {
            if (onBack != null) {
                VeloxGlassIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    onClick = onBack,
                )
            }
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
            if (onExportRequested != null && !isSystemPlaylist) {
                VeloxGlassIconButton(
                    icon = Icons.Filled.FileUpload,
                    contentDescription = stringResource(R.string.playlists_export_m3u),
                    onClick = onExportRequested,
                )
            }
        }

        if (current.tracks.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.sm),
            ) {
                VeloxPrimaryButton(
                    text = stringResource(R.string.playlist_play_all),
                    onClick = onPlayAll,
                    modifier = Modifier.weight(1f),
                )
                VeloxSecondaryButton(
                    text = stringResource(R.string.playlist_shuffle),
                    onClick = onShuffle,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (current.tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(VeloxSpacing.xxl), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.LibraryMusic,
                        contentDescription = null,
                        tint = VeloxColors.OnSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(VeloxSpacing.md))
                    Text(
                        text = stringResource(
                            if (isSystemPlaylist) R.string.playlist_system_empty_body
                            else R.string.playlist_empty_body,
                        ),
                        style = VeloxTheme.typography.bodyMedium,
                        color = VeloxColors.OnSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(VeloxSpacing.xxs),
            ) {
                itemsIndexed(current.tracks, key = { index, item -> "${item.id}-$index" }) { _, track ->
                    PlaylistTrackRow(
                        track = track,
                        onClick = { onTrackClick(track) },
                        onRemove = if (onRemoveTrack != null && !isSystemPlaylist) {
                            { onRemoveTrack(track) }
                        } else null,
                    )
                }
                if (!isSystemPlaylist) {
                    item {
                        Text(
                            text = stringResource(R.string.playlist_add_tracks),
                            style = VeloxTheme.typography.labelLarge,
                            color = accentColor(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(VeloxShapes.md)
                                .clickable { onAddTracksRequested() }
                                .padding(VeloxSpacing.md),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
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

/**
 * The "add tracks" bottom sheet used by both the route and the
 * in-place pane. Kept as a package-private helper so the route
 * screen can continue to call it directly, and the pane in
 * [PlaylistsScreen] can also reuse the same instance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTracksSheet(
    allTracks: List<MediaItem>,
    onAdd: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember { mutableStateOf(setOf<Long>()) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = VeloxColors.currentSurface) {
        Column(
            modifier = Modifier
                .padding(horizontal = VeloxSpacing.lg)
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.8f)
                .fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.playlist_add_tracks),
                style = VeloxTheme.typography.headlineMedium,
                color = VeloxColors.OnBackground,
                modifier = Modifier.padding(bottom = VeloxSpacing.sm),
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(allTracks, key = { index, item -> "${item.id}-$index" }) { _, track ->
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

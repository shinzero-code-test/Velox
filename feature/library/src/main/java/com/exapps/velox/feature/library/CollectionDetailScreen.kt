package com.exapps.velox.feature.library

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exapps.velox.core.common.util.ScreenState
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.ui.components.ClickableGlassCard
import com.exapps.velox.core.ui.components.VeloxEmptyState
import com.exapps.velox.core.ui.components.VeloxGlassIconButton
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxSpacing
import com.exapps.velox.core.ui.theme.VeloxTheme
import com.exapps.velox.core.ui.theme.accentColor

/**
 * Track list behind one Library grouping — an album, an artist, or a folder.
 * Tapping a row starts playback with the whole collection as the queue; the heart
 * toggles favourite exactly like the Library list rows (SCREEN_HOME_LIBRARY.md).
 */
@Composable
fun CollectionDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onMediaItemClick: (MediaItem) -> Unit = {},
    viewModel: CollectionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.tracks.collectAsStateWithLifecycle()
    val tracks: List<MediaItem> = (state as? ScreenState.Content)?.data.orEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VeloxGlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.collection_back),
                onClick = onBack,
            )
            Column {
                Text(
                    text = viewModel.title,
                    style = VeloxTheme.typography.headlineMedium,
                    color = VeloxColors.OnBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // M3 (features review): the count subtitle now reflects the
                // currently-rendered row count, not the still-loading total
                // (0 during loading was misleading).
                Text(
                    text = pluralStringResource(R.plurals.collection_track_count, tracks.size, tracks.size),
                    style = VeloxTheme.typography.bodyMedium,
                    color = VeloxColors.OnSurfaceVariant,
                )
            }
        }

        when (state) {
            ScreenState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            is ScreenState.Content -> if (tracks.isEmpty()) {
                VeloxEmptyState(
                    icon = Icons.Filled.LibraryMusic,
                    title = stringResource(R.string.library_empty_title),
                    body = stringResource(R.string.collection_empty_body),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(VeloxSpacing.xs),
                ) {
                    itemsIndexed(tracks, key = { index, item -> "${item.id}-$index" }) { _, track ->
                        CollectionTrackRow(
                            track = track,
                            onClick = {
                                viewModel.onTrackClick(track)
                                // Same contract as the Library list: audio expands the
                                // Now Playing sheet, video opens the fullscreen player.
                                onMediaItemClick(track)
                            },
                            onFavoriteClick = { viewModel.onToggleFavorite(track) },
                        )
                    }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun CollectionTrackRow(
    track: MediaItem,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClickableGlassCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md),
            modifier = Modifier.padding(end = VeloxSpacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
            // 40dp clickable box around the icon — accessibility-minimum tap target
            // (same pattern as LibraryComponents.TrackRow).
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onFavoriteClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (track.isFavorite) accentColor() else VeloxColors.OnSurfaceVariant,
                )
            }
        }
    }
}

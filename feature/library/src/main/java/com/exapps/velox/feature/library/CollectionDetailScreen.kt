package com.exapps.velox.feature.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.exapps.velox.core.domain.model.MediaItem
import com.exapps.velox.core.ui.components.VeloxGlassIconButton
import com.exapps.velox.core.ui.theme.VeloxSpacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding

/**
 * Track list behind one Library grouping — an album, an artist, or a folder.
 * Tapping a row starts playback with the whole collection as the queue; the heart
 * toggles favourite exactly like the Library list rows (SCREEN_HOME_LIBRARY.md).
 *
 * Phase 3 / Milestone 3 completion: this screen is now a thin route-shell
 * that delegates to [CollectionDetailContent]. The body composable is the
 * one that gets re-used by the in-place list-detail pane in
 * [LibraryScreen] on tablets.
 */
@Composable
fun CollectionDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onMediaItemClick: (MediaItem) -> Unit = {},
    viewModel: CollectionDetailViewModel = hiltViewModel(),
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md),
        ) {
            VeloxGlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.collection_back),
                onClick = onBack,
            )
        }
        CollectionDetailContent(
            title = viewModel.title,
            tracks = viewModel.tracks,
            onTrackClick = { track ->
                viewModel.onTrackClick(track)
                onMediaItemClick(track)
            },
            onToggleFavorite = viewModel::onToggleFavorite,
        )
    }
}

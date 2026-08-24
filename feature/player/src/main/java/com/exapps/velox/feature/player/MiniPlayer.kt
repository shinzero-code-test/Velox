package com.exapps.velox.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.exapps.velox.core.domain.player.PlaybackStatus
import com.exapps.velox.core.ui.components.ClickableGlassCard
import com.exapps.velox.core.ui.components.VeloxGlassIconButton
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxShapes
import com.exapps.velox.core.ui.theme.VeloxSpacing
import com.exapps.velox.core.ui.theme.VeloxTheme

/**
 * SCREEN_PATTERNS.md §1 (Mini Player): floating glass pill, artwork + title/artist +
 * play/pause + next, tapping it (outside the controls) opens Now Playing. Hidden
 * entirely — not just collapsed — when nothing is loaded, which is why this is an
 * `AnimatedVisibility` around the whole thing rather than an empty-state branch.
 */
@Composable
fun MiniPlayer(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MiniPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val visible = state.currentItem != null && state.status != PlaybackStatus.IDLE

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = modifier,
    ) {
        val item = state.currentItem
        ClickableGlassCard(
            onClick = onExpand,
            shape = VeloxShapes.full,
            elevated = true,
            contentPadding = PaddingValues(horizontal = VeloxSpacing.sm, vertical = VeloxSpacing.xs),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VeloxSpacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.sm),
                modifier = Modifier.height(56.dp),
            ) {
                AsyncImage(
                    model = item?.artworkUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(VeloxShapes.sm),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item?.title.orEmpty(),
                        style = VeloxTheme.typography.titleMedium,
                        color = VeloxColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item?.artistName?.let {
                        Text(
                            text = it,
                            style = VeloxTheme.typography.bodyMedium,
                            color = VeloxColors.OnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                VeloxGlassIconButton(
                    icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                    onClick = viewModel::onPlayPause,
                    size = 40.dp,
                )
                VeloxGlassIconButton(
                    icon = Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.cd_next),
                    onClick = viewModel::onSkipNext,
                    size = 40.dp,
                )
            }
        }
    }
}

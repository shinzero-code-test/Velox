package com.exapps.velox.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.exapps.velox.core.common.util.formatDuration
import com.exapps.velox.core.common.util.formatRemaining
import com.exapps.velox.core.domain.player.RepeatMode
import com.exapps.velox.core.ui.components.GlassCard
import com.exapps.velox.core.ui.components.VeloxGlassIconButton
import com.exapps.velox.core.ui.components.VeloxPlayPauseButton
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxShapes
import com.exapps.velox.core.ui.theme.VeloxSpacing
import com.exapps.velox.core.ui.theme.VeloxTheme
import com.exapps.velox.core.ui.theme.accentColor

@Composable
fun NowPlayingScreen(
    onCollapse: () -> Unit,
    onOpenEqualizer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimer.collectAsStateWithLifecycle()
    val item = state.currentItem

    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = VeloxSpacing.lg),
    ) {
        // Top bar: collapse + overflow (SCREEN_NOW_PLAYING.md §3)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = VeloxSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VeloxGlassIconButton(Icons.Filled.KeyboardArrowDown, stringResource(R.string.cd_collapse), onCollapse)
            Text(
                text = stringResource(R.string.now_playing_title),
                style = VeloxTheme.typography.titleMedium,
                color = VeloxColors.OnSurfaceVariant,
            )
            if (sleepTimer != SleepTimerOption.OFF) {
                VeloxGlassIconButton(
                    icon = Icons.Filled.Timer,
                    contentDescription = stringResource(R.string.cd_sleep_timer),
                    onClick = { showSleepTimerSheet = true },
                    tint = accentColor(),
                )
            } else {
                Spacer(Modifier.height(48.dp))
            }
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(VeloxSpacing.xl))

            // Hero artwork (§4) — large, rounded, shared-element capable target
            // once navigation animations land (Phase 1 polish pass).
            AsyncImage(
                model = item?.artworkUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(VeloxShapes.xl),
            )

            Spacer(Modifier.height(VeloxSpacing.xxl))

            Text(
                text = item?.title ?: "—",
                style = VeloxTheme.typography.displayLarge,
                color = VeloxColors.OnBackground,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(VeloxSpacing.xxs))
            Text(
                text = item?.artistName ?: item?.albumTitle.orEmpty(),
                style = VeloxTheme.typography.titleMedium,
                color = VeloxColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(VeloxSpacing.xl))

            // Progress (§5) — local drag state, single seek on release: seeking on
            // every drag frame would flood the session controller with IPC.
            // LTR per §11's documented decision.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                var scrubbing by remember { mutableStateOf(false) }
                var scrubPositionMs by remember { mutableStateOf(0f) }
                val duration = state.durationMs.toFloat().coerceAtLeast(1f)
                val position = (if (scrubbing) scrubPositionMs else state.positionMs.toFloat())
                    .coerceIn(0f, duration)
                Slider(
                    value = position,
                    onValueChange = {
                        scrubbing = true
                        scrubPositionMs = it
                    },
                    onValueChangeFinished = {
                        viewModel.onSeek(scrubPositionMs.toLong())
                        scrubbing = false
                    },
                    valueRange = 0f..duration,
                    colors = SliderDefaults.colors(
                        thumbColor = VeloxColors.OnBackground,
                        activeTrackColor = accentColor(),
                        inactiveTrackColor = VeloxColors.OnSurfaceVariant.copy(alpha = 0.24f),
                    ),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration(position.toLong()), style = VeloxTheme.typography.labelMedium, color = VeloxColors.OnSurfaceVariant)
                    Text(formatRemaining(state.durationMs - position.toLong()), style = VeloxTheme.typography.labelMedium, color = VeloxColors.OnSurfaceVariant)
                }
            }

            Spacer(Modifier.height(VeloxSpacing.lg))

            // Transport row (§6)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VeloxGlassIconButton(
                    icon = Icons.Filled.Shuffle,
                    contentDescription = stringResource(R.string.cd_shuffle),
                    onClick = viewModel::onToggleShuffle,
                    tint = if (state.shuffleEnabled) accentColor() else VeloxColors.OnSurface,
                )
                VeloxGlassIconButton(Icons.Filled.SkipPrevious, stringResource(R.string.cd_previous), viewModel::onSkipPrevious, size = 56.dp, iconSize = 28.dp)
                VeloxPlayPauseButton(
                    icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                    onClick = viewModel::onPlayPause,
                )
                VeloxGlassIconButton(Icons.Filled.SkipNext, stringResource(R.string.cd_next), viewModel::onSkipNext, size = 56.dp, iconSize = 28.dp)
                VeloxGlassIconButton(
                    icon = if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = stringResource(R.string.cd_repeat),
                    onClick = viewModel::onCycleRepeat,
                    tint = if (state.repeatMode != RepeatMode.OFF) accentColor() else VeloxColors.OnSurface,
                )
            }

            Spacer(Modifier.height(VeloxSpacing.xl))

            // Secondary actions (§7): sleep timer, EQ, queue, favorite
            GlassCard(shape = VeloxShapes.full, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    VeloxGlassIconButton(
                        icon = Icons.Filled.Timer,
                        contentDescription = stringResource(R.string.cd_sleep_timer),
                        onClick = { showSleepTimerSheet = true },
                        tint = if (sleepTimer != SleepTimerOption.OFF) accentColor() else VeloxColors.OnSurface,
                    )
                    VeloxGlassIconButton(Icons.Filled.Equalizer, stringResource(R.string.cd_equalizer), onOpenEqualizer)
                    VeloxGlassIconButton(Icons.Filled.QueueMusic, stringResource(R.string.cd_queue), { showQueueSheet = true })
                    VeloxGlassIconButton(
                        icon = if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(R.string.cd_favorite),
                        onClick = viewModel::onFavoriteToggle,
                        tint = if (state.isFavorite) accentColor() else VeloxColors.OnSurface,
                    )
                }
            }

            Spacer(Modifier.height(VeloxSpacing.lg))
        }
    }

    if (showQueueSheet) {
        QueueSheet(
            state = state,
            onItemClick = viewModel::onQueueItemClick,
            onItemRemove = viewModel::onQueueItemRemove,
            onClear = viewModel::onQueueClear,
            onDismiss = { showQueueSheet = false },
        )
    }

    if (showSleepTimerSheet) {
        SleepTimerSheet(
            current = sleepTimer,
            onSelect = {
                viewModel.setSleepTimer(it)
                showSleepTimerSheet = false
            },
            onDismiss = { showSleepTimerSheet = false },
        )
    }
}

/** SCREEN_NOW_PLAYING.md §9 — current item highlighted, remove per row, clear all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(
    state: com.exapps.velox.core.domain.player.PlaybackState,
    onItemClick: (Int) -> Unit,
    onItemRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = VeloxColors.surface) {
        Column(Modifier.padding(horizontal = VeloxSpacing.lg)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.queue_title),
                    style = VeloxTheme.typography.headlineMedium,
                    color = VeloxColors.OnBackground,
                )
                Text(
                    text = stringResource(R.string.queue_clear),
                    style = VeloxTheme.typography.labelLarge,
                    color = VeloxColors.Error,
                    modifier = Modifier
                        .clip(VeloxShapes.sm)
                        .clickable(onClick = onClear)
                        .padding(VeloxSpacing.sm),
                )
            }
            LazyColumn(modifier = Modifier.height(420.dp).padding(bottom = VeloxSpacing.xl)) {
                itemsIndexed(state.queue, key = { index, item -> "${item.id}-$index" }) { index, mediaItem ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(VeloxShapes.md)
                            .padding(VeloxSpacing.xs),
                    ) {
                        val isCurrent = index == state.currentIndex
                        if (isCurrent) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = accentColor())
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(VeloxShapes.md)
                                .clickable { onItemClick(index) }
                                .padding(VeloxSpacing.sm),
                        ) {
                            Text(
                                text = mediaItem.title,
                                style = VeloxTheme.typography.titleMedium,
                                color = if (isCurrent) accentColor() else VeloxColors.OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            mediaItem.artistName?.let {
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
                            icon = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_remove_from_queue),
                            onClick = { onItemRemove(index) },
                            size = 36.dp,
                            iconSize = 18.dp,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(
    current: SleepTimerOption,
    onSelect: (SleepTimerOption) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = VeloxColors.surface) {
        Column(Modifier.padding(horizontal = VeloxSpacing.lg)) {
            Text(
                text = stringResource(R.string.sleep_timer_title),
                style = VeloxTheme.typography.headlineMedium,
                color = VeloxColors.OnBackground,
                modifier = Modifier.padding(bottom = VeloxSpacing.md),
            )
            SleepTimerOption.entries.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(VeloxShapes.md)
                        .clickable { onSelect(option) }
                        .padding(vertical = VeloxSpacing.sm),
                ) {
                    Text(
                        text = sleepTimerLabel(option),
                        style = VeloxTheme.typography.titleMedium,
                        color = if (option == current) accentColor() else VeloxColors.OnSurface,
                    )
                    if (option == current) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = accentColor())
                    }
                }
            }
            Spacer(Modifier.height(VeloxSpacing.xxl))
        }
    }
}

@Composable
private fun sleepTimerLabel(option: SleepTimerOption): String = stringResource(
    when (option) {
        SleepTimerOption.OFF -> R.string.sleep_timer_off
        SleepTimerOption.END_OF_TRACK -> R.string.sleep_timer_end_of_track
        SleepTimerOption.MINUTES_15 -> R.string.sleep_timer_15
        SleepTimerOption.MINUTES_30 -> R.string.sleep_timer_30
        SleepTimerOption.MINUTES_60 -> R.string.sleep_timer_60
    },
)

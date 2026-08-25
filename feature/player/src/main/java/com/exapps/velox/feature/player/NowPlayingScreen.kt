package com.exapps.velox.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
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
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val activeLyricIndex by viewModel.activeLyricIndex.collectAsStateWithLifecycle()
    var showLyrics by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showMarkersSheet by remember { mutableStateOf(false) }
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimer.collectAsStateWithLifecycle()
    val item = state.currentItem

    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }

    // Phase 2 "Foldable / large screen optimizations": on expanded widths the
    // column caps its width and centres instead of stretching edge to edge.
    val isExpandedWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 720

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = if (isExpandedWidth) 720.dp else androidx.compose.ui.unit.Dp.Unspecified)
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
                val progressDescription = stringResource(R.string.cd_progress)
                Slider(
                    modifier = Modifier.semantics { contentDescription = progressDescription },
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

            // Secondary actions (§7) — Phase 2 layout: two even rows.
            GlassCard(shape = VeloxShapes.full, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = VeloxSpacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Phase 2 A-B repeat: OFF → A → A-B loop → OFF.
                        val loopLabel = when {
                            state.loopStartMs != null && state.loopEndMs != null -> "A↔B"
                            state.loopStartMs != null -> "A…"
                            else -> "A-B"
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable(onClick = viewModel::onCycleLoopRegion),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = loopLabel,
                                style = VeloxTheme.typography.labelLarge,
                                color = if (state.loopStartMs != null) accentColor() else VeloxColors.OnSurface,
                            )
                        }
                        VeloxGlassIconButton(Icons.Filled.QueueMusic, stringResource(R.string.cd_queue), { showQueueSheet = true })
                        VeloxGlassIconButton(Icons.Filled.Equalizer, stringResource(R.string.cd_equalizer), onOpenEqualizer)
                        VeloxGlassIconButton(
                            icon = Icons.Filled.Bookmarks,
                            contentDescription = stringResource(R.string.cd_markers),
                            onClick = {
                                showMarkersSheet = true
                                state.currentItem?.let { viewModel.loadMarkersFor(it.id, it) }
                            },
                        )
                        VeloxGlassIconButton(
                            icon = Icons.Filled.Timer,
                            contentDescription = stringResource(R.string.cd_sleep_timer),
                            onClick = { showSleepTimerSheet = true },
                            tint = if (sleepTimer != SleepTimerOption.OFF) accentColor() else VeloxColors.OnSurface,
                        )
                    }
                    Spacer(Modifier.height(VeloxSpacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VeloxGlassIconButton(
                            icon = Icons.Filled.MusicNote,
                            contentDescription = stringResource(R.string.cd_lyrics),
                            onClick = { showLyrics = !showLyrics },
                            tint = if (showLyrics) accentColor() else VeloxColors.OnSurface,
                        )
                        // Playback speed for songs (videos have their own picker in the
                        // player chrome). Cycles 1x → 1.25x → 1.5x → 2x → 1x.
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable(onClick = viewModel::onCycleSpeed),
                            contentAlignment = Alignment.Center,
                        ) {
                            val speedLabel = stringResource(R.string.cd_playback_speed)
                            Text(
                                text = formatPlaybackSpeed(state.playbackSpeed),
                                style = VeloxTheme.typography.labelLarge,
                                color = if (state.playbackSpeed != 1f) accentColor() else VeloxColors.OnSurface,
                                modifier = Modifier.semantics { contentDescription = speedLabel },
                            )
                        }
                        VeloxGlassIconButton(
                            icon = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.cd_edit_info),
                            onClick = { showEditDialog = true },
                        )
                        VeloxGlassIconButton(
                            icon = if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(R.string.cd_favorite),
                            onClick = viewModel::onFavoriteToggle,
                            tint = if (state.isFavorite) accentColor() else VeloxColors.OnSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(VeloxSpacing.lg))

            // Phase 1.1 "Lyrics display (basic)": sidecar .lrc (synced highlight) or
            // .txt (plain). Hidden entirely when the track has no sidecar content.
            val currentLyrics = lyrics
            if (showLyrics && currentLyrics != null && !currentLyrics.isEmpty) {
                GlassCard(shape = VeloxShapes.full, modifier = Modifier.fillMaxWidth()) {
                    LyricsPanel(
                        lyrics = currentLyrics,
                        activeIndex = activeLyricIndex,
                    )
                }
                Spacer(Modifier.height(VeloxSpacing.lg))
            }
        }
    }

    if (showEditDialog && state.currentItem != null) {
        EditInfoDialog(
            initialTitle = state.currentItem?.title.orEmpty(),
            initialArtist = state.currentItem?.artistName.orEmpty(),
            initialAlbum = state.currentItem?.albumTitle.orEmpty(),
            onSave = { title, artist, album ->
                viewModel.onSaveTrackMetadata(state.currentItem!!.id, title, artist, album)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
        )
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

    if (showMarkersSheet) {
        MarkersSheet(
            bookmarks = bookmarks,
            chapters = chapters,
            currentPositionMs = state.positionMs,
            onAddBookmark = viewModel::onAddBookmark,
            onSeekToBookmark = viewModel::onSeekToBookmark,
            onDeleteBookmark = viewModel::onDeleteBookmark,
            onDismiss = { showMarkersSheet = false },
        )
    }

    if (showSleepTimerSheet) {
        SleepTimerSheet(
            current = sleepTimer,
            onSelect = { option, customMinutes ->
                viewModel.setSleepTimer(option, customMinutes)
                if (option != SleepTimerOption.CUSTOM || customMinutes?.let { it > 0 } == true) {
                    showSleepTimerSheet = false
                }
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = VeloxColors.currentSurface) {
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
    onSelect: (SleepTimerOption, customMinutes: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var customMinutes by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = VeloxColors.currentSurface) {
        Column(Modifier.padding(horizontal = VeloxSpacing.lg)) {
            Text(
                text = stringResource(R.string.sleep_timer_title),
                style = VeloxTheme.typography.headlineMedium,
                color = VeloxColors.OnBackground,
                modifier = Modifier.padding(bottom = VeloxSpacing.md),
            )
            SleepTimerOption.entries
                .filter { it != SleepTimerOption.CUSTOM }
                .forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(VeloxShapes.md)
                            .clickable { onSelect(option, null) }
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

            Spacer(Modifier.height(VeloxSpacing.md))

            // Phase 2: custom minutes + fade-out (fade is applied automatically in
            // the final 10s by the view model).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md)) {
                OutlinedTextField(
                    value = customMinutes,
                    onValueChange = { customMinutes = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.sleep_timer_custom)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = customMinutes.toIntOrNull()?.let { it > 0 } == true,
                    onClick = {
                        current.let { }
                        onSelect(SleepTimerOption.CUSTOM, customMinutes.toIntOrNull())
                    },
                ) {
                    Text(stringResource(R.string.action_save))
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
        SleepTimerOption.END_OF_QUEUE -> R.string.sleep_timer_end_of_queue
        SleepTimerOption.CUSTOM -> R.string.sleep_timer_custom
        SleepTimerOption.MINUTES_15 -> R.string.sleep_timer_15
        SleepTimerOption.MINUTES_30 -> R.string.sleep_timer_30
        SleepTimerOption.MINUTES_60 -> R.string.sleep_timer_60
    },
)


/** "1x" / "1.25x" — same rendering rule as the video player's speed chip. */
private fun formatPlaybackSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

/** Phase 1.1 "Lyrics display (basic)": synced lines highlight + auto-scroll; plain
 * text just scrolls. Kept deliberately simple — no karaoke wipe, no font settings. */
@Composable
private fun LyricsPanel(
    lyrics: LyricsLoader.Lyrics,
    activeIndex: Int,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && lyrics.syncedLines.isNotEmpty()) {
            listState.animateScrollToItem(index = (activeIndex - 2).coerceAtLeast(0))
        }
    }

    if (lyrics.syncedLines.isNotEmpty()) {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxWidth().padding(vertical = VeloxSpacing.md),
            contentPadding = PaddingValues(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(VeloxSpacing.xs),
        ) {
            itemsIndexed(lyrics.syncedLines) { index, line ->
                val active = index == activeIndex
                Text(
                    text = line.text.ifBlank { "♪" },
                    style = VeloxTheme.typography.bodyLarge,
                    color = if (active) accentColor() else VeloxColors.OnSurfaceVariant,
                    fontWeight = if (active) FontWeight.SemiBold else null,
                )
            }
        }
    } else {
        Text(
            text = lyrics.plainText.orEmpty(),
            style = VeloxTheme.typography.bodyLarge,
            color = VeloxColors.OnSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(VeloxSpacing.lg),
        )
    }
}

/** Phase 1.1 "Tag editor (basic)": edits the library's display metadata for the
 * current track. File tags are not rewritten (MediaStore ownership walls on
 * API 29+ would need per-file recoverable-permission flows); the repository's
 * user-metadata snapshot keeps these edits alive across rescans. */
@Composable
private fun EditInfoDialog(
    initialTitle: String,
    initialArtist: String,
    initialAlbum: String,
    onSave: (title: String, artist: String, album: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var artist by remember { mutableStateOf(initialArtist) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_info_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VeloxSpacing.md)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.edit_info_field_title)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text(stringResource(R.string.edit_info_field_artist)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = initialAlbum,
                    onValueChange = { /* album display is derived from rows; kept read-only in v1 */ },
                    label = { Text(stringResource(R.string.edit_info_field_album)) },
                    singleLine = true,
                    enabled = false,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, artist, initialAlbum) }, enabled = title.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Phase 2 "Bookmarks + Chapters": chapters are read-only sidecar markers; bookmarks
 * are user-saved points (add at current position, tap to jump, swipe-free delete). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkersSheet(
    bookmarks: List<com.exapps.velox.core.domain.model.Bookmark>,
    chapters: List<ChaptersLoader.Chapter>,
    currentPositionMs: Long,
    onAddBookmark: (Long) -> Unit,
    onSeekToBookmark: (Long) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = VeloxColors.currentSurface) {
        Column(Modifier.padding(horizontal = VeloxSpacing.lg)) {
            Text(
                text = stringResource(R.string.markers_title),
                style = VeloxTheme.typography.headlineMedium,
                color = VeloxColors.OnBackground,
                modifier = Modifier.padding(bottom = VeloxSpacing.md),
            )

            if (chapters.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.markers_chapters),
                    style = VeloxTheme.typography.labelLarge,
                    color = VeloxColors.OnSurfaceVariant,
                )
                Spacer(Modifier.height(VeloxSpacing.xs))
                chapters.forEach { chapter ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(VeloxShapes.md)
                            .clickable { onSeekToBookmark(chapter.timeMs) }
                            .padding(vertical = VeloxSpacing.sm),
                    ) {
                        Text(
                            text = formatDuration(chapter.timeMs),
                            style = VeloxTheme.typography.labelLarge,
                            color = accentColor(),
                            modifier = Modifier.width(64.dp),
                        )
                        Text(
                            text = chapter.title,
                            style = VeloxTheme.typography.bodyLarge,
                            color = VeloxColors.OnSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(VeloxSpacing.lg))
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.markers_bookmarks),
                    style = VeloxTheme.typography.labelLarge,
                    color = VeloxColors.OnSurfaceVariant,
                )
                TextButton(onClick = { onAddBookmark(currentPositionMs) }) { Text(stringResource(R.string.markers_add)) }
            }
            if (bookmarks.isEmpty()) {
                Text(stringResource(R.string.lyrics_none), style = VeloxTheme.typography.bodyMedium, color = VeloxColors.OnSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(VeloxSpacing.xs)) {
                    itemsIndexed(bookmarks, key = { _, b -> b.id }) { _, bookmark ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(VeloxShapes.md)
                                .clickable { onSeekToBookmark(bookmark.positionMs) }
                                .padding(vertical = VeloxSpacing.sm),
                        ) {
                            Text(
                                text = formatDuration(bookmark.positionMs),
                                style = VeloxTheme.typography.labelLarge,
                                color = accentColor(),
                                modifier = Modifier.width(64.dp),
                            )
                            Text(
                                text = bookmark.label,
                                style = VeloxTheme.typography.bodyLarge,
                                color = VeloxColors.OnSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onDeleteBookmark(bookmark.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel), tint = VeloxColors.OnSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(VeloxSpacing.xxl))
        }
    }
}

package com.exapps.velox.feature.player

import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exapps.velox.core.common.util.formatDuration
import com.exapps.velox.core.domain.player.PlaybackStatus
import com.exapps.velox.core.domain.player.PlayerTrack
import com.exapps.velox.core.domain.player.RepeatMode
import com.exapps.velox.core.domain.player.TrackType
import com.exapps.velox.core.ui.components.VeloxGlassIconButton
import com.exapps.velox.core.ui.components.VeloxPlayPauseButton
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxShapes
import com.exapps.velox.core.ui.theme.VeloxSpacing
import com.exapps.velox.core.ui.theme.VeloxTheme
import com.exapps.velox.core.ui.theme.accentColor
import com.exapps.velox.core.ui.theme.glassSurfaceColor
import com.exapps.velox.player.engine.VeloxResizeMode
import com.exapps.velox.player.engine.VeloxVideoSurface
import com.exapps.velox.player.service.subtitleMimeTypeFor
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

/** The transient center feedback for whichever gesture is in flight (§5 "Visual feedback"). */
private sealed interface GestureFeedback {
    data class Seek(val deltaMs: Long, val backwards: Boolean) : GestureFeedback
    data class Brightness(val percent: Int) : GestureFeedback
    data class Volume(val percent: Int) : GestureFeedback
    data object SpeedBoost : GestureFeedback
}

private enum class DragMode { NONE, SEEK, BRIGHTNESS, VOLUME }

private val SPEED_CHOICES = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f)

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

private fun formatSignedDuration(deltaMs: Long): String =
    (if (deltaMs >= 0) "+" else "−") + formatDuration(abs(deltaMs))

/** SCREEN_VIDEO_PLAYER.md §13: controls fade 200–250ms. */
private const val CONTROLS_FADE_MS = 220

/**
 * SCREEN_VIDEO_PLAYER.md — immersive video surface with the full gesture map (§5):
 * tap toggles controls, double-tap seeks (physical left = back, §12 RTL note),
 * horizontal drag scrubs, left/right vertical drags drive brightness/volume,
 * long-press holds 2x speed, pinch zooms; lock mode (§6) disables all of it.
 */
@Composable
fun VideoPlayerScreen(
    mediaItemId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val seekIncrementSeconds by viewModel.seekIncrementSeconds.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
    val view = LocalView.current

    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(VeloxResizeMode.FIT) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var feedback by remember { mutableStateOf<GestureFeedback?>(null) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showTracksSheet by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableStateOf(0f) }
    var speedBeforeBoost by remember { mutableFloatStateOf(1f) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var brightness by remember {
        mutableFloatStateOf(
            (activity?.window?.attributes?.screenBrightness ?: -1f).takeIf { it >= 0f } ?: 0.5f,
        )
    }
    var volumeDragAnchor by remember { mutableFloatStateOf(-1f) }

    // Feedback pills fade on their own once the gesture stops updating them.
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(650)
            feedback = null
        }
    }

    // Controls auto-hide after 3s of playback; scrubbing or sheet interaction pauses that.
    LaunchedEffect(controlsVisible, state.isPlaying, locked, scrubbing, showSpeedSheet, showTracksSheet) {
        if (controlsVisible && state.isPlaying && !locked && !scrubbing && !showSpeedSheet && !showTracksSheet) {
            delay(3_000)
            controlsVisible = false
        }
    }

    // Immersive: system bars track the controls (§2.1), restored when leaving.
    DisposableEffect(controlsVisible) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (controlsVisible) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val subtitlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let {
            val name = it.lastPathSegment?.substringAfterLast('/') ?: "subtitle"
            viewModel.onSubtitleFilePicked(it.toString(), subtitleMimeTypeFor(name), name)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        VeloxVideoSurface(
            playerController = viewModel.controller,
            modifier = Modifier.fillMaxSize(),
            resizeMode = resizeMode,
            zoom = zoom,
        )

        // ---- Gesture layers (composed out entirely in lock mode, §6) ----
        if (!locked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Pinch → zoom (§5), 1x–3x. Two pointers only, so it never
                    // competes with the single-finger detectors below.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.size >= 2) {
                                    val zoomChange = event.calculateZoom()
                                    if (abs(zoomChange - 1f) > 0.01f) {
                                        zoom = (zoom * zoomChange).coerceIn(1f, 3f)
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(seekIncrementSeconds) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = { pos ->
                                // Physical screen half, not layout direction (§12).
                                val backwards = pos.x < size.width / 2f
                                val delta = seekIncrementSeconds * 1_000L
                                viewModel.onSeekBy(if (backwards) -delta else delta)
                                feedback = GestureFeedback.Seek(if (backwards) -delta else delta, backwards)
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        var mode = DragMode.NONE
                        var startX = 0f
                        var seekDeltaMs = 0f
                        var msPerPx = 0f
                        var verticalAccumulated = 0f
                        detectDragGestures(
                            onDragStart = { pos ->
                                mode = DragMode.NONE
                                startX = pos.x
                                seekDeltaMs = 0f
                                verticalAccumulated = 0f
                                volumeDragAnchor = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                                // Full-width horizontal drag ≈ the whole duration, clamped
                                // so short and very long videos both stay controllable.
                                msPerPx = (viewModel.state.value.durationMs.coerceAtLeast(1L)
                                        .toFloat() / size.width)
                                    .coerceIn(60f, 1_500f)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (mode == DragMode.NONE) {
                                    mode = if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                        DragMode.SEEK
                                    } else if (startX < size.width / 2f) {
                                        DragMode.BRIGHTNESS
                                    } else {
                                        DragMode.VOLUME
                                    }
                                }
                                when (mode) {
                                    DragMode.SEEK -> {
                                        seekDeltaMs += dragAmount.x * msPerPx
                                        val playback = viewModel.state.value
                                        val clamped = seekDeltaMs.coerceIn(
                                            -playback.positionMs.toFloat(),
                                            (playback.durationMs - playback.positionMs).coerceAtLeast(0L).toFloat(),
                                        )
                                        feedback = GestureFeedback.Seek(clamped.toLong(), clamped < 0)
                                    }
                                    DragMode.BRIGHTNESS -> {
                                        verticalAccumulated += dragAmount.y
                                        brightness = (brightness - verticalAccumulated / size.height)
                                            .coerceIn(0.02f, 1f)
                                        activity?.window?.let { window ->
                                            val attrs = window.attributes
                                            attrs.screenBrightness = brightness
                                            window.attributes = attrs
                                        }
                                        feedback = GestureFeedback.Brightness((brightness * 100).toInt())
                                    }
                                    DragMode.VOLUME -> {
                                        verticalAccumulated += dragAmount.y
                                        // Volume steps are discrete — anchor the drag to the
                                        // volume at gesture start and map the full drag
                                        // height onto the whole range (up = louder).
                                        val fraction = (volumeDragAnchor / maxVolume - verticalAccumulated / size.height)
                                            .coerceIn(0f, 1f)
                                        val targetSteps = (fraction * maxVolume).toInt().coerceIn(0, maxVolume)
                                        if (targetSteps != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetSteps, 0)
                                        }
                                        feedback = GestureFeedback.Volume((fraction * 100).toInt())
                                    }
                                    DragMode.NONE -> Unit
                                }
                            },
                            onDragEnd = {
                                if (mode == DragMode.SEEK && seekDeltaMs != 0f) {
                                    viewModel.onSeekBy(seekDeltaMs.toLong())
                                }
                                mode = DragMode.NONE
                                feedback = null
                            },
                            onDragCancel = {
                                mode = DragMode.NONE
                                feedback = null
                            },
                        )
                    }
                    // Long-press → temporary 2x speed (§5 "Speed scrub"). Observed on
                    // the Initial pass so it never steals events from the detectors
                    // above; release anywhere restores the previous speed.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var boosted = false
                            var slopped = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.any { abs(it.positionChange().getDistance()) > viewConfiguration.touchSlop * 2 }) {
                                    slopped = true
                                }
                                val pressed = event.changes.any { it.pressed }
                                val heldLongEnough = event.changes.maxOf { it.uptimeMillis } - down.uptimeMillis > 480L
                                if (!boosted && !slopped && heldLongEnough) {
                                    boosted = true
                                    speedBeforeBoost = viewModel.state.value.playbackSpeed.takeIf { it in 0.25f..2f } ?: 1f
                                    viewModel.onSetSpeed(2f)
                                    feedback = GestureFeedback.SpeedBoost
                                }
                                if (!pressed) {
                                    if (boosted) {
                                        viewModel.onSetSpeed(speedBeforeBoost)
                                        feedback = null
                                    }
                                    break
                                }
                            }
                        }
                    },
            )
        }

        // ---- Buffering indicator (§10) ----
        if (state.status == PlaybackStatus.BUFFERING) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.Center).size(44.dp),
            )
        }

        // ---- Gesture feedback pill ----
        feedback?.let { current ->
            GestureFeedbackPill(current, modifier = Modifier.align(Alignment.Center))
        }

        // ---- Controls chrome ----
        AnimatedVisibility(
            visible = controlsVisible && !locked,
            enter = fadeIn(tween(CONTROLS_FADE_MS)),
            exit = fadeOut(tween(CONTROLS_FADE_MS)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            0.15f to Color.Transparent,
                            0.82f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.75f),
                        ),
                    )
                    .padding(VeloxSpacing.md),
            ) {
                // Top bar (§3): back, title, PiP, lock
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.sm),
                ) {
                    VeloxGlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        onClick = onBack,
                    )
                    Text(
                        text = state.currentItem?.title.orEmpty(),
                        style = VeloxTheme.typography.titleMedium,
                        color = VeloxColors.OnBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    VeloxGlassIconButton(
                        icon = Icons.Filled.PictureInPictureAlt,
                        contentDescription = stringResource(R.string.cd_pip),
                        onClick = {
                            activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                        },
                    )
                    VeloxGlassIconButton(
                        icon = Icons.Filled.Lock,
                        contentDescription = stringResource(R.string.cd_lock_controls),
                        onClick = {
                            locked = true
                            controlsVisible = false
                        },
                    )
                }

                Spacer(Modifier.weight(1f))

                // Progress — LTR by convention (§12 / SCREEN_NOW_PLAYING.md §11).
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column {
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
                                viewModel.onSeekTo(scrubPositionMs.toLong())
                                scrubbing = false
                            },
                            valueRange = 0f..duration,
                            colors = SliderDefaults.colors(
                                thumbColor = VeloxColors.OnBackground,
                                activeTrackColor = accentColor(),
                                inactiveTrackColor = VeloxColors.OnSurfaceVariant.copy(alpha = 0.32f),
                            ),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatDuration(position.toLong()), style = VeloxTheme.typography.labelMedium, color = VeloxColors.OnSurfaceVariant)
                            Text(formatDuration(duration.toLong()), style = VeloxTheme.typography.labelMedium, color = VeloxColors.OnSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(VeloxSpacing.sm))

                // Transport row (§4)
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
                    VeloxGlassIconButton(Icons.Filled.SkipPrevious, stringResource(R.string.cd_previous), viewModel::onSkipPrevious)
                    VeloxPlayPauseButton(
                        icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                        onClick = viewModel::onPlayPause,
                    )
                    VeloxGlassIconButton(Icons.Filled.SkipNext, stringResource(R.string.cd_next), viewModel::onSkipNext)
                    VeloxGlassIconButton(
                        icon = if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = stringResource(R.string.cd_repeat),
                        onClick = viewModel::onCycleRepeat,
                        tint = if (state.repeatMode != RepeatMode.OFF) accentColor() else VeloxColors.OnSurface,
                    )
                }

                Spacer(Modifier.height(VeloxSpacing.sm))

                // Utility row (§4): speed, aspect ratio, subtitles, audio tracks
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpeedChipButton(
                        speed = state.playbackSpeed,
                        onClick = { showSpeedSheet = true },
                    )
                    VeloxGlassIconButton(
                        icon = Icons.Filled.AspectRatio,
                        contentDescription = stringResource(R.string.cd_aspect_ratio),
                        onClick = {
                            resizeMode = when (resizeMode) {
                                VeloxResizeMode.FIT -> VeloxResizeMode.FILL
                                VeloxResizeMode.FILL -> VeloxResizeMode.ZOOM
                                VeloxResizeMode.ZOOM -> VeloxResizeMode.FIT
                            }
                            zoom = 1f
                        },
                    )
                    VeloxGlassIconButton(
                        icon = Icons.Filled.Subtitles,
                        contentDescription = stringResource(R.string.cd_subtitles),
                        onClick = { showTracksSheet = true },
                    )
                    VeloxGlassIconButton(
                        icon = Icons.Filled.Audiotrack,
                        contentDescription = stringResource(R.string.cd_audio_tracks),
                        onClick = { showTracksSheet = true },
                    )
                }
            }
        }

        // ---- Lock mode chrome (§6): only the unlock affordance survives ----
        if (locked) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(VeloxSpacing.xl)) {
                VeloxGlassIconButton(
                    icon = Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.cd_unlock_controls),
                    onClick = {
                        locked = false
                        controlsVisible = true
                    },
                )
            }
        }
    }

    if (showSpeedSheet) {
        SpeedPickerSheet(
            current = state.playbackSpeed,
            onSelected = { speed ->
                viewModel.onSetSpeed(speed)
                showSpeedSheet = false
            },
            onDismiss = { showSpeedSheet = false },
        )
    }

    if (showTracksSheet) {
        TracksSheet(
            tracks = tracks,
            onAudioTrackSelected = { viewModel.onSelectTrack(TrackType.AUDIO, it) },
            onTextTrackSelected = { viewModel.onSelectTrack(TrackType.TEXT, it) },
            onOpenSubtitleFile = {
                showTracksSheet = false
                subtitlePicker.launch(
                    arrayOf(
                        "application/x-subrip",
                        "text/vtt",
                        "application/ttml+xml",
                        "application/octet-stream",
                        "*/*",
                    ),
                )
            },
            onDismiss = { showTracksSheet = false },
        )
    }
}

@Composable
private fun GestureFeedbackPill(feedback: GestureFeedback, modifier: Modifier = Modifier) {
    val (icon, label) = when (feedback) {
        is GestureFeedback.Seek -> Icons.Filled.PlayArrow to formatSignedDuration(feedback.deltaMs)
        is GestureFeedback.Brightness -> Icons.Filled.Brightness6 to "${feedback.percent}%"
        is GestureFeedback.Volume -> Icons.Filled.VolumeUp to "${feedback.percent}%"
        GestureFeedback.SpeedBoost -> Icons.Filled.Bolt to formatSpeed(2f)
    }
    Box(
        modifier = modifier
            .clip(VeloxShapes.full)
            .background(glassSurfaceColor(elevated = true))
            .padding(horizontal = VeloxSpacing.xl, vertical = VeloxSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.sm),
        ) {
            Icon(icon, contentDescription = null, tint = VeloxColors.OnBackground)
            Text(label, style = VeloxTheme.typography.titleMedium, color = VeloxColors.OnBackground)
        }
    }
}

@Composable
private fun SpeedChipButton(speed: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(VeloxShapes.full)
            .background(glassSurfaceColor())
            .clickable(onClick = onClick)
            .padding(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.sm),
    ) {
        Text(
            text = formatSpeed(speed),
            style = VeloxTheme.typography.labelLarge,
            color = if (speed != 1f) accentColor() else VeloxColors.OnSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedPickerSheet(
    current: Float,
    onSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = VeloxColors.Surface) {
        Column(Modifier.padding(horizontal = VeloxSpacing.lg)) {
            Text(
                text = stringResource(R.string.video_speed_title),
                style = VeloxTheme.typography.headlineMedium,
                color = VeloxColors.OnBackground,
                modifier = Modifier.padding(bottom = VeloxSpacing.md),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(VeloxSpacing.sm),
                modifier = Modifier.height(240.dp).padding(bottom = VeloxSpacing.xl),
            ) {
                items(SPEED_CHOICES.size) { index ->
                    val speed = SPEED_CHOICES[index]
                    val selected = abs(speed - current) < 0.01f
                    Box(
                        modifier = Modifier
                            .clip(VeloxShapes.md)
                            .background(if (selected) accentColor() else glassSurfaceColor())
                            .clickable { onSelected(speed) }
                            .padding(VeloxSpacing.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = formatSpeed(speed),
                            style = VeloxTheme.typography.titleMedium,
                            color = if (selected) VeloxColors.Background else VeloxColors.OnSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TracksSheet(
    tracks: List<PlayerTrack>,
    onAudioTrackSelected: (String?) -> Unit,
    onTextTrackSelected: (String?) -> Unit,
    onOpenSubtitleFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val audioTracks = tracks.filter { it.type == TrackType.AUDIO }
    val textTracks = tracks.filter { it.type == TrackType.TEXT }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = VeloxColors.Surface) {
        Column(Modifier.padding(horizontal = VeloxSpacing.lg)) {
            Text(
                text = stringResource(R.string.video_tracks_title),
                style = VeloxTheme.typography.headlineMedium,
                color = VeloxColors.OnBackground,
                modifier = Modifier.padding(bottom = VeloxSpacing.md),
            )

            Text(
                text = stringResource(R.string.video_tracks_audio_section),
                style = VeloxTheme.typography.labelLarge,
                color = VeloxColors.OnSurfaceVariant,
            )
            if (audioTracks.isEmpty()) {
                Text(
                    text = stringResource(R.string.video_tracks_audio_none),
                    style = VeloxTheme.typography.bodyMedium,
                    color = VeloxColors.OnSurfaceVariant,
                )
            }
            audioTracks.forEach { track ->
                TrackRow(track = track, onClick = { onAudioTrackSelected(track.id) })
            }

            Spacer(Modifier.height(VeloxSpacing.md))

            Text(
                text = stringResource(R.string.video_tracks_subtitles_section),
                style = VeloxTheme.typography.labelLarge,
                color = VeloxColors.OnSurfaceVariant,
            )
            TrackRow(
                track = PlayerTrack(
                    id = "off",
                    type = TrackType.TEXT,
                    label = stringResource(R.string.video_subtitles_off),
                    language = null,
                    isSelected = textTracks.none { it.isSelected },
                ),
                onClick = { onTextTrackSelected(null) },
            )
            textTracks.forEach { track ->
                TrackRow(track = track, onClick = { onTextTrackSelected(track.id) })
            }
            // §7 "Open external" — online search arrives in Phase 1.1.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(VeloxShapes.md)
                    .clickable(onClick = onOpenSubtitleFile)
                    .padding(VeloxSpacing.md),
            ) {
                Icon(Icons.Filled.Subtitles, contentDescription = null, tint = accentColor())
                Text(
                    text = stringResource(R.string.video_subtitles_open_file),
                    style = VeloxTheme.typography.titleMedium,
                    color = VeloxColors.OnSurface,
                )
            }
            Spacer(Modifier.height(VeloxSpacing.xxl))
        }
    }
}

@Composable
private fun TrackRow(track: PlayerTrack, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md),
        modifier = modifier
            .fillMaxWidth()
            .clip(VeloxShapes.md)
            .clickable(onClick = onClick)
            .padding(VeloxSpacing.md),
    ) {
        if (track.isSelected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = accentColor())
        } else {
            Spacer(Modifier.size(24.dp))
        }
        Text(
            text = track.label,
            style = VeloxTheme.typography.titleMedium,
            color = if (track.isSelected) accentColor() else VeloxColors.OnSurface,
        )
    }
}

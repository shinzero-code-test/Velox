package com.exapps.velox.player.engine

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.exapps.velox.core.domain.player.PlayerController

/**
 * The resize/aspect modes offered by SCREEN_VIDEO_PLAYER.md §8 (Fit / Fill / Zoom),
 * as an engine-owned enum so feature code never touches a media3-ui constant.
 */
enum class VeloxResizeMode(internal val media3Mode: Int) {
    /** Letter/pillar-box to preserve aspect ratio (default). */
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT),

    /** Stretch to fill the window, ignoring aspect ratio. */
    FILL(AspectRatioFrameLayout.RESIZE_MODE_FILL),

    /** Crop-fill: preserve aspect ratio by scaling until both edges are covered. */
    ZOOM(AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
}

/**
 * Renders the session player's video inside Compose (SCREEN_VIDEO_PLAYER.md §2).
 * All chrome — progress, transport, gestures — is Compose drawn on top; the
 * embedded PlayerView controller is permanently off. Subtitle rendering stays with
 * PlayerView's own SubtitleView, so external/text tracks selected through
 * PlayerController render here for free.
 */
@Composable
fun VeloxVideoSurface(
    playerController: PlayerController,
    modifier: Modifier = Modifier,
    resizeMode: VeloxResizeMode = VeloxResizeMode.FIT,
    zoom: Float = 1f,
) {
    val context = LocalContext.current
    val player = (playerController as? Media3PlayerAccessor)?.media3Player

    AndroidView(
        modifier = modifier.graphicsLayer {
            scaleX = zoom
            scaleY = zoom
        },
        factory = {
            PlayerView(context).apply {
                useController = false
                // Keep the last frame visible across item transitions instead of
                // flashing black — matters for queue playback of several videos.
                setKeepContentOnPlayerReset(true)
            }
        },
        update = { view ->
            view.resizeMode = resizeMode.media3Mode
            if (view.player !== player) view.player = player
        },
        onRelease = { view ->
            view.player = null
        },
    )
}

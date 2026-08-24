package com.exapps.velox.feature.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxShapes
import com.exapps.velox.core.ui.theme.accentColor
import com.exapps.velox.core.ui.theme.glassSurfaceColor

/**
 * One vertical EQ band (SCREEN_EQUALIZER.md §3): drag to move, fill is measured
 * from the 0dB midpoint like a hardware fader, accent-colored, ~48dp wide to stay
 * a comfortable touch target. Levels are in millibel.
 */
@Composable
internal fun VerticalBandSlider(
    levelMillibel: Int,
    minLevelMillibel: Int,
    maxLevelMillibel: Int,
    enabled: Boolean,
    onLevelChange: (Int) -> Unit,
    onDragFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = (maxLevelMillibel - minLevelMillibel).coerceAtLeast(1)
    var dragLevel by remember(levelMillibel) { mutableFloatStateOf(levelMillibel.toFloat()) }
    // Composable-scoped so the Canvas draw lambda (a DrawScope, not a composable)
    // can use the live accent.
    val accent = accentColor()

    Box(
        modifier = modifier
            .width(40.dp)
            .fillMaxHeight()
            .clip(VeloxShapes.sm)
            .pointerInput(enabled, minLevelMillibel, maxLevelMillibel) {
                if (!enabled) return@pointerInput
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        // Drag up = boost: invert the Y delta over the track height.
                        val fractionDelta = -dragAmount / size.height.toFloat()
                        dragLevel = (dragLevel + fractionDelta * range)
                            .coerceIn(minLevelMillibel.toFloat(), maxLevelMillibel.toFloat())
                        onLevelChange(dragLevel.toInt())
                    },
                    onDragEnd = { onDragFinished() },
                    onDragCancel = { onDragFinished() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp)) {
            val trackWidth = 8.dp.toPx()
            val midY = size.height / 2f
            val zeroDbY = midY

            // Full track
            drawRoundRect(
                color = glassSurfaceColor(elevated = true),
                topLeft = Offset((size.width - trackWidth) / 2f, 0f),
                size = Size(trackWidth, size.height),
                cornerRadius = CornerRadius(trackWidth / 2f),
            )
            // 0dB reference line
            drawRoundRect(
                color = VeloxColors.OnSurfaceVariant.copy(alpha = 0.35f),
                topLeft = Offset(0f, zeroDbY - 1.dp.toPx() / 2f),
                size = Size(size.width, 1.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx() / 2f),
            )

            if (enabled) {
                val fraction = (dragLevel - minLevelMillibel) / range.toFloat()
                val thumbY = (1f - fraction) * size.height
                // Fill from the 0dB midpoint toward the thumb
                val fillTop = minOf(thumbY, zeroDbY)
                val fillHeight = maxOf(1f, abs(thumbY - zeroDbY))
                drawRoundRect(
                    color = accent,
                    topLeft = Offset((size.width - trackWidth) / 2f, fillTop),
                    size = Size(trackWidth, fillHeight),
                    cornerRadius = CornerRadius(trackWidth / 2f),
                )
                // Thumb
                val thumbRadius = 6.dp.toPx()
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = Offset(size.width / 2f, thumbY),
                )
            }
        }
    }
}

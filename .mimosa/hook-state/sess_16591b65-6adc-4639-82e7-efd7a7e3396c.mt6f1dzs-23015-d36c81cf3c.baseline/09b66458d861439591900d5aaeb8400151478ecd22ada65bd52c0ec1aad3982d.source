package com.exapps.velox.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.exapps.velox.core.ui.theme.VeloxMotion
import com.exapps.velox.core.ui.theme.VeloxShapes
import com.exapps.velox.core.ui.theme.VeloxSpacing
import com.exapps.velox.core.ui.theme.glassOutlineColor
import com.exapps.velox.core.ui.theme.glassSurfaceColor

/**
 * The base "frosted glass" surface — DESIGN_SYSTEM.md §5.1 / §8.
 *
 * This is the cheap, reliable approximation the design doc itself endorses for lists and
 * grids ("approximate with pre-blurred images + overlays") rather than a true
 * `Modifier.blur` backdrop, which is costly behind scrolling content. Reach for [GlassCard]
 * by default; a real backdrop blur is only worth its cost behind mostly-static content
 * (e.g. the Now Playing background) and should be built separately at that call site.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = VeloxShapes.md,
    elevated: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(VeloxSpacing.md),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(glassSurfaceColor(elevated))
            .border(1.dp, glassOutlineColor(strong = elevated), shape)
            .padding(contentPadding),
    ) {
        content()
    }
}

/**
 * [GlassCard] plus tap/long-press handling and DESIGN_SYSTEM.md's press-scale feedback
 * (0.97 — §5.2, confirmed again in SCREENS_OVERVIEW.md §7 and SCREEN_HOME_LIBRARY.md §6).
 * Used for library cards, playlist cards, and list rows — anywhere a glass surface is
 * also a tap target. Indication is intentionally `null`: the scale animation *is* the
 * press feedback here, layering a default ripple on top of it reads as noisy.
 */
@Composable
fun ClickableGlassCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = VeloxShapes.md,
    elevated: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(VeloxSpacing.md),
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) VeloxMotion.PRESS_SCALE else 1f,
        animationSpec = tween(durationMillis = VeloxMotion.Duration.MICRO_MS),
        label = "glassCardPressScale",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(glassSurfaceColor(elevated))
            .border(1.dp, glassOutlineColor(strong = elevated), shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(contentPadding),
    ) {
        content()
    }
}

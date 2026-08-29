package com.exapps.velox.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
// `minimumInteractiveComponentSize` is exposed by the Material 2
// (`androidx.compose.material`) extension on `Modifier`. Material 2
// is on Velox's classpath via `androidx.compose.material:material`
// (a transitive of material-icons-extended, which the
// feature/library/feature/player modules all already depend on).
import androidx.compose.material.minimumInteractiveComponentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxShapes
import com.exapps.velox.core.ui.theme.accentColor
import com.exapps.velox.core.ui.theme.glassSurfaceColor

/**
 * Circular glass icon button (DESIGN_SYSTEM.md §5.5). The 48dp default touch target
 * meets the accessibility minimum called out in §9 and SCREEN_PATTERNS.md §11 even
 * when the visual icon itself is smaller.
 *
 * M13 (features review): callers previously passed a 32dp or 36dp `size` to
 * compress rows, which put the touch target below Material's 40dp minimum.
 * We now enforce the 40dp floor via `minimumInteractiveComponentSize()`
 * regardless of the caller's chosen visual size.
 */
@Composable
fun VeloxGlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    tint: androidx.compose.ui.graphics.Color = VeloxColors.OnSurface,
    filled: Boolean = false,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .clip(VeloxShapes.full)
            .background(if (filled) accentColor() else glassSurfaceColor())
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
    ) {
        CompositionLocalProvider(LocalContentColor provides if (filled) VeloxColors.currentBackground else tint) {
            Icon(
                imageVector = icon,
                contentDescription = null, // already set on the Box above
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/** The large, filled Play/Pause anchor button (SCREEN_NOW_PLAYING.md §6: "the visual
 * anchor — larger, possibly contained in glass circle"). */
@Composable
fun VeloxPlayPauseButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VeloxGlassIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        size = 72.dp,
        iconSize = 32.dp,
        filled = true,
    )
}

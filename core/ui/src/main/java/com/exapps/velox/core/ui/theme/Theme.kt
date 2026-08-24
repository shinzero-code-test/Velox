package com.exapps.velox.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Velox ships one visual mode for v1: Dark Glass (DESIGN_SYSTEM.md — "Light (future)"
 * is explicitly out of scope). `isSystemInDarkTheme()` is intentionally not consulted.
 * TECHNICAL_PLAN.md §8 also calls for an AMOLED pure-black variant (Settings →
 * Appearance), which the [amoled] flag switches.
 *
 * The accent is overridable at runtime from Settings → Appearance → Accent color
 * (BRANDING.md's swatch options): components read [accentColor] instead of
 * `VeloxColors.Accent` directly so the whole app retints without recomposition of
 * static references.
 */
private val LocalVeloxSpacing = staticCompositionLocalOf { VeloxSpacing }
private val LocalVeloxShapes = staticCompositionLocalOf { VeloxShapes }
private val LocalVeloxTypography = staticCompositionLocalOf<VeloxTypography> {
    error("VeloxTypography not provided — wrap content in VeloxTheme { }")
}
private val LocalVeloxAccent = staticCompositionLocalOf { VeloxColors.Accent }

/** Mirrors the `MaterialTheme.xxx` access pattern for the tokens Material3 doesn't model
 * (glass alpha tiers, the doc's own spacing/radius scale, the full 11-style type scale). */
object VeloxTheme {
    val spacing: VeloxSpacing
        @Composable get() = LocalVeloxSpacing.current
    val shapes: VeloxShapes
        @Composable get() = LocalVeloxShapes.current
    val typography: VeloxTypography
        @Composable get() = LocalVeloxTypography.current
}

/** The live accent — Teal unless the user picked another swatch in Settings. */
@Composable
fun accentColor(): Color = LocalVeloxAccent.current

/** The 15%-alpha "accent container" tint derived from the live accent. */
@Composable
fun accentContainerColor(): Color = accentColor().copy(alpha = VeloxColors.AccentContainerAlpha)

@Composable
fun VeloxTheme(
    amoled: Boolean = false,
    accent: Color = VeloxColors.Accent,
    content: @Composable () -> Unit,
) {
    val backgroundColor = VeloxColors.background
    val surfaceColor = VeloxColors.surface

    // Publish the mode for the palette helpers and every VeloxColors.surface /
    // .background reader across the app (see the flag's doc on why this must be
    // snapshot state). SideEffect = write after successful composition, no loop.
    SideEffect { VeloxColors.amoledMode = amoled }

    val colorScheme = darkColorScheme(
        primary = accent,
        onPrimary = if (amoled) VeloxColors.AmoledBackground else VeloxColors.background,
        primaryContainer = accent.copy(alpha = VeloxColors.AccentContainerAlpha),
        onPrimaryContainer = accent,
        secondary = accent,
        onSecondary = if (amoled) VeloxColors.AmoledBackground else VeloxColors.background,
        background = backgroundColor,
        onBackground = VeloxColors.OnBackground,
        surface = surfaceColor,
        onSurface = VeloxColors.OnSurface,
        surfaceVariant = surfaceColor,
        onSurfaceVariant = VeloxColors.OnSurfaceVariant,
        outline = glassOutlineColor(),
        outlineVariant = glassOutlineColor(strong = true),
        error = VeloxColors.Error,
        onError = VeloxColors.OnBackground,
    )

    val veloxTypography = rememberVeloxTypography()

    CompositionLocalProvider(
        LocalVeloxSpacing provides VeloxSpacing,
        LocalVeloxShapes provides VeloxShapes,
        LocalVeloxTypography provides veloxTypography,
        LocalVeloxAccent provides accent,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = veloxTypography.toMaterial3Typography(),
            content = content,
        )
    }
}

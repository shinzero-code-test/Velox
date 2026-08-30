package com.exapps.velox.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
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
 *
 * Phase 3 / Milestone 2: a new [VeloxThemeSpec]-driven overload accepts the
 * resolved theme (theme id + accent override + AMOLED flag) and is the
 * preferred call site going forward. The legacy two-arg overload is kept
 * for callers that don't yet have a [VeloxThemeSpec] (notably the widget
 * Glance surface, which can't easily reach the theme registry).
 */
private val LocalVeloxSpacing = staticCompositionLocalOf { VeloxSpacing }
private val LocalVeloxShapes = staticCompositionLocalOf { VeloxShapes }
private val LocalVeloxTypography = staticCompositionLocalOf<VeloxTypography> {
    error("VeloxTypography not provided — wrap content in VeloxTheme { }")
}
private val LocalVeloxAccent = staticCompositionLocalOf { VeloxColors.Accent }
private val LocalVeloxThemeSpec = staticCompositionLocalOf<VeloxThemeSpec> {
    error("VeloxThemeSpec not provided — wrap content in VeloxTheme { }")
}

/** Mirrors the `MaterialTheme.xxx` access pattern for the tokens Material3 doesn't model
 * (glass alpha tiers, the doc's own spacing/radius scale, the full 11-style type scale). */
object VeloxTheme {
    val spacing: VeloxSpacing
        @Composable get() = LocalVeloxSpacing.current
    val shapes: VeloxShapes
        @Composable get() = LocalVeloxShapes.current
    val typography: VeloxTypography
        @Composable get() = LocalVeloxTypography.current
    /** The resolved theme spec. Available inside any `VeloxTheme { }` block. */
    val spec: VeloxThemeSpec
        @Composable get() = LocalVeloxThemeSpec.current
}

/** The live accent — Teal unless the user picked another swatch in Settings. */
@Composable
fun accentColor(): Color = LocalVeloxAccent.current

/** The 15%-alpha "accent container" tint derived from the live accent. */
@Composable
fun accentContainerColor(): Color = accentColor().copy(alpha = VeloxColors.AccentContainerAlpha)

/**
 * Legacy two-arg overload — kept for callers that have only an
 * [amoled] / [accent] pair and no theme spec. New call sites should
 * prefer the [VeloxThemeSpec] overload below.
 */
@Composable
fun VeloxTheme(
    amoled: Boolean = false,
    accent: Color = VeloxColors.Accent,
    content: @Composable () -> Unit,
) {
    val spec = resolveThemeSpec(
        theme = com.exapps.velox.core.domain.theme.ThemeDefinition(
            id = "__legacy__",
            name = com.exapps.velox.core.domain.theme.LocalizedText(default = "Legacy"),
        ),
        accent = accent,
        amoled = amoled,
    )
    VeloxTheme(spec = spec, content = content)
}

/**
 * Preferred entry point: takes a [VeloxThemeSpec] (already resolved by
 * the caller — typically from [resolveThemeSpec] with the active theme
 * and accent picker) and materialises it into the Compose
 * CompositionLocals + MaterialTheme color scheme.
 */
@Composable
fun VeloxTheme(
    spec: VeloxThemeSpec,
    content: @Composable () -> Unit,
) {
    // Publish the amoled mode for the palette helpers and every
    // VeloxColors.currentSurface / .background reader across the app
    // (see the flag's doc on why this must be snapshot state).
    // SideEffect = write after successful composition, no loop.
    SideEffect { VeloxColors.amoledMode = spec.amoled }

    val colorScheme = darkColorScheme(
        primary = spec.accent,
        onPrimary = if (spec.amoled) VeloxColors.AmoledBackground else spec.background,
        primaryContainer = spec.accent.copy(alpha = VeloxColors.AccentContainerAlpha),
        onPrimaryContainer = spec.accent,
        secondary = spec.accent,
        onSecondary = if (spec.amoled) VeloxColors.AmoledBackground else spec.background,
        background = spec.background,
        onBackground = spec.onBackground,
        surface = spec.surface,
        onSurface = spec.onSurface,
        surfaceVariant = spec.surface,
        onSurfaceVariant = spec.onSurfaceVariant,
        outline = glassOutlineColorAt(spec.outlineAlpha),
        outlineVariant = glassOutlineColorAt(spec.outlineStrongAlpha),
        error = spec.error,
        onError = spec.onBackground,
    )

    val veloxTypography = rememberVeloxTypography()

    CompositionLocalProvider(
        LocalVeloxSpacing provides VeloxSpacing,
        LocalVeloxShapes provides VeloxShapes,
        LocalVeloxTypography provides veloxTypography,
        LocalVeloxAccent provides spec.accent,
        LocalVeloxThemeSpec provides spec,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = veloxTypography.toMaterial3Typography(),
            content = content,
        )
    }
}

/**
 * Compose-friendly accessor for the glass surface tint. Reads the
 * active [VeloxThemeSpec] rather than the legacy [VeloxColors] static
 * field, so themes that override `glassAlpha` are honoured everywhere.
 */
@Composable
fun glassSurfaceColor(elevated: Boolean = false): Color {
    val alpha = if (elevated) {
        VeloxTheme.spec.glassElevatedAlpha
    } else {
        VeloxTheme.spec.glassAlpha
    }
    return Color.White.copy(alpha = alpha)
}

/**
 * Compose-friendly accessor for the outline tint. Reads the active
 * [VeloxThemeSpec] for the same reason as [glassSurfaceColor].
 */
@Composable
fun glassOutlineColor(strong: Boolean = false): Color {
    val alpha = if (strong) {
        VeloxTheme.spec.outlineStrongAlpha
    } else {
        VeloxTheme.spec.outlineAlpha
    }
    return Color.White.copy(alpha = alpha)
}

/**
 * Direct alpha form for callers that already have a `Float` (e.g. the
 * theme engine reading `spec.outlineAlpha`). Not composable — pure
 * helper. Use the [Boolean] form above in modifier chains.
 */
fun glassOutlineColorAt(alpha: Float): Color = Color.White.copy(alpha = alpha)

/**
 * Direct alpha form for the glass surface tint, mirror of
 * [glassOutlineColorAt].
 */
fun glassSurfaceColorAt(alpha: Float): Color = Color.White.copy(alpha = alpha)

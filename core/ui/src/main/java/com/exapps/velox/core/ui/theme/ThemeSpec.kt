package com.exapps.velox.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.exapps.velox.core.domain.theme.ThemeDefinition
import com.exapps.velox.core.domain.theme.ThemeTokens

/**
 * Phase 3 / Milestone 2 — Theme engine. The *resolved* runtime shape:
 * the user-selected [ThemeDefinition] plus the accent-picker override
 * and the AMOLED toggle, all materialised into Compose [Color]s and
 * floats. This is what [VeloxTheme] actually reads; the [ThemeDefinition]
 * is the serialisable storage shape.
 *
 * Marked [Immutable] so the Compose compiler can skip recomposition
 * when the spec hasn't changed (the StateFlow emitting the spec is
 * conflated, but `@Immutable` lets the compiler prove the value is
 * stable for a given emission).
 */
@Immutable
data class VeloxThemeSpec(
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val error: Color,
    val accent: Color,
    val glassAlpha: Float,
    val glassElevatedAlpha: Float,
    val outlineAlpha: Float,
    val outlineStrongAlpha: Float,
    val radiusScale: Float,
    val amoled: Boolean,
    /** The id of the source theme (for the picker UI / debugging). */
    val themeId: String,
)

/**
 * Resolves a [ThemeDefinition] into a [VeloxThemeSpec] by combining
 * the theme's [ThemeTokens] with:
 *  - the live accent override (`accentIndex` in `UserSettings` → one of
 *    [VeloxAccentOptions]);
 *  - the AMOLED toggle (which forces a pure-black background and a
 *    pure-black `onPrimary` regardless of the theme's background token).
 *
 * Any field the theme doesn't override falls back to the bundled
 * "Dark Glass" default in [VeloxColors]. This is what makes themes
 * additive — an author can ship a theme that only changes
 * `glassAlpha` and still get a usable, fully-coloured app.
 */
fun resolveThemeSpec(
    theme: ThemeDefinition,
    accent: Color,
    amoled: Boolean,
): VeloxThemeSpec {
    val t = theme.tokens
    return VeloxThemeSpec(
        background = if (amoled) {
            // AMOLED always wins over the theme's background token —
            // the whole point of the toggle is to bypass the layered
            // greys. Theme authors can mark their theme `amoled: true`
            // to opt in to this behaviour by default, but the toggle
            // is a runtime override.
            VeloxColors.AmoledBackground
        } else {
            t.background.parseColorOr(VeloxColors.Background)
        },
        onBackground = t.onBackground.parseColorOr(VeloxColors.OnBackground),
        surface = if (amoled) {
            VeloxColors.AmoledSurface
        } else {
            t.surface.parseColorOr(VeloxColors.Surface)
        },
        onSurface = t.onSurface.parseColorOr(VeloxColors.OnSurface),
        onSurfaceVariant = t.onSurfaceVariant.parseColorOr(VeloxColors.OnSurfaceVariant),
        error = t.error.parseColorOr(VeloxColors.Error),
        accent = accent,
        glassAlpha = t.glassAlpha ?: VeloxColors.SurfaceGlassAlpha,
        glassElevatedAlpha = t.glassElevatedAlpha ?: VeloxColors.SurfaceGlassElevatedAlpha,
        outlineAlpha = t.outlineAlpha ?: VeloxColors.OutlineAlpha,
        outlineStrongAlpha = t.outlineStrongAlpha ?: VeloxColors.OutlineStrongAlpha,
        radiusScale = t.radiusScale ?: 1.0f,
        amoled = amoled,
        themeId = theme.id,
    )
}

/**
 * Parse a `#RRGGBB` or `#AARRGGBB` string into a Compose [Color].
 * Returns [fallback] on any parse failure (empty string, missing `#`,
 * wrong length) so a malformed theme never crashes the app — it just
 * renders with the bundled default for that token.
 */
internal fun String?.parseColorOr(fallback: Color): Color {
    if (this.isNullOrBlank()) return fallback
    val v = this.trim().removePrefix("#")
    if (v.length != 6 && v.length != 8) return fallback
    val parsed = runCatching { v.toLong(16) }.getOrNull() ?: return fallback
    return when (v.length) {
        6 -> Color(0xFF000000L or parsed)
        8 -> Color(parsed)
        else -> fallback
    }
}

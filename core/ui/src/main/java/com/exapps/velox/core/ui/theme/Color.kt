package com.exapps.velox.core.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Velox "Dark Glass" color tokens.
 *
 * These are transcribed 1:1 from DESIGN_SYSTEM.md §2 and BRANDING.md §4 — do not
 * introduce new colors here without updating those docs first. Dark is the only
 * mode defined for v1 ("Light (future)" in the doc), so there is a single palette,
 * not a light/dark pair.
 *
 * A note on provenance: the Stitch-generated UI mockups in velox-UI.zip use a
 * different, Material-You-generated accent (Electric Violet-Blue, #6366F1). That
 * predates/conflicts with this doc and with the master prompt's explicit
 * "No default purple Material theme" rule, so it was NOT used here. DESIGN_SYSTEM.md
 * and BRANDING.md — which independently agree on Velox Teal — are the source of
 * truth for color. See PROGRESS.md for the full note.
 */
object VeloxColors {

    // Backgrounds & surfaces
    val Background = Color(0xFF0B0D10)
    val Surface = Color(0xFF12151A)
    val SurfaceGlass = Color(0x0FFFFFFF) // rgba(255,255,255,0.06) — approximated at 6% alpha
    val SurfaceGlassAlpha = 0.06f
    val SurfaceGlassElevatedAlpha = 0.09f

    // Text
    val OnBackground = Color(0xFFF2F4F7)
    val OnSurface = Color(0xFFE8EAED)
    val OnSurfaceVariant = Color(0xFF9AA0A6)

    // Borders
    val OutlineAlpha = 0.08f
    val OutlineStrongAlpha = 0.14f

    // Accent — Velox Teal (BRANDING.md §4: "Primary Brand Color")
    val Accent = Color(0xFF2EE6A6)
    val AccentMuted = Color(0xFF1A9B74)
    val AccentContainerAlpha = 0.15f

    // Semantic
    val Error = Color(0xFFFF6B6B)
    val Success = Color(0xFF2EE6A6)
    val Warning = Color(0xFFFFB020)

    // AMOLED option (Settings → Appearance → Theme)
    val AmoledBackground = Color(0xFF000000)
    val AmoledSurface = Color(0xFF070707)

    /**
     * Runtime theme-mode switch published by [VeloxTheme]. Backed by Compose
     * snapshot state so every reader — composables, draw scopes, non-compose
     * palette helpers — recomposes/redraws the moment Settings toggles AMOLED,
     * instead of silently keeping the standard-dark grays (which is exactly why
     * the toggle previously appeared to do nothing outside the root background).
     */
    var amoledMode: Boolean by mutableStateOf(false)
        internal set

    /** Mode-aware tokens — prefer these over [Surface]/[Background] in UI code. */
    val surface: Color get() = if (amoledMode) AmoledSurface else Surface
    val background: Color get() = if (amoledMode) AmoledBackground else Background

    /** User-selectable accents (BRANDING.md / DESIGN_SYSTEM.md §2.2). Teal ships as default in v1;
     * the picker itself is a later Settings feature — these are just the swatch values. */
    object AccentOptions {
        val Teal = Accent
        val SoftBlue = Color(0xFF4EA1FF)
        val Violet = Color(0xFF9C7BFF)
        val Amber = Color(0xFFFFB020)
        val Rose = Color(0xFFFF6B9C)
        val Emerald = Color(0xFF2ECC71)
    }
}

/** Convenience helpers for the glass surfaces used throughout the app (DESIGN_SYSTEM.md §5.1, §8). */
fun glassSurfaceColor(elevated: Boolean = false): Color =
    Color.White.copy(alpha = if (elevated) VeloxColors.SurfaceGlassElevatedAlpha else VeloxColors.SurfaceGlassAlpha)

fun glassOutlineColor(strong: Boolean = false): Color =
    Color.White.copy(alpha = if (strong) VeloxColors.OutlineStrongAlpha else VeloxColors.OutlineAlpha)

/** The selectable accent swatches (BRANDING.md / DESIGN_SYSTEM.md §2.2). Order matters:
 * index 0 (Teal) is the v1 default and what UserSettingsPreferences.accentIndex counts
 * from. Kept as a val list so Settings can render the picker without hand-copying. */
val VeloxAccentOptions: List<Color> = listOf(
    VeloxColors.AccentOptions.Teal,
    VeloxColors.AccentOptions.SoftBlue,
    VeloxColors.AccentOptions.Violet,
    VeloxColors.AccentOptions.Amber,
    VeloxColors.AccentOptions.Rose,
    VeloxColors.AccentOptions.Emerald,
)

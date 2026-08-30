package com.exapps.velox.core.domain.theme

import kotlinx.serialization.Serializable

/**
 * Phase 3 / Milestone 2 — Theme engine. A serialisable theme manifest.
 *
 * Themes are pure data: a list of named color tokens, a glass-tint alpha
 * pair, and a radius scale. The runtime [com.exapps.velox.core.ui.theme.VeloxThemeSpec]
 * is the *resolved* shape (after applying the AMOLED override and the
 * accent-picker override); a [ThemeDefinition] is what's stored on disk
 * or shipped in `assets/themes/`.
 *
 * Schema versioning: a [schemaVersion] field on the JSON makes future
 * migrations safe — a stored theme that doesn't match the current
 * [SCHEMA_VERSION] is rejected with a fallback to the bundled default
 * (no crash, just a snackbar).
 */
@Serializable
data class ThemeDefinition(
    val id: String,
    val schemaVersion: Int = SCHEMA_VERSION,
    val name: LocalizedText,
    val tokens: ThemeTokens,
)

/**
 * The user-facing name in their current locale, with a fallback to
 * English when the locale isn't represented. Both fields are required
 * (the fallback is the un-localised `default`).
 */
@Serializable
data class LocalizedText(
    val default: String,
    val ar: String? = null,
    val en: String? = null,
) {
    /** Returns the string for [locale] ("ar" or "en"), falling back to
     * the explicit [default] field, then to the first non-null localised
     * value, then to the empty string. */
    fun forLocale(locale: String): String {
        val want = locale.lowercase()
        val ar = ar
        val en = en
        return when {
            want.startsWith("ar") && ar != null -> ar
            want.startsWith("en") && en != null -> en
            else -> default
        }
    }
}

/**
 * The color tokens a theme can override. Anything not set falls back to
 * the bundled "Dark Glass" defaults in
 * [com.exapps.velox.core.ui.theme.VeloxColors]. The accent is
 * intentionally **not** part of the theme — it's a runtime override
 * (the existing Accent picker in Settings), not a theme property.
 *
 * Color values are stored as `#RRGGBB` strings for JSON friendliness
 * and human-readability (a theme author can write `accent: "#2EE6A6"`
 * without thinking about int packing).
 */
@Serializable
data class ThemeTokens(
    val background: String? = null,
    val onBackground: String? = null,
    val surface: String? = null,
    val onSurface: String? = null,
    val onSurfaceVariant: String? = null,
    val error: String? = null,
    /** 0..1 alpha used for the "glass" surface tint. */
    val glassAlpha: Float? = null,
    /** 0..1 alpha used for the "glass elevated" surface tint. */
    val glassElevatedAlpha: Float? = null,
    /** 0..1 alpha for the standard outline. */
    val outlineAlpha: Float? = null,
    /** 0..1 alpha for the "strong" outline (used on borders that need
     * more contrast, e.g. selected items). */
    val outlineStrongAlpha: Float? = null,
    /** Multiplier on the base radius scale (1.0 = standard, 1.5 = chunky). */
    val radiusScale: Float? = null,
    /** If true, the theme is meant to be used with AMOLED (pure black
     * background) — used as a hint for the theme picker UI, not for
     * any rendering logic. */
    val amoled: Boolean = false,
)

const val SCHEMA_VERSION: Int = 1

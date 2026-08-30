package com.exapps.velox.core.ui.theme

import androidx.compose.ui.graphics.Color
import com.exapps.velox.core.domain.theme.LocalizedText
import com.exapps.velox.core.domain.theme.ThemeDefinition
import com.exapps.velox.core.domain.theme.ThemeTokens
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 3 / Milestone 2 — Theme engine. Pure-Kotlin contract tests
 * for the theme resolver. These don't touch Compose runtime; the
 * resolved values are [Color]s whose .value field we assert on.
 */
class ThemeSpecTest {

    @Test
    fun `default theme spec uses bundled Dark Glass colors`() {
        val theme = ThemeDefinition(
            id = "test-empty",
            name = LocalizedText(default = "Empty"),
            tokens = ThemeTokens(),
        )
        val spec = resolveThemeSpec(theme, accent = VeloxColors.Accent, amoled = false)
        assertEquals(VeloxColors.Background, spec.background)
        assertEquals(VeloxColors.OnBackground, spec.onBackground)
        assertEquals(VeloxColors.Surface, spec.surface)
        assertEquals(VeloxColors.OnSurface, spec.onSurface)
        assertEquals(VeloxColors.OnSurfaceVariant, spec.onSurfaceVariant)
        assertEquals(VeloxColors.Error, spec.error)
        assertEquals(VeloxColors.Accent, spec.accent)
        assertEquals(VeloxColors.SurfaceGlassAlpha, spec.glassAlpha, 0f)
        assertEquals(VeloxColors.SurfaceGlassElevatedAlpha, spec.glassElevatedAlpha, 0f)
        assertEquals(VeloxColors.OutlineAlpha, spec.outlineAlpha, 0f)
        assertEquals(VeloxColors.OutlineStrongAlpha, spec.outlineStrongAlpha, 0f)
        assertEquals(1.0f, spec.radiusScale, 0f)
        assertEquals(false, spec.amoled)
        assertEquals("test-empty", spec.themeId)
    }

    @Test
    fun `amoled toggle overrides background and surface tokens`() {
        val theme = ThemeDefinition(
            id = "test-bright",
            name = LocalizedText(default = "Bright"),
            tokens = ThemeTokens(
                background = "#FFFFFF",
                surface = "#EEEEEE",
            ),
        )
        // AMOLED off: the theme's tokens win.
        val off = resolveThemeSpec(theme, accent = VeloxColors.Accent, amoled = false)
        // 0xFFFFFFFF
        assertEquals(0xFFFFFFFFL, off.background.value.toLong() and 0xFFFFFFFFL)
        // 0xFFEEEEEE
        assertEquals(0xFFEEEEEEL, off.surface.value.toLong() and 0xFFFFFFFFL)
        // AMOLED on: tokens bypassed, the AMOLED palette wins.
        val on = resolveThemeSpec(theme, accent = VeloxColors.Accent, amoled = true)
        assertEquals(VeloxColors.AmoledBackground, on.background)
        assertEquals(VeloxColors.AmoledSurface, on.surface)
    }

    @Test
    fun `accent override is honoured regardless of theme tokens`() {
        val theme = ThemeDefinition(
            id = "t",
            name = LocalizedText(default = "t"),
            tokens = ThemeTokens(),
        )
        val spec = resolveThemeSpec(theme, accent = VeloxColors.AccentOptions.Violet, amoled = false)
        assertEquals(VeloxColors.AccentOptions.Violet, spec.accent)
    }

    @Test
    fun `malformed color strings fall back to bundled defaults`() {
        val theme = ThemeDefinition(
            id = "t",
            name = LocalizedText(default = "t"),
            tokens = ThemeTokens(
                background = "not-a-color",
                surface = "#GGGGGG", // invalid hex
            ),
        )
        val spec = resolveThemeSpec(theme, accent = VeloxColors.Accent, amoled = false)
        assertEquals(VeloxColors.Background, spec.background)
        assertEquals(VeloxColors.Surface, spec.surface)
    }

    @Test
    fun `LocalisedText forLocale picks the right field`() {
        val t = LocalizedText(default = "F", ar = "ع", en = "E")
        assertEquals("ع", t.forLocale("ar"))
        assertEquals("E", t.forLocale("en"))
        assertEquals("F", t.forLocale("fr"))
        assertEquals("E", t.forLocale("en-US"))
    }

    @Test
    fun `LocalisedText forLocale falls back when target locale missing`() {
        val t = LocalizedText(default = "F", ar = "ع") // en is null
        assertEquals("ع", t.forLocale("ar"))
        assertEquals("F", t.forLocale("en"))
    }

    @Test
    fun `alpha tokens honour theme overrides`() {
        val theme = ThemeDefinition(
            id = "t",
            name = LocalizedText(default = "t"),
            tokens = ThemeTokens(
                glassAlpha = 0.20f,
                glassElevatedAlpha = 0.30f,
                outlineAlpha = 0.10f,
                outlineStrongAlpha = 0.20f,
                radiusScale = 1.5f,
            ),
        )
        val spec = resolveThemeSpec(theme, accent = VeloxColors.Accent, amoled = false)
        assertEquals(0.20f, spec.glassAlpha, 0f)
        assertEquals(0.30f, spec.glassElevatedAlpha, 0f)
        assertEquals(0.10f, spec.outlineAlpha, 0f)
        assertEquals(0.20f, spec.outlineStrongAlpha, 0f)
        assertEquals(1.5f, spec.radiusScale, 0f)
    }

    @Test
    fun `parseColorOr handles both 6 and 8 hex digits`() {
        // 6-digit form gets an alpha of 0xFF prepended.
        val s6 = "#2EE6A6".parseColorOr(Color.Black)
        assertEquals(0xFF2EE6A6L, s6.value.toLong() and 0xFFFFFFFFL)
        // 8-digit form preserves the alpha.
        val s8 = "#802EE6A6".parseColorOr(Color.Black)
        assertEquals(0x802EE6A6L, s8.value.toLong() and 0xFFFFFFFFL)
    }
}

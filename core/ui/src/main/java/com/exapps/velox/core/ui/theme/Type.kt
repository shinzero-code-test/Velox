package com.exapps.velox.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.exapps.velox.core.ui.R

/**
 * DESIGN_SYSTEM.md §3.1: IBM Plex Sans Arabic is the primary font for both Arabic
 * (display/headline/body) and — per the Stitch DESIGN.md notes — it renders Latin
 * glyphs cleanly too, so it is used as the single family across the whole app
 * rather than switching families per-locale. Plus Jakarta Sans (the doc's Latin
 * display alternative) is left as a documented option below if a future Settings
 * "Latin font" toggle is added — it is not wired up in v1.
 *
 * Fetched via the Downloadable Fonts API (Google Play services), not bundled —
 * this keeps the APK lean, matching ARCHITECTURE.md's <25MB target. Falls back to
 * the system default font automatically if Play services / network is unavailable.
 */
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private fun googleFontFamily(name: String) = FontFamily(
    Font(googleFont = GoogleFont(name), fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont(name), fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont(name), fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleFont(name), fontProvider = googleFontProvider, weight = FontWeight.Bold),
)

val IbmPlexSansArabic: FontFamily = googleFontFamily("IBM Plex Sans Arabic")

/**
 * The full named scale from DESIGN_SYSTEM.md §3.2. Prefer these over MaterialTheme.typography
 * directly when a screen needs a style that doesn't map cleanly onto Material3's own slots
 * (e.g. `displayLarge` there is the Now Playing title specifically, not a generic "big text").
 */
data class VeloxTypography(
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle,
)

@Composable
fun rememberVeloxTypography(): VeloxTypography = remember {
    val family = IbmPlexSansArabic
    VeloxTypography(
        // Now Playing title
        displayLarge = TextStyle(fontFamily = family, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp),
        // Section headers
        displayMedium = TextStyle(fontFamily = family, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp),
        // Screen titles
        headlineLarge = TextStyle(fontFamily = family, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
        // Card titles
        headlineMedium = TextStyle(fontFamily = family, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
        // List primary
        titleLarge = TextStyle(fontFamily = family, fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
        // Secondary titles
        titleMedium = TextStyle(fontFamily = family, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
        // Body text
        bodyLarge = TextStyle(fontFamily = family, fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
        // Secondary body
        bodyMedium = TextStyle(fontFamily = family, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
        // Buttons, chips
        labelLarge = TextStyle(fontFamily = family, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
        // Captions
        labelMedium = TextStyle(fontFamily = family, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
        // Overlines, timestamps
        labelSmall = TextStyle(fontFamily = family, fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp),
    )
}

/** Material3 interop mapping, so stock M3 components (buttons, text fields, etc.) also
 * pick up the Velox scale via `MaterialTheme.typography` — see Theme.kt. */
fun VeloxTypography.toMaterial3Typography(): Typography = Typography(
    displayLarge = displayLarge,
    displayMedium = displayMedium,
    headlineLarge = headlineLarge,
    headlineMedium = headlineMedium,
    titleLarge = titleLarge,
    titleMedium = titleMedium,
    bodyLarge = bodyLarge,
    bodyMedium = bodyMedium,
    labelLarge = labelLarge,
    labelMedium = labelMedium,
    labelSmall = labelSmall,
)

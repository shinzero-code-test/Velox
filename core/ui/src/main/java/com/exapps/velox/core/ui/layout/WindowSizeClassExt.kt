package com.exapps.velox.core.ui.layout

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

/**
 * Phase 3 / Milestone 3 — Better tablet layouts.
 *
 * Three buckets, matching `material3.adaptive` defaults:
 *  - [Compact] — phones in portrait, < 600 dp wide.
 *  - [Medium] — small tablets in portrait, large phones in landscape,
 *    foldables closed; 600..839 dp.
 *  - [Expanded] — tablets in landscape, foldables open, desktop windows;
 *    ≥ 840 dp.
 *
 * The default Material 3 cutoffs are 600 dp and 840 dp. Velox's
 * pre-existing 720 dp cap (on Now Playing) is a single-screen cap,
 * not a global breakpoint — it stays as a Now-Playing-specific
 * override.
 *
 * Compose UI distributes this through [LocalWindowSizeClass] (set up
 * in [MainActivity]); the [isCompact] / [isMedium] / [isExpanded]
 * helpers are short-hands for the most common case.
 */
val WindowSizeClass.isCompact: Boolean
    get() = widthSizeClass == WindowWidthSizeClass.Compact

val WindowSizeClass.isMedium: Boolean
    get() = widthSizeClass == WindowWidthSizeClass.Medium

val WindowSizeClass.isExpanded: Boolean
    get() = widthSizeClass == WindowWidthSizeClass.Expanded

/**
 * True when the device is wide enough to use a side navigation rail
 * instead of a bottom bar. Mirrors the Material 3 guidance: at
 * medium width and above, the rail pattern reads more naturally than
 * a bottom bar.
 */
val WindowSizeClass.shouldUseNavRail: Boolean
    get() = widthSizeClass != WindowWidthSizeClass.Compact

/**
 * The fallback [WindowSizeClass] for composables that haven't been
 * threaded one (e.g. previews, or screens that don't yet need a
 * window-size-aware layout). Mirrors the smallest meaningful bucket
 * (phone-portrait) so the no-arg call sites behave the same as they
 * did before [WindowSizeClass] was introduced.
 */
val DefaultWindowSizeClass: WindowSizeClass = WindowSizeClass(
    widthSizeClass = WindowWidthSizeClass.Compact,
    heightSizeClass = WindowHeightSizeClass.Compact,
)


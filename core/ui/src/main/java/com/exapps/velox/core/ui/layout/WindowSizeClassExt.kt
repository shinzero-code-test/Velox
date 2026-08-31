package com.exapps.velox.core.ui.layout

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

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
 * threaded one. In practice every real call site (MainActivity / NavHost)
 * provides it; this default only fires from previews. We use the
 * smallest meaningful bucket (Compact) to mirror the phone-portrait
 * behaviour in previews and from any forgotten test harness.
 *
 * The `WindowSizeClass` primary constructor is `private` in the
 * `material3-window-size-class` artifact; the public factory is
 * [WindowSizeClass.Companion.calculateFromSize], gated behind
 * [ExperimentalMaterial3WindowSizeClassApi]. We use a 0×0 dp
 * `DpSize`, which the factory maps to Compact/Compact.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
val DefaultWindowSizeClass: WindowSizeClass = WindowSizeClass.calculateFromSize(
    size = DpSize(0.dp, 0.dp),
)

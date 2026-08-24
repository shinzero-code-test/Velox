package com.exapps.velox.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring

/**
 * Motion tokens — DESIGN_SYSTEM.md §6. "Purposeful, not decorative" (§6.1): every
 * animation in the app should reference one of these rather than inventing a new
 * duration, and should back off to a simple fade when reduced-motion is on
 * (SCREEN_PATTERNS.md §14) — that check happens at the call site via
 * LocalReducedMotion, not here.
 */
object VeloxMotion {

    object Duration {
        /** Icon morphs, press feedback. */
        const val MICRO_MS = 150
        /** Fades, small moves. */
        const val SHORT_MS = 240
        /** Sheets, page transitions. */
        const val MEDIUM_MS = 360
        /** Mini Player ↔ Now Playing shared-element artwork. */
        const val SHARED_ELEMENT_MS = 420
    }

    val standardEasing: Easing = FastOutSlowInEasing
    val enterEasing: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)   // Decelerate
    val exitEasing: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)    // Accelerate

    /** Medium-stiffness spring used for sheet presentation and player expand/collapse. */
    fun <T> mediumSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Press scale for cards and list rows (DESIGN_SYSTEM.md §5.2, SCREENS_OVERVIEW.md §7). */
    const val PRESS_SCALE = 0.97f
}

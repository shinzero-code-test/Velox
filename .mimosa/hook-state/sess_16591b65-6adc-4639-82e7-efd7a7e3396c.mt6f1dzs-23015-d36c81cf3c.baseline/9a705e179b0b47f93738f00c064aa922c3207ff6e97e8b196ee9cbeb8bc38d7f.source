package com.exapps.velox.core.common.util

import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * LOCALIZATION.md §6: "Duration always shown in a clear, consistent style
 * (e.g. `1:23:45` or localized equivalent)". Every screen that shows elapsed/total
 * time (Now Playing, Video Player, list row durations) should go through this
 * rather than formatting ad hoc.
 *
 * Deliberately locale-invariant digits (Locale.US) even in Arabic — mixing Eastern
 * Arabic numerals into a colon-separated timer reads worse than plain digits, and
 * SCREEN_NOW_PLAYING.md §11 / SCREEN_EQUALIZER.md §9 both call out keeping
 * time-like elements in their conventional left-to-right form for familiarity.
 */
fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val duration: Duration = durationMs.milliseconds
    val totalSeconds = duration.inWholeSeconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/** Same as [formatDuration] but with a leading sign for "remaining time" displays
 * (SCREEN_NOW_PLAYING.md §5: "Time labels: start (elapsed) and end (total or
 * remaining — user preference)"). */
fun formatRemaining(durationMs: Long): String = "-${formatDuration(durationMs)}"

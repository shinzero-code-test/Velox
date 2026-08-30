package com.exapps.velox.core.domain.recommendation

import com.exapps.velox.core.domain.model.MediaItem

/**
 * Phase 3 / Wave 3 / Round 3 — Milestone 7. Personalized
 * recommendations, computed on-device from play history. The
 * host never sends a single byte off-device; the README/About
 * makes that explicit.
 */
sealed class Recommendation {

    /** "Because you listened to X" — surfaced when the user
     *  searches for a known track. Co-occurrence + item-similarity
     *  neighbours, ranked. */
    data class BecauseYouListened(
        val seedTrackId: Long,
        val items: List<MediaItem>,
    ) : Recommendation()

    /** "Up next for you" — surfaced on the Now Playing queue sheet.
     *  Co-occurrence neighbours of *everything* the user has
     *  played in the last 30 days, weighted by time-of-day. */
    data class UpNext(
        val items: List<MediaItem>,
    ) : Recommendation()

    /** "Recommended" — surfaced as a horizontal row in the Library
     *  tab. The same engine as [UpNext] but truncated to 12 items
     *  and capped with ~10% random / "discovery" picks so the user
     *  doesn't get stuck in a filter bubble. */
    data class ForYou(
        val items: List<MediaItem>,
    ) : Recommendation()
}

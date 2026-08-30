package com.exapps.velox.core.domain.recommendation

import com.exapps.velox.core.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

/**
 * Phase 3 / Wave 3 / Round 3 — the recommendation service port.
 *
 * Implementations live in `:core:data` and back the three
 * recommendation surfaces (Library "Recommended" row, Now
 * Playing "Up next", Search "Because you listened to X"). The
 * engine is hot, recomputes lazily on first subscription, and
 * re-emits whenever [invalidate] is called (e.g. after
 * "Reset recommendation data" in Settings → Data, or after
 * `play_history` changes beyond a configurable threshold).
 */
interface RecommendationEngine {

    /** The "For You" list (top 12 with ~10% discovery injection). */
    fun forYou(): Flow<Recommendation.ForYou>

    /** The "Up next" list (the next track to play after the current
     *  one — broader than [forYou], weighted by time-of-day). */
    fun upNext(): Flow<Recommendation.UpNext>

    /** The "Because you listened to X" list, scoped to a seed
     *  track. Empty when the seed is unknown or has no neighbours
     *  with sufficient co-occurrence weight. */
    fun becauseYouListened(seedTrackId: Long): Flow<Recommendation.BecauseYouListened>

    /**
     * Drop the cached neighbour matrix. The next call to any
     * of the three flows above re-runs the play-history scan
     * from disk. Settings → Data → "Reset recommendation data"
     * calls this.
     */
    suspend fun invalidate()

    /**
     * Notify the engine that the play history has changed. The
     * engine may choose to invalidate immediately (cheap, ~10ms
     * for a 5k library) or schedule a debounced recompute. Round
     * 1 of this milestone invalidates eagerly; a debounce is a
     * future round.
     */
    suspend fun onPlayHistoryChanged()
}

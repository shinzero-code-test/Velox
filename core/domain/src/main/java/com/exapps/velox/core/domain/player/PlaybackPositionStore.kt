package com.exapps.velox.core.domain.player

/**
 * Persists "where was I in this track" so playback can resume (Settings →
 * Playback → "Remember position"). Kept as a tiny port here — not part of
 * [MediaLibraryRepository] — because the player controller needs it on every
 * poll tick, and the library repository is the wrong owner for player state.
 */
interface PlaybackPositionStore {

    /** Last saved position in ms for [mediaItemId], or null when never played/resumable. */
    suspend fun get(mediaItemId: Long): Long?

    suspend fun put(mediaItemId: Long, positionMs: Long)
}

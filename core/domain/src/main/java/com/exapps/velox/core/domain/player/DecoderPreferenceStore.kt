package com.exapps.velox.core.domain.player

import kotlinx.coroutines.flow.StateFlow

/**
 * Phase 3 / L6 (deferred-backlog): the player engine used to depend on
 * `:core:data` directly to read the decoder preference. This port is the
 * domain-level seam — the engine only sees "is software preferred or not",
 * and the concrete `DecoderPreference` enum stays in `:core:data` where
 * the persistence layer owns it.
 *
 * Synchronous reads ([preferSoftwareCached]) back the H5 fast-path that
 * [com.exapps.velox.player.engine.VeloxExoPlayerFactory] uses to avoid a
 * DataStore read on the playback service's main thread. The cached value
 * is primed once at app start and updated on every write.
 */
interface DecoderPreferenceStore {

    /**
     * Synchronous accessor used by the player factory on the main thread.
     * Returns the safe default (`false` = AUTO) before the app-start
     * priming completes.
     */
    fun preferSoftwareCached(): Boolean

    /**
     * Hot, conflated view of the user's current preference. Observers see
     * the same value the cached accessor would, but without a main-thread
     * disk read. Updated on every write and on the first DataStore emit.
     */
    val preferSoftware: StateFlow<Boolean>

    /**
     * H5: warm the cached value from disk. Called once from
     * [com.exapps.velox.VeloxApplication.onCreate] alongside the locale
     * load — both happen before any activity's `attachBaseContext` runs,
     * so the first service create observes a hot cache.
     */
    suspend fun primeCache()
}

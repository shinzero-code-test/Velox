package com.exapps.velox.core.domain.player

import kotlinx.coroutines.flow.StateFlow

/**
 * Phase 3 / L6 (deferred-backlog): the player engine used to depend on
 * `:core:data` directly to read and write the equalizer settings. This
 * port is the domain-level seam — the engine only sees
 * [EqualizerSettings], and the persistence shape (DataStore keys, CSV
 * encoding) stays in `:core:data` where the storage layer owns it.
 *
 * Implementations must:
 *  - keep [settings] as a hot, conflated [StateFlow] that always emits
 *    a value (a [EqualizerSettings] with all defaults) on subscription,
 *    so the audio effects controller can read the most recent value
 *    synchronously when an audio session attaches;
 *  - serialize concurrent [save] calls so two simultaneous persist
 *    operations (e.g. a per-track flush overlapping a user-driven save)
 *    can't interleave on the underlying DataStore.
 */
interface EqualizerPreferencesStore {

    /**
     * Hot observation of the persisted EQ state. The first emission is
     * the values from the previous process (if any), or the defaults
     * (EQ off, all bands flat) on a fresh install.
     */
    val settings: StateFlow<EqualizerSettings>

    /**
     * One-shot read of the most recent persisted value. Used by the
     * audio effects controller's restore-on-attach path so it can pull
     * a consistent snapshot without subscribing to the flow.
     */
    suspend fun current(): EqualizerSettings

    /**
     * Persist [settings] to the underlying store atomically. Returns
     * when the write is durable (the storage layer's own lock has
     * released), not when the in-memory cache is updated.
     */
    suspend fun save(settings: EqualizerSettings)
}

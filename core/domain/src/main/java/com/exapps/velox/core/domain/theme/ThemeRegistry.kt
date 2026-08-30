package com.exapps.velox.core.domain.theme

import kotlinx.coroutines.flow.StateFlow

/**
 * Phase 3 / Milestone 2 — Theme engine. The active-theme port. Anything
 * that needs to know "which theme is selected" injects this and reads
 * [active]; anything that wants to render the theme picker reads
 * [available] and listens to changes.
 *
 * Implementations live in `:core:data` and `:core:ui`:
 *  - `:core:data` owns the persisted selection (DataStore) and the
 *    bundled-asset enumeration.
 *  - `:core:ui` consumes the resolved [ThemeDefinition] and combines it
 *    with the runtime accent override and AMOLED toggle into the live
 *    [com.exapps.velox.core.ui.theme.VeloxThemeSpec] that Compose
 *    composables read.
 */
interface ThemeRegistry {

    /**
     * Hot, conflated view of the currently active theme. The first
     * emission is the persisted selection (or the bundled default if
     * the user has never picked a theme).
     */
    val active: StateFlow<ThemeDefinition>

    /**
     * Snapshot of every theme the user can pick: bundled (Dark Glass,
     * AMOLED Dark) plus any imported via SAF. Sorted by
     * [ThemeDefinition.name]'s default-string for stable ordering.
     */
    suspend fun available(): List<ThemeDefinition>

    /**
     * Persist [themeId] as the new active theme. The id must appear in
     * [available]; unknown ids are ignored (no crash, no-op).
     */
    suspend fun setActive(themeId: String)

    /**
     * H5-equivalent: warm the cached [active] value from disk. Called
     * from `VeloxApplication.onCreate()` alongside the locale and
     * decoder-preference priming.
     */
    suspend fun primeCache()
}

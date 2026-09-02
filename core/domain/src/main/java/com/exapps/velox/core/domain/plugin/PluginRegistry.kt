package com.exapps.velox.core.domain.plugin

/**
 * Phase 3 / Milestone 4 — Plugin architecture. The lookup surface
 * for [MediaSourceProvider]s.
 *
 * Two pieces of state:
 *  - [providerForScheme] routes a URL scheme (`smb`, `davs`, etc.)
 *    to the provider that handles it. This is the hot path the
 *    `RoutingDataSource` calls on every `open()`.
 *  - [available] lists every registered provider for the
 *    Settings → Plugins UI.
 *
 * Implementations live in `:core:data` and bind the registry via
 * Hilt. They collect built-in providers (SMB / FTP / WebDAV / HTTP)
 * and first-party plugins; APK-form discovery ([PluginDiscovery])
 * is a Round 1.5 / 3.5d addition that v1.8.0 ships the interface
 * for but the empty implementation.
 */
interface PluginRegistry {

    /**
     * The first provider that claims [scheme], or null when no
     * provider registered for that scheme. Schemes are matched
     * case-insensitively and the lookup is `O(providers)`, which
     * is fine for the current handful of built-in providers.
     * Disabled plugins (see [isEnabled]) are skipped — they never
     * handle a scheme.
     */
    fun providerForScheme(scheme: String): MediaSourceProvider?

    /**
     * Every registered provider, sorted by [MediaSourceProvider.id]
     * for stable Settings UI ordering. Includes first-party
     * Hilt-bound providers + any third-party APK providers
     * returned by [PluginDiscovery.discover]. Disabled state is
     * not filtered here — the UI shows every provider with its
     * enabled toggle.
     */
    suspend fun available(): List<MediaSourceProvider>

    /** Whether [id] is currently enabled (not in the disabled set). */
    suspend fun isEnabled(id: String): Boolean

    /** Persist enabled state for [id]. */
    suspend fun setEnabled(id: String, enabled: Boolean)

    /** Flow of disabled ids, for UI to collect. */
    fun observeDisabledIds(): kotlinx.coroutines.flow.Flow<Set<String>>
}

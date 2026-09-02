package com.exapps.velox.core.data.plugin

import com.exapps.velox.core.common.di.ApplicationScope
import com.exapps.velox.core.data.preferences.PluginPreferences
import com.exapps.velox.core.domain.plugin.MediaSourceProvider
import com.exapps.velox.core.domain.plugin.PluginDiscovery
import com.exapps.velox.core.domain.plugin.PluginRegistry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Phase 3 / Milestone 4 — Plugin registry default implementation.
 *
 * Phase 3 / Wave 3 / Round 3.5d — also folds in third-party
 * plugins returned by [PluginDiscovery.discover] (APK-form
 * plugins). The Hilt-bound `Set<MediaSourceProvider>` is the
 * first-party surface; the discovery list is appended (with
 * dedup by [MediaSourceProvider.id]).
 *
 * v1.9.x — Per-plugin enable/disable: disabled ids are persisted
 * in [PluginPreferences] (DataStore StringSet) and cached in
 * memory so the hot path [providerForScheme] (called on ExoPlayer's
 * loader thread, non-suspend) can filter without blocking.
 */
@Singleton
class PluginRegistryAdapter @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards MediaSourceProvider>,
    private val discovery: PluginDiscovery,
    private val pluginPreferences: PluginPreferences,
    @ApplicationScope private val appScope: CoroutineScope,
) : PluginRegistry {

    @Volatile
    private var disabledCache: Set<String> = emptySet()

    init {
        // Warm cache asynchronously; first call before prime is emptySet (all enabled).
        appScope.launch {
            pluginPreferences.disabledIds.collect { disabledCache = it }
        }
    }

    private val byScheme: Map<String, MediaSourceProvider> by lazy {
        providers
            .flatMap { provider -> provider.supportedProtocols.map { it.lowercase() to provider } }
            .toMap()
    }

    override fun providerForScheme(scheme: String): MediaSourceProvider? {
        val provider = byScheme[scheme.lowercase()] ?: return null
        // Hot path must not suspend — check in-memory cache.
        return if (provider.id in disabledCache) null else provider
    }

    override suspend fun available(): List<MediaSourceProvider> {
        // Merge first-party (Hilt) + third-party (APK discovery).
        // Dedup by id; the Hilt-bound list wins on collision.
        val byId = LinkedHashMap<String, MediaSourceProvider>()
        for (p in providers) byId.putIfAbsent(p.id, p)
        for (p in discovery.discover()) byId.putIfAbsent(p.id, p)
        return byId.values.sortedBy { it.id }
    }

    override suspend fun isEnabled(id: String): Boolean = pluginPreferences.isEnabled(id)

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        pluginPreferences.setEnabled(id, enabled)
        // Optimistically update cache so subsequent providerForScheme sees new value
        // without waiting for DataStore flow emission.
        disabledCache = if (enabled) disabledCache - id else disabledCache + id
    }

    override fun observeDisabledIds(): Flow<Set<String>> = pluginPreferences.disabledIds
}

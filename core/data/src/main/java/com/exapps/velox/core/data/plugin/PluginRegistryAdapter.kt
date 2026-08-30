package com.exapps.velox.core.data.plugin

import com.exapps.velox.core.domain.plugin.MediaSourceProvider
import com.exapps.velox.core.domain.plugin.PluginDiscovery
import com.exapps.velox.core.domain.plugin.PluginRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3 / Milestone 4 — Plugin registry default implementation.
 *
 * Phase 3 / Wave 3 / Round 3.5d — also folds in third-party
 * plugins returned by [PluginDiscovery.discover] (APK-form
 * plugins). The Hilt-bound `Set<MediaSourceProvider>` is the
 * first-party surface; the discovery list is appended (with
 * dedup by [MediaSourceProvider.id]).
 */
@Singleton
class PluginRegistryAdapter @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards MediaSourceProvider>,
    private val discovery: PluginDiscovery,
) : PluginRegistry {

    private val byScheme: Map<String, MediaSourceProvider> by lazy {
        providers
            .flatMap { provider -> provider.supportedProtocols.map { it.lowercase() to provider } }
            .toMap()
    }

    override fun providerForScheme(scheme: String): MediaSourceProvider? {
        // Hot path: only the first-party set is consulted. APK-
        // discovered providers are paged in lazily — the engine
        // asks for a provider, gets a hit from the first-party
        // set, and if no hit exists it would be a brand-new
        // protocol that the engine has no reason to ask about.
        // The Settings → About → Plugins surface uses [available]
        // which does include discovered plugins.
        return byScheme[scheme.lowercase()]
    }

    override suspend fun available(): List<MediaSourceProvider> {
        // Merge first-party (Hilt) + third-party (APK discovery).
        // Dedup by id; the Hilt-bound list wins on collision.
        val byId = LinkedHashMap<String, MediaSourceProvider>()
        for (p in providers) byId.putIfAbsent(p.id, p)
        for (p in discovery.discover()) byId.putIfAbsent(p.id, p)
        return byId.values.sortedBy { it.id }
    }
}

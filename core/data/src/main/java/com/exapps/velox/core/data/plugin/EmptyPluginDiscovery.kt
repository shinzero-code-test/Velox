package com.exapps.velox.core.data.plugin

import com.exapps.velox.core.domain.plugin.MediaSourceProvider
import com.exapps.velox.core.domain.plugin.PluginDiscovery
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3 / Wave 3 / Round 3.5d — empty [PluginDiscovery] for
 * v1.8.0. APK-form plugin discovery is deferred until a real
 * third-party consumer is on the horizon; the host returns an
 * empty list, the Settings → About → Plugins screen shows
 * "no third-party plugins installed", and the first-party
 * providers (HTTP, SMB, FTP, WebDAV) continue to ship in the
 * host APK via Hilt multibinding.
 *
 * When a third-party plugin is needed, the round-1.5 work
 * replaces this with a real [PackageManager] walk gated on a
 * signature permission, plus a [DexClassLoader] for the plugin's
 * APK. The interface is the same; the implementation swaps.
 */
@Singleton
class EmptyPluginDiscovery @Inject constructor() : PluginDiscovery {
    override suspend fun discover(): List<MediaSourceProvider> = emptyList()
}

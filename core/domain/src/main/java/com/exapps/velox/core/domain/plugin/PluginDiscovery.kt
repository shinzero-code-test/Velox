package com.exapps.velox.core.domain.plugin

/**
 * Phase 3 / Wave 3 / Round 3.5d — APK-form plugin discovery
 * foundation. The [PluginDiscovery] interface lets a host
 * implementation enumerate installed APKs that advertise the
 * `com.exapps.velox.MEDIA_SOURCE_PROVIDER` intent filter and
 * return each as a [MediaSourceProvider] (loaded via reflection
 * or DexClassLoader, depending on the host's security model).
 *
 * For v1.8.0 the host returns an empty list — no third-party
 * plugin is on the horizon yet, and adding APK loading with
 * signature-permission + DexClassLoader is its own can of
 * security worms. The interface exists so the Settings → About
 * → Plugins screen can show "no plugins installed" without
 * a separate code path, and so the registry's `@IntoSet` wiring
 * accepts a discovery-based provider as easily as a Hilt-bound
 * one.
 */
interface PluginDiscovery {
    /** Enumerate every installed plugin. Order is undefined. */
    suspend fun discover(): List<MediaSourceProvider>
}

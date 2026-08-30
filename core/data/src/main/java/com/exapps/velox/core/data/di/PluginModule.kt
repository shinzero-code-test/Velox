package com.exapps.velox.core.data.di

import com.exapps.velox.core.domain.plugin.MediaSourceProvider
import com.exapps.velox.core.domain.plugin.PluginRegistry
import com.exapps.velox.core.data.plugin.PluginRegistryAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Phase 3 / Milestone 4 — Plugin architecture. Hilt wiring for the
 * plugin registry.
 *
 * The registry takes a `Set<MediaSourceProvider>` via Hilt
 * multibinding — every `@Provides fun ...: MediaSourceProvider`
 * elsewhere in the graph lands in the set, and the adapter
 * (above) builds a `scheme → provider` map lazily from the set.
 *
 * Adding a new first-party plugin in this MVP is a one-liner:
 *   `@Provides @IntoSet fun provideMyPlugin(): MediaSourceProvider = MyPlugin()`
 * in some module the host builds. APK-form plugins (Phase 3b in
 * the plan) would replace this `IntoSet` discovery with a
 * PackageManager walk; the bound `PluginRegistry` contract
 * doesn't change.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PluginModule {

    @Binds
    @Singleton
    abstract fun bindPluginRegistry(impl: PluginRegistryAdapter): PluginRegistry
}

/**
 * Empty seed module. The first-party plugin in `:feature:network`
 * contributes its own `@Provides` to the same `Set<MediaSourceProvider>`.
 * Hilt aggregates the contributions at build time.
 */
@Module
@InstallIn(SingletonComponent::class)
object PluginSeedModule {
    // Intentional no-op. Real plugins contribute `@Provides` functions
    // (e.g. `feature.network.NetworkFirstPartyPluginModule.provideWebDavOverHttps()`)
    // that are aggregated into the same `Set<MediaSourceProvider>`.
    @Suppress("unused")
    private val SEED: Byte = 0
}
